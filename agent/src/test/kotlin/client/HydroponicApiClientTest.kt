package client

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import org.besomontro.client.*
import kotlin.test.Test
import kotlin.test.assertEquals

class HydroponicApiClientTest {
    @Test
    fun `executeJob handles api error gracefully`() = runBlocking {
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""{"error": "Internal Error"}"""),
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HydroponicApiClient("http://localhost", engine = mockEngine)

        // This currently throws MissingFieldException because the response doesn't match JobStatus
        // After fix, it should return a failure JobStatus
        try {
            val result = client.fillToMax()
            assertEquals(JobState.failed, result.status)
            assertEquals("submission_failed", result.job_id)
            // If we get here without exception and assertions pass, fix is working
        } catch (e: Exception) {
            println("Caught expected exception for reproduction: $e")
            throw e
        }
    }
    }

    @Test
    fun `FeedRequest ensures amounts_ml is empty dict not null`() {
        // We can verify this by checking the property default
        val request = FeedRequest(recipe = "test")
        assertEquals(emptyMap(), request.amounts_ml)
        
        // Using strict JSON to verify serialization string
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val string = json.encodeToString(FeedRequest.serializer(), request)
        // Check that "amounts_ml":{} is present
        assert(string.contains("\"amounts_ml\":{}")) { "JSON should contain empty dict for amounts_ml, but was: $string" }
    }
    @Test
    fun `dose sends correct request`() = runBlocking {
        val mockEngine = MockEngine { request ->
            assertEquals("/tools/dose", request.url.encodedPath)
            val body = request.body.toByteReadPacket().readText()
            // Verify request body contains expected fields
            assert(body.contains("flora_micro"))
            assert(body.contains("5.0"))
            
            respond(
                content = ByteReadChannel("""{"status": "success", "message": "Dosed 5.0ml", "nutrient": "flora_micro", "amount_dispensed_ml": 5.0, "final_tds": 450.0}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HydroponicApiClient("http://localhost", engine = mockEngine)
        
        val response = client.dose(DoseRequest("flora_micro", 5.0))
        assertEquals(450.0, response["final_tds"]?.jsonPrimitive?.double)
    }
}
