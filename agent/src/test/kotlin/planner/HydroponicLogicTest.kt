package planner

import org.besomontro.client.HydroponicApiClient
import org.besomontro.planner.HydroponicState
import org.besomontro.planner.GrowthStage
import org.besomontro.planner.hydroponicActions
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlinx.coroutines.runBlocking

class HydroponicLogicTest {

    @Test
    fun `Stage Calculation Logic`() {
        assertEquals(GrowthStage.SEEDLING, HydroponicState.calculateStage(0))
        assertEquals(GrowthStage.SEEDLING, HydroponicState.calculateStage(14))
        assertEquals(GrowthStage.VEGETATIVE, HydroponicState.calculateStage(15))
        assertEquals(GrowthStage.VEGETATIVE, HydroponicState.calculateStage(35))
        assertEquals(GrowthStage.FLOWERING, HydroponicState.calculateStage(36))
        assertEquals(GrowthStage.FLOWERING, HydroponicState.calculateStage(100))
    }

    @Test
    fun `Seedling stage should flush if TDS above limit`() = runBlocking {
        // Seedling Target Max: 500. Flush Trigger: Target Max + 200 = 700.
        val api = HydroponicApiClient("http://localhost:0", 0)
        val planner = hydroponicActions(api)

        val state = HydroponicState(
            currentStage = GrowthStage.SEEDLING,
            tds = 750.0, // High for Seedling (> 500 + 200)
            isTdsKnown = true,
            lastFlushTimestamp = 0L, 
            
            // Healthy otherwise
            phLevel = 6.0,
            isPhKnown = true,
            waterLevel = "optimal",
            isWaterLevelKnown = true,
            lightsOn = false, 
            isLightsKnown = true,
            isSystemHealthy = true
        )

        val plan = planner.plan(state)
        assertNotNull(plan, "Plan should exist for critical high TDS")
        assertTrue(plan!!.any { it.name == "System Flush" }, "Should plan System Flush for high TDS in Seedling stage")
    }

    @Test
    fun `Vegetative stage should NOT dose if within range`() = runBlocking {
        // Veg Target: 800 - 1200.
        val api = HydroponicApiClient("http://localhost:0", 0)
        val planner = hydroponicActions(api)

        val state = HydroponicState(
            currentStage = GrowthStage.VEGETATIVE,
            tds = 1000.0, // perfect
            isTdsKnown = true,
            
            // Healthy otherwise
            phLevel = 5.8, // Veg target 5.5-6.0
            isPhKnown = true,
            waterLevel = "optimal",
            isWaterLevelKnown = true,
            lightsOn = false, 
            isLightsKnown = true,
            isSystemHealthy = true
        )

        val plan = planner.plan(state)
        
        if (plan != null && plan.isNotEmpty()) {
             assertFalse(plan.any { it.name == "Dose Nutrients" }, "Should NOT dose nutrients when optimal")
        }
    }

    @Test
    fun `Flowering stage should dose if TDS is low`() = runBlocking {
        // Flowering Target: 1000 - 1400.
        // Dose Trigger: < Min (1000)
        val api = HydroponicApiClient("http://localhost:0", 0)
        val planner = hydroponicActions(api)

        val state = HydroponicState(
            currentStage = GrowthStage.FLOWERING,
            tds = 800.0, // Too low for Flowering
            isTdsKnown = true,
            
            // Healthy otherwise
            phLevel = 6.2, // Flower target 6.0-6.5
            isPhKnown = true,
            waterLevel = "optimal",
            isWaterLevelKnown = true,
            lightsOn = false, 
            isLightsKnown = true,
            isSystemHealthy = true
        )

        val plan = planner.plan(state)
        assertNotNull(plan, "Plan should exist for low TDS")
        assertTrue(plan!!.any { it.name == "Dose Nutrients" }, "Should dose nutrients for low TDS in Flowering stage")
    }

    @Test
    fun `Drift Tolerance - pH 6_8 should NOT trigger fix`() = runBlocking {
        val api = HydroponicApiClient("http://localhost:0", 0)
        val planner = hydroponicActions(api)

        // Seedling Target: 5.8-6.3.
        // 6.8 is outside Target, but inside Drift Tolerance (4.8 - 7.2).
        // It's also within the GOAP Goal condition of 4.8..7.2 (if implemented that way).
        
        val state = HydroponicState(
            currentStage = GrowthStage.SEEDLING,
            phLevel = 6.8, 
            isPhKnown = true,
            
            // Healthy otherwise
            tds = 400.0,
            isTdsKnown = true,
            waterLevel = "optimal",
            isWaterLevelKnown = true,
            lightsOn = false, 
            isLightsKnown = true,
            isSystemHealthy = true
        )

        val plan = planner.plan(state)
        // If Goal is satisfied (pH 6.8 is "Safe enough"), then plan should be empty or null (assuming other goals met)
        // Or specifically verify "Request User pH Fix" is NOT in plan.
        
        if (plan != null) {
            assertFalse(plan.any { it.name == "Request User pH Fix" }, "Should NOT request pH fix for 6.8 (Drift Tolerance)")
        }
    }

    @Test
    fun `Troubleshooting - Pulse Fill`() = runBlocking {
        val api = HydroponicApiClient("http://localhost:0", 0)
        val planner = hydroponicActions(api)

        // State: We think we filled it (tankFilled=true), but waterLevel is still "low".
        val state = HydroponicState(
            waterLevel = "low",
            isWaterLevelKnown = true,
            tankFilled = true, // Key flag
            
            // Healthy otherwise
            currentStage = GrowthStage.SEEDLING,
            phLevel = 6.0,
            isPhKnown = true,
            tds = 400.0,
            isTdsKnown = true,
            lightsOn = false, 
            isLightsKnown = true,
            isSystemHealthy = true
        )

        val plan = planner.plan(state)
        assertNotNull(plan, "Plan should exist for troubleshooting")
        assertTrue(plan!!.any { it.name == "Pulse Fill Recovery" }, "Should plan Pulse Fill Recovery")
    }
}
