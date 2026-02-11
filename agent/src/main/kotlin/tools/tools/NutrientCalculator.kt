package org.besomontro.tools.tools

import kotlinx.serialization.Serializable

enum class GrowthStage {
    SEEDLING,       // First 2 weeks, low strength (EC ~0.4-0.6)
    VEGETATIVE,     // Leaf growth, high Nitrogen (EC ~1.2-1.5)
    FLOWERING,      // Fruit production, high P/K (EC ~1.5-2.0)
    FLUSH           // End of life or salt clearing (Plain water)
}

@Serializable
data class FloraDose(
    val microMl: Double,
    val groMl: Double,
    val bloomMl: Double,
    val note: String
)

object NutrientCalculator {
    // Exact volume of the ZombiePlant V1.0 reservoir
    private const val TANK_VOLUME_LITERS = 6.333

    // Conversion factor: 1 Gallon = 3.785 Liters
    // We calculate multiplier to scale "Per Gallon" recipes to our tank
    private const val TANK_MULTIPLIER = TANK_VOLUME_LITERS / 3.785

    fun calculateDose(stage: GrowthStage): FloraDose {
        return when (stage) {
            GrowthStage.SEEDLING -> {
                // Recipe: ~2.5ml per gallon of all three (Light mix)
                // Goal: Balanced NPK but very gentle
                FloraDose(
                    microMl = format(2.5 * TANK_MULTIPLIER), // ~4.2 ml
                    groMl = format(2.5 * TANK_MULTIPLIER),   // ~4.2 ml
                    bloomMl = format(2.5 * TANK_MULTIPLIER), // ~4.2 ml
                    note = "Target EC: 0.6. Safe for young roots."
                )
            }
            GrowthStage.VEGETATIVE -> {
                // Recipe: "Aggressive Veg" (High Gro)
                // Micro: 5ml/gal, Gro: 10ml/gal, Bloom: 5ml/gal
                FloraDose(
                    microMl = format(5.0 * TANK_MULTIPLIER),  // ~8.4 ml
                    groMl = format(10.0 * TANK_MULTIPLIER),   // ~16.7 ml
                    bloomMl = format(5.0 * TANK_MULTIPLIER),  // ~8.4 ml
                    note = "Target EC: 1.3. High Nitrogen for leafy growth."
                )
            }
            GrowthStage.FLOWERING -> {
                // Recipe: "Bloom" (High Phosphorus/Potassium)
                // Micro: 5ml/gal, Gro: 5ml/gal, Bloom: 15ml/gal
                FloraDose(
                    microMl = format(5.0 * TANK_MULTIPLIER),  // ~8.4 ml
                    groMl = format(5.0 * TANK_MULTIPLIER),    // ~8.4 ml
                    bloomMl = format(15.0 * TANK_MULTIPLIER), // ~25.1 ml
                    note = "Target EC: 1.8. Phosphorus boost for fruit."
                )
            }
            GrowthStage.FLUSH -> {
                FloraDose(0.0, 0.0, 0.0, "Plain pH-balanced water only.")
            }
        }
    }

    // Rounds to 1 decimal place for pump precision
    private fun format(value: Double): Double {
        return "%.1f".format(value).toDouble()
    }
}