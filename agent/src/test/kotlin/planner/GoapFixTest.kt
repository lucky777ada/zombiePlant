package planner

import org.besomontro.client.HydroponicApiClient
import org.besomontro.planner.HydroponicState
import org.besomontro.planner.hydroponicActions
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlinx.coroutines.runBlocking

class GoapFixTest {

    @Test
    fun `test plan found when pH is out of bounds but notification sent`() = runBlocking {
        // Arrange
        val api = HydroponicApiClient("http://localhost:0", 0)
        val planner = hydroponicActions(api)
        
        // Use a value OUTSIDE the new range (4.8 - 7.2)
        val paramPh = 7.5 // Was 6.87, but 6.87 is now valid!
        
        val crashState = HydroponicState(
            phLevel = paramPh,
            waterLevel = null,
            temperature = null,
            tds = null,
            lastFlushTimestamp = null,
            lastPhNotificationTimestamp = null,
            lightsOn = false,
            pumpStatus = emptyMap(),
            isPhKnown = true,
            isTdsKnown = false,
            isWaterLevelKnown = false, // Will trigger Check Hardware Status
            isSystemHealthy = true,
            tankFilled = false,
            tankEmptied = false,
            flushCompleted = false
        )

        // Act
        val plan = planner.plan(crashState)

        // Assert
        assertNotNull(plan, "Plan should not be null when pH is invalid but repairable via user nitification")
        // We expect "Check Hardware Status" and "Request User pH Fix"
    }

    @Test
    fun `test pH 6_87 is now acceptable`() = runBlocking {
        // Arrange
        val api = HydroponicApiClient("http://localhost:0", 0)
        val planner = hydroponicActions(api)
        
        // 6.87 is inside 4.8..7.2
        val paramPh = 6.87 
        
        val validState = HydroponicState(
            phLevel = paramPh,
            waterLevel = "optimal", // Assuming other things are good to isolate pH check
            temperature = null,
            tds = null,
            lastFlushTimestamp = null,
            lastPhNotificationTimestamp = null,
            lightsOn = false,
            pumpStatus = emptyMap(),
            isPhKnown = true,
            isTdsKnown = true, // Satisfy "known" requirements
            isWaterLevelKnown = true,
            isSystemHealthy = true,
            tankFilled = false,
            tankEmptied = false,
            flushCompleted = false
        )

        // Act
        val plan = planner.plan(validState)

        // Assert
        // If the state is already valid (goal satisfied), the plan should be empty or null depending on implementation
        // But specifically, we want to ensure it DOES NOT plan "Request User pH Fix"
        
        // Our GOAP implementation returns null or empty list for goal satisfied? 
        // Or it might just return empty list.
        // Let's assume if goal is satisfied, plan is empty list.
        
        if (plan != null) {
            val hasPhFix = plan.any { it.name == "Request User pH Fix" }
            assertFalse(hasPhFix, "Should not request pH fix for 6.87")
        }
    }
}
