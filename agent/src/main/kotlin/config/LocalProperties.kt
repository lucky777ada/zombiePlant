package org.besomontro.config

import java.io.FileInputStream
import java.util.Properties

object LocalProperties {
    private val properties = Properties()

    init {
        val file = java.io.File("local.properties")
        if (file.exists()) {
            try {
                properties.load(FileInputStream(file))
            } catch (e: Exception) {
                println("Failed to load local.properties: ${e.message}")
            }
        } else {
            println("Warning: local.properties not found.")
        }
    }

    operator fun get(key: String): String? = properties.getProperty(key)
    
    fun get(key: String, default: String): String = properties.getProperty(key, default)
}
