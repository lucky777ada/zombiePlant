package skills.hydroponic_lifecycle_manager.examples

enum class GrowthStage { SEEDLING, VEGETATIVE, FLOWERING }

data class PlantState(
    val daysSinceGermination: Int,
    val currentPh: Double,
    val currentTds: Double
)

fun determineStage(days: Int): GrowthStage {
    return when {
        days <= 14 -> GrowthStage.SEEDLING
        days <= 35 -> GrowthStage.VEGETATIVE
        else -> GrowthStage.FLOWERING
    }
}

fun getTargets(stage: GrowthStage): Map<String, Any> {
    return when (stage) {
        GrowthStage.SEEDLING -> mapOf("tds" to 300..500, "ph" to 5.8..6.3)
        GrowthStage.VEGETATIVE -> mapOf("tds" to 800..1200, "ph" to 5.5..6.0)
        GrowthStage.FLOWERING -> mapOf("tds" to 1000..1400, "ph" to 6.0..6.5)
    }
}

fun decideAction(state: PlantState): String {
    val stage = determineStage(state.daysSinceGermination)
    val targets = getTargets(stage)
    val targetTds = targets["tds"] as IntRange
    val targetPh = targets["ph"] as ClosedFloatingPointRange<Double>

    if (state.currentTds !in targetTds.start.toDouble()..targetTds.endInclusive.toDouble()) {
        if (state.currentTds < targetTds.start) return "DOSE_NUTRIENTS"
        if (state.currentTds > targetTds.endInclusive) return "FLUSH_SYSTEM" // Simple logic, refine with hysteria
    }

    if (state.currentPh !in targetPh) {
        return "ADJUST_PH"
    }

    return "NO_ACTION"
}
