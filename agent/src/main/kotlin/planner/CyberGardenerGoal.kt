package org.besomontro.planner

import ai.koog.agents.planner.goap.Goal

fun maintainHealthyState(): Goal<HydroponicState> = Goal(
    name = "Maintain Healthy System",
    description = "Keep pH 5.5-6.5, TDS 200-800, and water level optimal",
    value = { 100.0 },
    cost = { 0.0 }, // Maintenance goal has no cost to exist, actions have cost
    condition = { state ->
        state.isPhKnown && 
        state.isWaterLevelKnown &&
        // pH Check: Accept if in range OR if we notified user recently (< 4 hours)
        ((state.phLevel ?: 0.0) in 5.5..6.5 || 
             (System.currentTimeMillis() - (state.lastPhNotificationTimestamp ?: 0L)) < (4 * 60 * 60 * 1000)) &&
        
        // Hysteresis: Accept range 200..800 to avoid excessive flushing
        (state.tds ?: 0.0) in 200.0..800.0 &&
        state.waterLevel == "optimal"
    }
)
