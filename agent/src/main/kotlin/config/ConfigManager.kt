package org.besomontro.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

@Serializable
data class AgentConfig(
    val germinationDate: String? = "2026-01-03", // ISO-8601 YYYY-MM-DD
    val lastPlantDetectionDate: String? = null
)

object ConfigManager {
    private const val CONFIG_FILE = "config.json"
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun load(): AgentConfig {
        val file = File(CONFIG_FILE)
        return if (file.exists()) {
            try {
                json.decodeFromString<AgentConfig>(file.readText())
            } catch (e: Exception) {
                println("Global: Failed to load config, using defaults. Error: ${e.message}")
                AgentConfig()
            }
        } else {
            AgentConfig()
        }
    }

    fun save(config: AgentConfig) {
        try {
            val file = File(CONFIG_FILE)
            val jsonString = json.encodeToString(AgentConfig.serializer(), config)
            file.writeText(jsonString)
        } catch (e: Exception) {
            println("Global: Failed to save config: ${e.message}")
        }
    }
}
