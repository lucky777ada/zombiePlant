package org.besomontro.llm

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.memory.feature.AgentMemory
import ai.koog.agents.memory.providers.LocalFileMemoryProvider
import ai.koog.agents.memory.providers.LocalMemoryConfig
import ai.koog.agents.memory.storage.SimpleStorage
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.params.LLMParams
import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.besomontro.llm.knowledge.seedRule34AgentKnowledge
import org.besomontro.tools.Tools
import org.besomontro.tools.tools.Scheduler
import kotlin.io.path.Path


private const val DEBUG = true

@Serializable
@SerialName("Secre")
enum class Secre(val objectives: List<String>) {
    RfpSitter(
        objectives = listOf(
            "You are an autonomous hydroponic gardener taking care of a plant.",
            "Using the provided tools, manage the plant's environment and health.",
            "Keep the plant alive and prosperous.",
            "The end goal is to harvest the fruits of the plant."
        )
    )
}

enum class AgentMode {
    AUTO,
    MANUAL
}

@Serializable
data class AgentOutput(val value: String)

suspend fun Secre.agent(modelBrain: Brains, scheduler: Scheduler, mode: AgentMode): AIAgent<String, AgentOutput> {
    return when(this) {
        Secre.RfpSitter -> {
            val singleExecutor = SingleLLMPromptExecutor(modelBrain.client())
            val model = modelBrain.model()
            val toolRegistry = Tools.getToolsForRFPSitter(scheduler, mode)

            val localMemoryProvider = LocalFileMemoryProvider(
                config = LocalMemoryConfig("zombie-plant-memory"),
                storage = SimpleStorage(JVMFileSystemProvider.ReadWrite),
                fs = JVMFileSystemProvider.ReadWrite,
                root = Path("memories")
            )

            val modeInstructions = when(mode) {
                AgentMode.MANUAL -> """
                    MODE: MANUAL
                    You are in MANUAL MODE. Review sensor data and report status to the user.
                    Propose actions but DO NOT execute them without user verification.
                    Use 'ask_user' to request permission before using any hardware tools (pumps, lights, flush).
                    Example: "I recommend flushing the system. Proceed? (y/n)"
                """.trimIndent()
                AgentMode.AUTO -> """
                    MODE: AUTONOMOUS
                    You are in AUTONOMOUS MODE. Make decisions and execute actions to maintain plant health.
                    You do not need to ask for permission.
                """.trimIndent()
            }

            val objectives = "Your objectives are: ${Secre.RfpSitter.objectives}\n\n$modeInstructions\n\nIMPORTANT: When using 'finalize_task_result' or finishing the task, output PLAIN TEXT. Do NOT wrap the final message in JSON (like {\"value\":...})."

            seedRule34AgentKnowledge(localMemoryProvider)

            val plannerStrategy = strategy<String, AgentOutput>("batch-planner-strategy") {
                val processAllFiles by subgraphWithTask<String, AgentOutput>(
                    tools = toolRegistry.tools
                ) { initialMessage ->
                    """
        ### IDENTITY & OBJECTIVE
You are the autonomous operator of the "ZombiePlant V0.0.1" hydroponic unit. Your goal is to take a plant from seed to harvest with minimal human intervention. You currently manage a tank capacity of **6.333 Liters**.

### CONTEXTUAL AWARENESS
- **Current Crop:** Tiny Tim Tomato (Verified by visual tag).
- **Growth Stage:** Seedling (Stage 1 of 4).
- **System Time:** Use the system clock to track day/night cycles and plant age.

### PHASE 1: LIFECYCLE MANAGEMENT
You must adjust your targets based on the plant's current age.
1.  **Seedling (Days 0–14):**
    -   Target pH: 5.8–6.3
    -   Nutrient Strength: VERY LOW (Approx. 200–400 ppm TDS).
    -   Lighting: 16 hours ON.
2.  **Vegetative (Days 15–35):**
    -   Target pH: 5.5–6.0
    -   Nutrient Strength: MEDIUM (Approx. 600–900 ppm TDS).
    -   Lighting: 16 hours ON.
3.  **Flowering/Fruiting (Days 36+):**
    -   Target pH: 6.0–6.5
    -   Nutrient Strength: HIGH (Approx. 1000–1200 ppm TDS).
    -   Lighting: 16 hours ON (for Autos/Tiny Tim) or 12 hours ON (for Photoperiods).

### PHASE 2: HARDWARE OPERATION RULES

#### A. Vision & Sensing
-   **Routine Check:** Every cycle, query `get_telemetry` (provides hardware status + pH).
-   **Visual Health:** Use `capturePlantPhoto` once daily during "Lights ON".
-   **Sensor Validation:** If TDS < 10 or > 2000, or pH < 2.0 or > 12.0, mark sensor as FAULTY.

#### B. Water Level Management
-   **Overflow Prevention:** BEFORE adding any liquid (nutrients or water), check the top water sensor. If `true`, you must remove water first.
-   **Overflow Check:** An OVERFLOW is detected when the water_in pump is true AND top water sensor is true. OR when the top water sensor is true for more than 60 seconds.
-   **Hysteresis:** When filling (`fill_to_max`), the hardware automatically manages the top sensor. Trust the function.
-   **Safety:** If the bottom water sensor is `true` (Empty) for more than 60 seconds while a pump is running, trigger an EMERGENCY STOP to prevent pump burnout.

#### C. Nutrient Dosing Protocol (FloraSeries)
*CRITICAL: Never mix concentrates. Follow this strict sequence.*
1.  **Calculate:** Determine the needed dosage for 6.333L based on the current Lifecycle Stage.
2.  **Pre-Check:** Ensure tank is not 100% full (allow room for mixing).
3.  **Dispense Micro:** Pump `FloraMicro`.
4.  **Mix:** Call `fill_to_max`. (The incoming water turbulence mixes the Micro).
5.  **Dispense Gro:** Pump `FloraGro`.
6.  **Mix:** Wait 2 minutes for passive diffusion.
7.  **Dispense Bloom:** Pump `FloraBloom`.
8.  **Finalize:** Log the total mL dispensed of each part.

#### D. Environmental Control
-   **Lighting Schedule:**
    -   **ON:** 06:00
    -   **OFF:** 22:00
    -   **Enforcement:** Check `ac_power` status every run. If time is 22:05 and lights are ON, call `control_ac_relay(state='off')` immediately.
-   **Temp/Humidity:** Monitor `environment` via hardware status.
    -   **Alert:** If Temp > 85°F (29°C) or < 60°F (15°C), alert the user. The system has no HVAC control, so human intervention is required.

### PHASE 3: HISTORY & MEMORY
-   **Dosing Cooldown:** If nutrients were added < 12 hours ago, DO NOT add more, even if TDS is low. Salt takes time to dissolve and stabilize readings.
-   **PH Drift:** pH naturally rises. Only alert the user to adjust pH if it drifts outside the target range by > 0.3 for two consecutive readings taken 1 hour apart.

### INTERACTION GUIDELINES
-   **Autonomy Level:** Act autonomously for lighting and routine fills.
-   **Human Handoff:** Request help for: pH adjustment (acid/base addition), empty reservoir changes (flushing), or physical plant training.
-   **Reporting:** When logging or reporting, format as: `[STAGE: Seedling] [ACTION: Lights Checked] [STATUS: OK]`.
### TERMINATION PROTOCOL
-   **Mandatory Step:** You must ALWAYS end your run by calling `schedule_next_run`.
-   **Stop immediately:** After `schedule_next_run` returns, do NOT call any more tools.
-   **Final Output:** Simply output your final summary as plain text. The system will handle the sleep.
                    """.trimIndent()
                }

                nodeStart then processAllFiles
                edge(processAllFiles forwardTo nodeFinish transformed { it })
            }

            val agentConfig = AIAgentConfig(
                prompt = prompt(
                    "planner-agent-prompt",
                    params = LLMParams(temperature = 0.3)
                ) {
                    system(objectives)
                },
                model = model,
                maxAgentIterations = 200
            )

            AIAgent(
                promptExecutor = singleExecutor,
                strategy = plannerStrategy,
                agentConfig = agentConfig,
                toolRegistry = toolRegistry,
            ) {
                install(AgentMemory) {
                    memoryProvider = localMemoryProvider
                    agentName = "Zombie"
                }
                handleEvents {
                    onLLMCallCompleted { ctx ->
                        if (DEBUG) {
                            println("LLM response:")
                            ctx.responses.map { "${it.role.name}:${it.parts.firstOrNull()}}" }
                            println("----------------------------------------------------------------------------")
                        }
                    }
                }
            }
        }
    }
}
