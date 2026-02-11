package org.besomontro.llm.knowledge

import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.FactType
import ai.koog.agents.memory.model.MemoryScope
import ai.koog.agents.memory.model.SingleFact
import ai.koog.agents.memory.providers.AgentMemoryProvider
import org.besomontro.llm.subjects.AgentKnowledgeBase
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/*
 * --- 2. Define Your Static Concept ---
 * This val defines the *category* for the rule.
 * You can define this once as a top-level constant.
 */
val tinyTomConcept = Concept(
    keyword = "frcp-rule-definition",
    description = "Details about Tiny Tim Tomato plant.",
    factType = FactType.SINGLE
)

/**
 * This is the "seeding" function you run at startup.
 * It ensures the agent's core knowledge is loaded into memory.
 */
@OptIn(ExperimentalTime::class)
suspend fun seedRule34AgentKnowledge(memoryProvider: AgentMemoryProvider) {
    // Define the scope this knowledge is available in
    val agentScope = MemoryScope.Product("ZombiePlant")

    // 1. Check if the Fact already exists
    val existingFacts = memoryProvider.load(
        concept = tinyTomConcept,
        subject = AgentKnowledgeBase,
        scope = agentScope
    )

    // 2. If the list is empty, the Fact does not exist. Save it.
    if (existingFacts.isEmpty()) {
        println("Memory is empty. Seeding foundational knowledge...")

        // Create the Fact instance
        val rule34Fact = SingleFact(
            concept = tinyTomConcept,
            value = tinyTomDetails,
            timestamp = Clock.System.now().toEpochMilliseconds()
        )

        // Save the new Fact to the memory provider
        memoryProvider.save(
            fact = rule34Fact,
            subject = AgentKnowledgeBase,
            scope = agentScope
        )

        println("...Seeding - complete.")

    } else {
        println("Foundational knowledge already exists. Skipping seed.")
    }
}

private val tinyTomDetails = """
    Plant Identity:
    - Name: Tiny Tim Tomato
    - Species: Solanum lycopersicum
    - Type: Dwarf / Determinate (Bush variety, grows ~12-18 inches tall)
    - Seed Source: TKE Farms (Sykesville, MD)
    - Classification: Heirloom, Non-GMO

    Timeline & Status:
    - Date Planted: 01/03/2026
    - Current Date: 01/10/2026 (Day 7)
    - Status: Germination Phase (Expected Germination: 7-14 days)
    - Estimated Harvest: ~March 4th, 2026 (60 days from transplant)

    Environmental Targets (Seedling Stage):
    - Temperature (Air): 70-85°F (Packet Recommendation)
    - Water Temperature: 68-72°F (Ideal for DWC/Hydro)
    - pH Range: 5.5 - 6.5
    - Target EC: 0.8 - 1.2
    - Humidity: 70-80% (Keep humidity dome ON until true leaves appear)
    - Light Cycle: 16 Hours ON / 8 Hours OFF

    Nutrient Plan (Flora Series):
    - Current Phase: Seedling (Week 1-2)
    - Nutrient Strength: Very Low (EC ~0.4 - 0.8)
    - Strategy: Primarily fresh water. If roots are visible, add 1/4 strength FloraMicro + FloraGro.
    - Warning: This is a DWARF variety. It is sensitive to nutrient burn. Do not exceed EC 2.0 even in full bloom.

    Agent Instructions:
    1. Germination Watch: We are at Day 7. Use the camera to look for green sprouts breaking the LECA surface.
    2. Dome Management: If you see condensation on the dome in photos, humidity is good. If dry, alert user.
    3. Growth Habit: This plant is DETERMINATE. It will grow to a set size and stop. Do not recommend pruning main stems, only dead leaves.
""".trimIndent()