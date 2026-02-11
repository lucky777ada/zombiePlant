package org.besomontro.planner

import ai.koog.agents.planner.goap.goap
import org.besomontro.client.HydroponicApiClient
import org.besomontro.client.FeedRequest
import org.besomontro.client.DoseRequest
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.boolean
import org.besomontro.db.DatabaseLogger
// FIX 1: Add missing reflection import
import kotlin.reflect.typeOf

fun hydroponicActions(api: HydroponicApiClient) = goap<HydroponicState>(
    stateType = typeOf<HydroponicState>()
) {
    // FIX 2: Define goal using the standard DSL parameters (name and condition)
    goal(
        name = "Maintain Healthy State",
        condition = { state ->
             val nowByHour = java.time.LocalTime.now().hour
             val shouldLightsBeOn = nowByHour in 6..21 // 06:00 to 21:59 lights ON
            
            state.isPhKnown &&
                    state.isWaterLevelKnown &&
                    state.isLightsKnown &&
                    (state.lightsOn == shouldLightsBeOn) &&
                    state.waterLevel == "optimal" &&
                    (
                        run {
                            // "Drift Tolerance": Only care if < 4.8 or > 7.2 (User defined buffer)
                            // We ignore the specific stage target for the *Goal* satisfied check
                            // to prevent micro-managing, unless it's way out of whack.
                            val ph = state.phLevel ?: 0.0
                            ph in 4.8..7.2
                        } ||
                        // Allow if user notified recently (< 4 hours)
                        ((System.currentTimeMillis() - (state.lastPhNotificationTimestamp ?: 0L)) < (4 * 60 * 60 * 1000))
                    ) &&
                    (
                        run {
                           val targetTds = getTdsRange(state.currentStage)
                           // Allow tolerance -50..+100
                           val safeTds = (targetTds.start - 50.0)..(targetTds.endInclusive + 100.0)
                           (state.tds ?: 0.0) in safeTds
                        }
                    )
        }
    )

    // --- Sensing Actions ---
    action(
        name = "Measure pH",
        precondition = { !it.isPhKnown },
        belief = { it.copy(isPhKnown = true, phLevel = 6.0) }, // Optimistic belief
        cost = { 1.0 }
    ) { _, state ->
        DatabaseLogger.log("Measure pH", "Triggered pH measurement (Multi-sample)")
        try {
            // Anti-Oscillation: Take 3 readings
            val samples = mutableListOf<Double>()
            repeat(3) {
                val response = api.getPh()
                val phVal = response["ph"]?.jsonPrimitive?.double ?: -1.0
                if (phVal > 0) samples.add(phVal)
                kotlinx.coroutines.delay(10000) // 10s delay between readings
            }
            
            if (samples.isEmpty()) throw Exception("No valid pH readings")
            
            val average = samples.average()
            val variance = samples.max() - samples.min()
            
            if (variance > 0.4) {
                 DatabaseLogger.log("Measure pH", "Unstable Readings! Variance=${"%.2f".format(variance)} > 0.4. Samples=$samples")
                 // Mark as unknown so we try again later? Or fail safely?
                 // Returning current state with isPhKnown=false forces a re-plan or retry next cycle
                 state.copy(isPhKnown = false)
            } else {
                DatabaseLogger.log("Measure pH", "Success: pH=${"%.2f".format(average)} (Var=${"%.2f".format(variance)})")
                state.copy(phLevel = average, isPhKnown = true)
            }
        } catch (e: Exception) {
            println("Error measuring pH: ${e.message}")
            DatabaseLogger.log("Measure pH", "Failed: ${e.message}")
            state.copy(isPhKnown = false, isSystemHealthy = false)
        }
    }

    action(
        name = "Check Hardware Status",
        precondition = { !it.isWaterLevelKnown || !it.isTdsKnown || !it.isLightsKnown },
        belief = { it.copy(isWaterLevelKnown = true, waterLevel = "optimal", isTdsKnown = true, tds = 500.0, isLightsKnown = true) }, // Optimistic belief
        cost = { 1.0 }
    ) { _, state ->
        DatabaseLogger.log("Check Hardware Status", "Querying telemetry")
         try {
            val response = api.getHardwareStatus()
            // Parsing water_level object: {"full": false, "empty": false}
            val waterLevelObj = response["water_level"]?.jsonObject
            val isEmpty = waterLevelObj?.get("empty")?.jsonPrimitive?.boolean ?: false
            val isFull = waterLevelObj?.get("full")?.jsonPrimitive?.boolean ?: false
            
            val tdsElement = response["tds"]
            val tdsVal = if (tdsElement is kotlinx.serialization.json.JsonPrimitive) {
                tdsElement.double
            } else {
                // Handle case where TDS is wrapped in an object
                // Try "ppm" first (from user's JSON), then fallback to "value"
                val tdsObj = tdsElement?.jsonObject
                tdsObj?.get("ppm")?.jsonPrimitive?.double 
                ?: tdsObj?.get("value")?.jsonPrimitive?.double 
                ?: -1.0
            }

            // Parse environment for temperature
            val environmentObj = response["environment"]?.jsonObject
            val temperatureF = environmentObj?.get("temperature_f")?.jsonPrimitive?.double

            // Parsing lights status
            // API returns "on" or "off" string, not a boolean primitive
            val acPowerStr = response["ac_power"]?.jsonPrimitive?.content ?: "off"
            val lightsOn = acPowerStr.equals("on", ignoreCase = true)

            // Map to 3 states:
            // "low" -> empty is true
            // "overflow" -> full is true (user requested "overflow" for full state)
            // "optimal" -> neither empty nor full
            val waterLvl = when {
                isEmpty -> "low"
                isFull -> "overflow"
                else -> "optimal"
            }
            
            DatabaseLogger.log("Check Hardware Status", "Result: WaterLevel=$waterLvl TDS=$tdsVal Lights=$lightsOn Temp=$temperatureF")
            state.copy(
                waterLevel = waterLvl, 
                isWaterLevelKnown = true,
                tds = tdsVal,
                isTdsKnown = true,
                lightsOn = lightsOn,
                isLightsKnown = true,
                temperature = temperatureF,
                // Track High TDS duration for sediment buffering
                highTdsSince = run {
                    val targetRange = getTdsRange(state.currentStage)
                    val isHigh = tdsVal > (targetRange.endInclusive + 100.0)
                    if (isHigh) (state.highTdsSince ?: System.currentTimeMillis()) else null
                }
            )
        } catch (e: Exception) {
            println("Error checking hardware: ${e.message}")
            DatabaseLogger.log("Check Hardware Status", "Failed: ${e.message}")
            state.copy(isWaterLevelKnown = false, isSystemHealthy = false)
        }
    }

    // --- Actuation Actions ---

    action(
        name = "Fill Tank",
        precondition = { it.isWaterLevelKnown && it.waterLevel == "low" && !it.tankFilled },
        belief = { it.copy(waterLevel = "optimal", tankFilled = true) },
        cost = { 5.0 } // Expensive/Takes time
    ) { _, state ->
        DatabaseLogger.log("Fill Tank", "Starting fill sequence")
        api.fillToMax()
        DatabaseLogger.log("Fill Tank", "Completed")
        state.copy(waterLevel = "optimal", tankFilled = true)
    }

    // Troubleshooting: Pulse Fill Recovery
    action(
        name = "Pulse Fill Recovery",
        precondition = { 
            // We thought we filled it, but it's still low!
            it.isWaterLevelKnown && it.waterLevel == "low" && it.tankFilled 
        },
        belief = { it.copy(waterLevel = "optimal", tankFilled = true) }, // Hope this fixes it
        cost = { 50.0 } // Penalize heavily so we only do this if normal fill failed
    ) { _, state -> 
        DatabaseLogger.log("Pulse Fill", "Potential Air-Lock Detected. pulsing pump...")
        repeat(5) {
             api.controlPump(org.besomontro.client.PumpCommand(pump_id = "water_in", duration = 2.0))
             kotlinx.coroutines.delay(2000)
        }
        // Assume fixed for now, next sensor read will confirm
        state.copy(waterLevel = "optimal")
    }

    action(
        name = "System Flush",
        // Trigger only if TDS is CRITICALLY high OR pH is crazy high
        precondition = { 
            val now = System.currentTimeMillis()
            val lastFlush = it.lastFlushTimestamp ?: 0L
            val cooldownPassed = (now - lastFlush) > (24 * 60 * 60 * 1000)
            
            val targetRange = getTdsRange(it.currentStage)
            val tdsHigh = (it.tds ?: 0.0) > (targetRange.endInclusive + 200.0)
            val phCrazy = (it.phLevel ?: 0.0) > 7.5
            val tdsAlsoHigh = (it.tds ?: 0.0) > targetRange.endInclusive
            
            // Sediment Check: Has TDS been high for > 24 hours?
            val highDuration = if (it.highTdsSince != null) now - it.highTdsSince else 0L
            val sedimentSettled = highDuration > (24 * 60 * 60 * 1000)

            // Trigger if:
            // 1. TDS is way over limit AND has been high for 24h (Sediment protection)
            // 2. pH is > 7.5 AND TDS is at least somewhat high (Immediate flush for chemical lockout)
            it.isTdsKnown && cooldownPassed && ( (tdsHigh && sedimentSettled) || (phCrazy && tdsAlsoHigh) )
        },
        belief = { 
            it.copy(
                tds = 50.0, // Assume fresh water TDS
                isTdsKnown = true,
                flushCompleted = true,
                lastFlushTimestamp = System.currentTimeMillis()
            ) 
        },
        cost = { 100.0 } // VERY EXPENSIVE to discourage usage unless critical
    ) { _, state ->
        DatabaseLogger.log("System Flush", "CRITICAL Flush Triggered. TDS=${state.tds}, pH=${state.phLevel}")
        api.trueFlush(soakDuration = 180)
        DatabaseLogger.log("System Flush", "Flush Completed")
        state.copy(
            tds = 50.0,
            isTdsKnown = true,
            flushCompleted = true,
            lastFlushTimestamp = System.currentTimeMillis()
        )
    }

    action(
        name = "Fix Overflow",
        precondition = { it.isWaterLevelKnown && it.waterLevel == "overflow" },
        belief = { it.copy(waterLevel = "optimal") },
        cost = { 1.0 }
    ) { _, state ->
        DatabaseLogger.log("Fix Overflow", "Triggering overflow fix")
        api.fixOverflow()
        state.copy(waterLevel = "optimal")
    }

    action(
        name = "Run Diagnostic",
        precondition = { 
            val now = System.currentTimeMillis()
            val lastDiag = it.lastDiagnosticTimestamp ?: 0L
            val expired = (now - lastDiag) > (24 * 60 * 60 * 1000)
            !it.isSystemHealthy || expired 
        },
        belief = { it.copy(isSystemHealthy = true, lastDiagnosticTimestamp = System.currentTimeMillis()) },
        cost = { 5.0 }
    ) { _, state ->
        DatabaseLogger.log("Diagnostics", "Running self-check...")
        try {
            val report = api.diagnose()
            val status = report["status"]?.jsonPrimitive?.content ?: "unknown"
            val isHealthy = status == "healthy"
            
            DatabaseLogger.log("Diagnostics", "Result: $status")
            state.copy(isSystemHealthy = isHealthy, lastDiagnosticTimestamp = System.currentTimeMillis())
        } catch (e: Exception) {
            DatabaseLogger.log("Diagnostics", "Failed: ${e.message}")
            state.copy(isSystemHealthy = false)
        }
    }

    action(
        name = "Turn Lights On",
        precondition = { it.isLightsKnown && !it.lightsOn },
        belief = { it.copy(lightsOn = true) },
        cost = { 2.0 }
    ) { _, state ->
        DatabaseLogger.log("Lights", "Turning Lights ON")
        api.controlAcRelay("on")
        state.copy(lightsOn = true)
    }

    action(
        name = "Turn Lights Off",
        precondition = { it.isLightsKnown && it.lightsOn },
        belief = { it.copy(lightsOn = false) },
        cost = { 2.0 }
    ) { _, state ->
        DatabaseLogger.log("Lights", "Turning Lights OFF")
        api.controlAcRelay("off")
        state.copy(lightsOn = false)
    }

    action(
        name = "Empty Tank",
        precondition = { !it.tankEmptied },
        belief = { it.copy(tankEmptied = true, waterLevel = "low") },
        cost = { 5.0 }
    ) { _, state ->
        DatabaseLogger.log("Empty Tank", "Starting empty sequence")
        api.emptyTank()
        DatabaseLogger.log("Empty Tank", "Completed")
        state.copy(tankEmptied = true, waterLevel = "low")
    }
    
    action(
        name = "Dose Nutrients",
        precondition = { 
            val targetRange = getTdsRange(it.currentStage)
            val phSafe = (it.phLevel ?: 7.0) < 7.5 // Don't dose if pH is crazy
            it.isTdsKnown && (it.tds ?: 0.0) < targetRange.start && phSafe 
        },
        belief = { 
            val targetRange = getTdsRange(it.currentStage)
            // Optimistically assume we hit the sweet spot (max of range)
            it.copy(tds = targetRange.endInclusive, lastFeedTimestamp = System.currentTimeMillis()) 
        },
        cost = { 10.0 }
    ) { _, state ->
        DatabaseLogger.log("Dose Nutrients", "Smart Feed: ${state.currentStage} Mix")
        // Mapping stage to recipe string
        val recipe = when(state.currentStage) {
            GrowthStage.SEEDLING -> "seedling"
            GrowthStage.VEGETATIVE -> "vegetative"
            GrowthStage.FLOWERING -> "flowering"
        }
        api.smartFeed(FeedRequest(recipe = recipe))
        
        val targetRange = getTdsRange(state.currentStage)
        state.copy(tds = targetRange.endInclusive, lastFeedTimestamp = System.currentTimeMillis())
    }

    action(
        name = "Micro Dose Nutrients",
        precondition = {
            val targetRange = getTdsRange(it.currentStage)
            // Trigger if TDS is below target but not critically low (use full feed for that)
            // and water level is optimal (safe to add)
            it.isTdsKnown && (it.tds ?: 0.0) < targetRange.start &&
                    (it.tds ?: 0.0) > (targetRange.start - 200.0) && // Only micro dose if close-ish
                    it.waterLevel == "optimal" &&
                    it.isSystemHealthy &&
                    it.doseCount < 3 // Limit to 3 times per routine
        },
        belief = {
            val targetRange = getTdsRange(it.currentStage)
            it.copy(
                tds = targetRange.start + 50.0, 
                lastFeedTimestamp = System.currentTimeMillis(),
                doseCount = it.doseCount + 1
            )
        },
        cost = { 3.0 } // Cheaper than full drain/feed
    ) { _, state ->
        DatabaseLogger.log("Micro Dose", "Injecting Micro-Dose to raise TDS")
        
        // Simple fixed small dose for now, can be made smarter
        val doseAmount = 5.0 // ml

        // Sequence: Micro -> Gro -> Bloom (Standard order)
        api.dose(DoseRequest(nutrient = "flora_micro", amount_ml = doseAmount))
        kotlinx.coroutines.delay(1000)
        api.dose(DoseRequest(nutrient = "flora_gro", amount_ml = doseAmount))
        kotlinx.coroutines.delay(1000)
        api.dose(DoseRequest(nutrient = "flora_bloom", amount_ml = doseAmount))
        
        // Update belief with new reading
        val res = api.dose(DoseRequest(nutrient = "flora_bloom", amount_ml = 0.0, mix_seconds = 60)) // Mixing run
        val finalTds = res["final_tds"]?.jsonPrimitive?.double ?: (state.tds ?: 0.0)
        
        state.copy(tds = finalTds, lastFeedTimestamp = System.currentTimeMillis())
    }

    // Correct pH (Manual Request)
    action(
        name = "Request User pH Fix",
        precondition = { 
            val now = System.currentTimeMillis()
            val lastNotif = it.lastPhNotificationTimestamp ?: 0L
            val cooldownPassed = (now - lastNotif) > (4 * 60 * 60 * 1000)

            // Relaxed Tolerance: 4.8 to 7.2
            val ph = it.phLevel ?: 0.0
            val isCritical = ph < 4.8 || ph > 7.2
            
            it.isPhKnown && isCritical && cooldownPassed
        },
        belief = { 
            it.copy(
                lastPhNotificationTimestamp = System.currentTimeMillis()
            ) 
        },
        cost = { 10.0 }
    ) { _, state ->
        DatabaseLogger.log("Request User pH Fix", "pH CRITICAL (${state.phLevel}). Notifying User.")
        println(">>> AGENT REQUEST: Please fix pH level! Current: ${state.phLevel}")
        
        state.copy(lastPhNotificationTimestamp = System.currentTimeMillis()) 
    }
}

private fun getTdsRange(stage: GrowthStage): ClosedRange<Double> = when(stage) {
    GrowthStage.SEEDLING -> 300.0..500.0
    GrowthStage.VEGETATIVE -> 800.0..1200.0
    GrowthStage.FLOWERING -> 1000.0..1400.0
}

private fun getPhRange(stage: GrowthStage): ClosedRange<Double> = when(stage) {
    GrowthStage.SEEDLING -> 5.8..6.3
    GrowthStage.VEGETATIVE -> 5.5..6.0
    GrowthStage.FLOWERING -> 6.0..6.5
}