package org.besomontro.llm

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.planner.AIAgentPlannerStrategy
import ai.koog.agents.planner.PlannerAIAgent
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import org.besomontro.client.HydroponicApiClient
import org.besomontro.planner.HydroponicState
import org.besomontro.tools.tools.Scheduler
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.params.LLMParams

suspend fun Secre.plannerAgent(modelBrain: Brains, scheduler: Scheduler, mode: AgentMode): AIAgent<HydroponicState, HydroponicState> {
    require(this == Secre.RfpSitter) { "Planner only supported for RfpSitter" }

    val singleExecutor = SingleLLMPromptExecutor(modelBrain.client())
    val model = modelBrain.model()
    
    val delayMillis = if (mode == AgentMode.AUTO) 2000L else 0L
    val api = HydroponicApiClient(org.besomontro.tools.Tools.BASE_URL, delayMillis)

    val planner = org.besomontro.planner.hydroponicActions(api)
    
    val strategy = AIAgentPlannerStrategy(
        name = "hydroponic-planner",
        planner = planner
    )
    
    return PlannerAIAgent(
        promptExecutor = singleExecutor,
        strategy = strategy,
        agentConfig = AIAgentConfig(
            model = model,
            maxAgentIterations = 50,
            prompt = prompt("planner-agent-prompt", params = LLMParams(temperature = 0.1)) {
                system("You are a hydroponic planner agent.")
            }
        )
    )
}
