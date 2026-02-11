package org.besomontro.tools.tools

import ai.koog.agents.core.tools.Tool
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
data class CalculateNutrientsArgs(val stage: String)

class CalculateNutrientsTool : Tool<CalculateNutrientsArgs, FloraDose>(
    CalculateNutrientsArgs.serializer(),
    FloraDose.serializer(),
    "calculate_nutrients",
    "Calculates the exact nutrient dosage (FloraSeries) for the current tank volume based on growth stage. Stage must be one of: SEEDLING, VEGETATIVE, FLOWERING, FLUSH."
) {
    override suspend fun execute(args: CalculateNutrientsArgs): FloraDose {
        return try {
            val stageEnum = GrowthStage.valueOf(args.stage.uppercase())
            NutrientCalculator.calculateDose(stageEnum)
        } catch (e: IllegalArgumentException) {
            // Fallback or error indication
            FloraDose(0.0, 0.0, 0.0, "Invalid stage: ${args.stage}. Choose: SEEDLING, VEGETATIVE, FLOWERING, FLUSH.")
        }
    }
}
