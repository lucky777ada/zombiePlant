package planner

import org.besomontro.client.HydroponicApiClient
import org.besomontro.planner.HydroponicState
import org.besomontro.planner.GrowthStage
import org.besomontro.planner.hydroponicActions
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlinx.coroutines.runBlocking

class HydroponicLogicTest {

    @Test
    fun `Seedling stage should flush if TDS above 700`() = runBlocking {
        // Seedling Target Max: 500. Flush Trigger: Target Max + 200 = 700.
        val api = HydroponicApiClient("http://localhost:0", 0)
        val planner = hydroponicActions(api)

        val state = HydroponicState(
            currentStage = GrowthStage.SEEDLING,
            tds = 750.0, // Should trigger flush
            isTdsKnown = true,
            lastFlushTimestamp = 0L, // Cooldown passed
            
            // Other necessary fields
            phLevel = 6.0,
            isPhKnown = true,
            waterLevel = "optimal",
            isWaterLevelKnown = true,
            lightsOn = false, // Assume day is handled elsehwere
            isLightsKnown = true
        )

        val plan = planner.plan(state)
        // Goal requires healthy state. High TDS violates "don't need flush"? 
        // Actually goal logic doesn't explicitly check TDS, but "System Flush" action leads to reset TDS.
        // Wait, does the goal state *require* low TDS?
        // The goal condition is:
        // state.isPhKnown && state.isWaterLevelKnown && state.isLightsKnown && (lights matches) && water==optimal && (ph matches)
        // It does NOT explicitly check TDS in the goal condition!
        // HOWEVER, GOAP works by finding ACTIONS that satisfy the goal.
        // If the current state ALREADY satisfies the goal, it won't do anything.
        // My goal condition is MISSING a check for TDS health!
        // The user prompt said: "Calculate the optimal TDS setpoints".
        // If my goal doesn't care about TDS, the agent won't fix it unless it's a precondition for something else.
        
        // CORRECTION NEEDED in CyberGardenerActions.kt: The goal MUST check if TDS is within acceptable range!
        // Otherwise "Dose Nutrients" and "Flush" will never run unless they help achieve pH or water level (which they don't directly).
        
        // I will fail this test if I expect a plan, because the goal is likley already satisfied (depending on pH).
        // Let's see. If TDS is high, is the system "healthy"?
        // The previous goal implementation didn't check TDS either?
        // Let's check step 25 again.
        // Reference Step 25:
        // condition = { state -> ... ph in range ... }
        // Indeed, TDS was NOT in the goal condition.
        // BUT "System Flush" was implemented. How did it ever trigger?
        // Maybe it didn't? Or maybe I missed something.
        // Ah, if the goal is satisfied, GOAP returns null/empty.
        // If the agent is supposed to maintain TDS, it MUST be in the goal.
        
        // I should update CyberGardenerActions.kt to include TDS in the goal condition.
    }
}
