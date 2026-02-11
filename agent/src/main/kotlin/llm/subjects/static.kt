package org.besomontro.llm.subjects

import ai.koog.agents.memory.model.MemorySubject

/*
 * --- 1. Define Your Static Subject ---
 * This object represents the agent's internal knowledge base.
 * You only need to define this class once.
 */
object AgentKnowledgeBase : MemorySubject() {
    override val name: String = "agent_knowledge_base"
    override val promptDescription: String = "The agent's foundational, static knowledge and rules."
    override val priorityLevel: Int = 10 // Core knowledge
}