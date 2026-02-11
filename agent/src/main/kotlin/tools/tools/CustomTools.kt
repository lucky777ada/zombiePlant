package org.besomontro.tools.tools

import ai.koog.agents.core.tools.Tool
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.besomontro.db.DatabaseLogger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class SayArgs(val value: String)

class CustomSayToUser : Tool<SayArgs, String>(
    SayArgs.serializer(),
    String.serializer(),
    "say_to_user",
    "Send a message to the user."
) {
    override suspend fun execute(args: SayArgs): String {
        println("Agent says: ${args.value}")
        return "Message sent."
    }
}

@Serializable
data class AskArgs(val question: String)

class CustomAskUser : Tool<AskArgs, String>(
    AskArgs.serializer(),
    String.serializer(),
    "ask_user",
    "Ask the user a question and wait for their response."
) {
    override suspend fun execute(args: AskArgs): String {
        println("Agent asks: ${args.question}")
        print("> ")
        val answer = readln()
        return answer
    }
}

class GetCurrentTimeTool : Tool<Unit, String>(
    Unit.serializer(),
    String.serializer(),
    "get_current_time",
    "Returns the current system date and time."
) {
    override suspend fun execute(args: Unit): String {
        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return current.format(formatter)
    }
}

@Serializable
data class LogArgs(val action: String, val details: String)

class LogActionTool : Tool<LogArgs, String>(
    LogArgs.serializer(),
    String.serializer(),
    "log_action",
    "Log an action to the database. 'action' should be short (e.g. PUMP_ON), 'details' can be longer."
) {
    override suspend fun execute(args: LogArgs): String {
        DatabaseLogger.log(args.action, args.details)
        return "Action logged."
    }
}
