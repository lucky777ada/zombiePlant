package org.besomontro.planner

import kotlinx.serialization.Serializable

@Serializable
enum class GrowthStage { SEEDLING, VEGETATIVE, FLOWERING }

@Serializable
data class HydroponicState(
    // Biological Context
    val currentStage: GrowthStage = GrowthStage.SEEDLING,
    val daysSinceGermination: Int = 0,
    // Sensor Readings
    val phLevel: Double? = null,
    val waterLevel: String? = null, // "low", "optimal", "overflow"
    val temperature: Double? = null,
    val tds: Double? = null,
    val lastFlushTimestamp: Long? = null,
    val lastPhNotificationTimestamp: Long? = null,
    val lastDiagnosticTimestamp: Long? = null,
    val lastFeedTimestamp: Long? = null,
    val highTdsSince: Long? = null,
    
    // System Status
    val lightsOn: Boolean = false,
    val pumpStatus: Map<String, Boolean> = emptyMap(), // pump_id -> active
    
    // Knowledge Flags (have we measured recently?)
    val isPhKnown: Boolean = false,
    val isTdsKnown: Boolean = false,
    val isWaterLevelKnown: Boolean = false,
    val isLightsKnown: Boolean = false,
    val isSystemHealthy: Boolean = true, // General error flag
    
    // Action Completion Flags
    val tankFilled: Boolean = false,
    val tankEmptied: Boolean = false,
    val flushCompleted: Boolean = false,
    
    // Limits
    val doseCount: Int = 0
) {
    companion object {
        fun calculateStage(daysSinceGermination: Int): GrowthStage = when {
            daysSinceGermination < 15 -> GrowthStage.SEEDLING
            daysSinceGermination < 36 -> GrowthStage.VEGETATIVE
            else -> GrowthStage.FLOWERING
        }
    }
}
