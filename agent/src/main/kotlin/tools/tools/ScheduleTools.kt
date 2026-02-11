package org.besomontro.tools.tools

import ai.koog.agents.core.tools.Tool
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class Scheduler {
    var nextDelay: Long = 4 * 60 * 60 * 1000 // Default 4 hours
}

@Serializable
data class ScheduleArgs(
    val hours: Int = 0,
    val minutes: Int = 0
)

class ScheduleNextRunTool(private val scheduler: Scheduler) : Tool<ScheduleArgs, JsonObject>(
    ScheduleArgs.serializer(),
    JsonObject.serializer(),
    "schedule_next_run",
    "Schedules the next time the agent will wake up. Provide hours and/or minutes from now."
) {
    override suspend fun execute(args: ScheduleArgs): JsonObject {
        val delayMillis = (args.hours * 60 * 60 * 1000L) + (args.minutes * 60 * 1000L)
        scheduler.nextDelay = delayMillis
        return JsonObject(mapOf("status" to JsonPrimitive("Next run scheduled in ${args.hours} hours and ${args.minutes} minutes")))
    }
}
