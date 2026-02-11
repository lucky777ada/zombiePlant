package org.besomontro.db

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

import org.besomontro.config.LocalProperties

object AgentLogs : Table("agent_logs") {
    val id = integer("id").autoIncrement()
    val timestamp = long("timestamp")
    val action = varchar("action", 255)
    val details = text("details")

    override val primaryKey = PrimaryKey(id)
}


object DatabaseLogger {
    private val URL = LocalProperties["db.url"] 
        ?: error("Missing 'db.url' in local.properties")
    private const val DRIVER = "org.postgresql.Driver"
    private val USER = LocalProperties["db.user"] 
        ?: error("Missing 'db.user' in local.properties")
    private val PASSWORD = LocalProperties["db.password"] 
        ?: error("Missing 'db.password' in local.properties")

    fun init() {
        try {
            Database.connect(URL, driver = DRIVER, user = USER, password = PASSWORD)
            transaction {
                SchemaUtils.create(AgentLogs)
            }
            println("Database connected and schema initialized.")
        } catch (e: Exception) {
            println("Failed to connect to database: ${e.message}")
        }
    }

    fun log(action: String, details: String) {
        try {
            transaction {
                AgentLogs.insert {
                    it[timestamp] = System.currentTimeMillis()
                    it[AgentLogs.action] = action
                    it[AgentLogs.details] = details
                }
            }
            println("Logged action to DB: $action")
        } catch (e: Exception) {
            println("Failed to log to DB: ${e.message}")
        }
    }

    fun getRecentLogs(limit: Int = 15): List<String> {
        return try {
            transaction {
                AgentLogs.selectAll()
                    .orderBy(AgentLogs.timestamp, SortOrder.DESC)
                    .limit(limit)
                    .map {
                        val ts = java.time.Instant.ofEpochMilli(it[AgentLogs.timestamp])
                            .atZone(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                        "[$ts] ${it[AgentLogs.action]}: ${it[AgentLogs.details]}"
                    }
                    .reversed()
            }
        } catch (e: Exception) {
            listOf("Error retrieving logs: ${e.message}")
        }
    }

    fun getLastPhotoBase64(): String? {
        return try {
            transaction {
                AgentLogs.selectAll().where { AgentLogs.action eq "CAPTURE_PLANT_PHOTO" }
                    .orderBy(AgentLogs.timestamp, SortOrder.DESC)
                    .limit(1)
                    .map { it[AgentLogs.details] }
                    .firstOrNull()
                    ?.let { jsonStr ->
                        // Simple regex extraction to avoid full JSON parsing overhead if just grabbing the string
                        // Looking for "base64_data":"..."
                        val match = "\"base64_data\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonStr)
                        match?.groupValues?.get(1)
                    }
            }
        } catch (e: Exception) {
            null
        }
    }
}
