package org.besomontro

import kotlinx.coroutines.delay
import org.besomontro.db.DatabaseLogger
import org.besomontro.llm.Brains
import org.besomontro.llm.Secre
import org.besomontro.llm.StateSummarizer
import org.besomontro.llm.plannerAgent
import org.besomontro.tools.tools.Scheduler
import kotlin.time.Duration.Companion.milliseconds

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
suspend fun main() {
    org.besomontro.cli.TerminalFormatter.printStartupLogo()
    println("Select Agent Mode:")
    println("1. AUTO (Autonomous decisions)")
    println("2. MANUAL (Ask for permission)")
    print("Enter choice (default 1): ")
    
    val input = readlnOrNull()?.trim()
    val mode = if (input == "2") org.besomontro.llm.AgentMode.MANUAL else org.besomontro.llm.AgentMode.AUTO
    
    println("Starting agent in $mode mode.")
    
    // --- Persistence & Startup Logic ---
    var config = org.besomontro.config.ConfigManager.load()
    
    // 1. Confirm Germination Date
    println("\n--- Configuration ---")
    val defaultDate = config.germinationDate ?: "2026-01-03"
    println("Stored Germination Date: $defaultDate")
    print("Is this correct? (Y/n): ")
    val dateResp = readlnOrNull()?.trim()?.lowercase()
    
    val germinationDateStr = if (dateResp == "n" || dateResp == "no") {
        print("Enter Germination Date (YYYY-MM-DD): ")
        val newDate = readlnOrNull()?.trim() ?: defaultDate
        println("Updated Germination Date to: $newDate")
        newDate
    } else {
        defaultDate
    }

    // 2. Plant Life Verification
    val today = java.time.LocalDate.now()
    val lastCheckStr = config.lastPlantDetectionDate
    val lastCheck = if (lastCheckStr != null) java.time.LocalDate.parse(lastCheckStr) else today.minusDays(10)
    
    val daysSinceCheck = java.time.temporal.ChronoUnit.DAYS.between(lastCheck, today)
    
    if (daysSinceCheck > 3) {
        println("\n>>> ALERT: Plant life not verified for $daysSinceCheck days.")
        print("Is the plant still alive? (Y/n): ")
        val aliveResp = readlnOrNull()?.trim()?.lowercase()
        
        if (aliveResp == "n" || aliveResp == "no") {
            println(">>> TERMINATING AGENT. Please replant and reset system.")
            return // Exit agent
        } else {
             println(">>> Thank you. Verification logged.")
             config = config.copy(lastPlantDetectionDate = today.toString())
        }
    } else {
         // Auto-renew if recently checked (or just assume alive for now since we lack vision logic yet)
         // In real system, we'd run vision check here.
         config = config.copy(lastPlantDetectionDate = today.toString())
    }

    // Update Config
    config = config.copy(germinationDate = germinationDateStr)
    org.besomontro.config.ConfigManager.save(config)
    
    // Calculate Days & Stage
    val germDate = java.time.LocalDate.parse(germinationDateStr)
    val daysSinceGermination = java.time.temporal.ChronoUnit.DAYS.between(germDate, today).toInt()
    
    val currentStage = org.besomontro.planner.HydroponicState.calculateStage(daysSinceGermination)
    
    println("\n--- Status ---")
    println("Day: $daysSinceGermination")
    println("Stage: $currentStage")
    println("------------------\n")

    DatabaseLogger.init()

    val scheduler = Scheduler()
    
    // Initialize State with Biological Context
    var currentState = org.besomontro.planner.HydroponicState(
        currentStage = currentStage,
        daysSinceGermination = daysSinceGermination
    )
    
    while (true) {
        println("----------------------------------------------------------------")
        println("Starting agent run (GOAP)...")
        val secreAgent = Secre.RfpSitter.plannerAgent(Brains.GeminiFlash, scheduler, mode)
        var lastRunState: org.besomontro.planner.HydroponicState? = null

        try {
            // GOAP Agent expects an initial state to start planning from.
            // Ensure stage is always current (in case we cross midnight or run for days)
            val freshDays = java.time.temporal.ChronoUnit.DAYS.between(germDate, java.time.LocalDate.now()).toInt()
            val freshStage = org.besomontro.planner.HydroponicState.calculateStage(freshDays)

            val stateToRun = currentState.copy(
                currentStage = freshStage,
                daysSinceGermination = freshDays,
                doseCount = 0 // Reset per wake cycle
            )
            
            val result = secreAgent.run(stateToRun)
            lastRunState = result
            
            // Persist state for next run, but invalidate sensor knowledge to force re-measurement
            currentState = result.copy(
                isPhKnown = false,
                isTdsKnown = false,
                isWaterLevelKnown = false
            )
        } catch (e: Exception) {
            println("Agent run failed: ${e.message}")
            e.printStackTrace()
            lastRunState = currentState
        }
        

        // Smart Scheduling Logic
        val now = java.time.LocalDateTime.now()
        val nextDelayMs = scheduler.nextDelay

        // Define verify critical schedule points (6am lights on, 10pm lights off)
        val today6am = now.withHour(6).withMinute(0).withSecond(0).withNano(0)
        val today10pm = now.withHour(22).withMinute(0).withSecond(0).withNano(0)
        val tomorrow6am = today6am.plusDays(1)
        val tomorrow10pm = today10pm.plusDays(1)

        val schedulePoints = listOf(today6am, today10pm, tomorrow6am, tomorrow10pm)
        
        // Find the next schedule point that is in the future
        val nextSchedulePoint = schedulePoints
            .filter { it.isAfter(now) }
            .minOrNull()
        
        var actualDelayMs = nextDelayMs

        if (nextSchedulePoint != null) {
            val msUntilSchedule = java.time.Duration.between(now, nextSchedulePoint).toMillis()
            // Add a small buffer (5s) to ensure we wake up *after* the target time
            val msUntilScheduleWithBuffer = msUntilSchedule + 5000 
            
            if (msUntilScheduleWithBuffer < nextDelayMs) {
                actualDelayMs = msUntilScheduleWithBuffer
                println("adjusted sleep to wake up for schedule event at $nextSchedulePoint")
            }
        }

        val hours = actualDelayMs / 3600000.0
        val wakeUpTime = now.plus(actualDelayMs, java.time.temporal.ChronoUnit.MILLIS)
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss a")

        println(" Generating Summary...")
    println(org.besomontro.cli.TerminalFormatter.formatSummary(StateSummarizer.summarize(lastRunState, wakeUpTime)))

        println("Sleeping for ${"%.2f".format(hours)} hrs (until ${wakeUpTime.format(formatter)})...")
        delay(actualDelayMs.milliseconds)
    }
}