package org.besomontro.llm

import ai.koog.prompt.params.LLMParams
import org.besomontro.db.DatabaseLogger
import org.besomontro.planner.HydroponicState
import java.time.LocalDateTime

object StateSummarizer {
    
    suspend fun summarize(state: HydroponicState, wakeUpTime: LocalDateTime): String {
        try {
            val logs = DatabaseLogger.getRecentLogs(15).joinToString("\n")

            val promptText = """
                You are the consciousness of the ZombiePlant AI.
                Analyze the system state, recent history, and plant photo.
                
                [CURRENT DATE]
                ${java.time.LocalDate.now()}
                
                [SYSTEM STATE]
                $state
                
                [SCHEDULE]
                Next Wake Up: $wakeUpTime
                
                [RECENT LOGS]
                $logs
                
                [CONTEXT]
                Nutrients: FloraSeries (Micro, Gro, Bloom)
                Water Capacity: 6.333 Liters
                
                [CAPABILITIES]
                - You CAN dose nutrients (smartFeed) and flush the tank.
                - You CANNOT adjust pH directly (no pH Up/Down pumps). 
                - If pH is critical, acknowledge you cannot fix it and verify if you have asked the user for help.
                
                Provide a concise, human-readable status report (1-3 sentences) and your internal 'thoughts' on current health and next steps. Be expressive but professional.
            """.trimIndent()

            val promptConfig = ai.koog.prompt.dsl.prompt("state-summary", params = LLMParams(temperature = 0.7)) {
                user {
                    text(promptText)
                }
            }

            val brain = Brains.GeminiFlash
            val executor = ai.koog.prompt.executor.llms.SingleLLMPromptExecutor(brain.client())
            val model = brain.model()

            val result = executor.execute(promptConfig, model)
            
            return result.toString()

        } catch (e: Exception) {
            return "Error generating summary: ${e.message}\n\nOriginal State: $state"
        }
    }
}
