package org.besomontro.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

@Serializable
data class FeedRequest(
    val recipe: String,
    val amounts_ml: Map<String, Double> = emptyMap()
)

@Serializable
data class DoseRequest(
    val nutrient: String,
    val amount_ml: Double,
    val mix_seconds: Int = 30
)

@Serializable
data class PumpCommand(
    val pump_id: String,
    val duration: Double
)

@Serializable
enum class JobType {
    fill_to_max,
    empty_tank,
    system_flush,
    feed,
    diagnose
}

@Serializable
enum class JobState {
    queued,
    running,
    completed,
    failed
}

@Serializable
data class JobRequest(
    val type: JobType,
    val params: JsonObject? = null
)

@Serializable
data class JobStatus(
    val job_id: String,
    val type: JobType,
    val status: JobState,
    val error: String? = null,
    val result: JsonObject? = null
)

class HydroponicApiClient(
    private val baseUrl: String,
    private val delayMillis: Long = 0,
    engine: HttpClientEngine? = null
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val client = HttpClient(engine ?: CIO.create()) {
        install(ContentNegotiation) {
            json(json)
        }
        install(io.ktor.client.plugins.HttpTimeout) {
            requestTimeoutMillis = 600_000 // 10 minutes
        }
    }

    private suspend fun throttle() {
        if (delayMillis > 0) {
            println("Throttling request for ${delayMillis}ms...")
            delay(delayMillis)
        }
    }

    private suspend fun executeJob(type: JobType, params: JsonObject? = null): JsonObject {
        throttle()
        val request = JobRequest(type, params)
        val submitRes = client.post("$baseUrl/jobs/") {
            setBody(request)
            header("Content-Type", "application/json")
        }

        if (!submitRes.status.isSuccess()) {
            val errorBody = try {
                submitRes.body<String>()
            } catch (e: Exception) {
                submitRes.status.description
            }
            println("Job submission failed: ${submitRes.status} - $errorBody")
            // Return empty result or throw specific exception depending on requirements.
            // Returning a failed JobStatus-like structure indirectly via Exception or special handling?
            // The method expects JsonObject (result).
            // But the caller might be expecting the result of the job.
            
            // Wait, the callers (fillToMax, etc) return JsonObject which is jobStatus.result.
            // If we fail here, we can't really return a result.
            // But the Plan said: "return a JobStatus object with ... status: JobState.failed"
            // HOWEVER, executeJob returns JsonObject (the result), NOT JobStatus.
            // Let's look at the code again.
            
            // Original code:
            // var jobStatus = submitRes.body<JobStatus>()
            // ...
            // return jobStatus.result ?: JsonObject(emptyMap())
            
            // If I return a failed JobStatus, I need to refactor executeJob to possibly throw or handle it.
            // Wait, if I change executeJob to return JobStatus, I break all callers.
            // But the plan said "return a JobStatus object".
            
            // Let's re-read the plan.
            // "If not ... return a JobStatus object"
            // But executeJob returns JsonObject.
            
            // Ah, line 95: var jobStatus = submitRes.body<JobStatus>()
            // It uses jobStatus to loop.
            
            // So if I construct a synthetic JobStatus here, I can use it.
            
             val errorMsg = "Submission failed: ${submitRes.status} $errorBody"
             val syntheticStatus = JobStatus(
                 job_id = "submission_failed",
                 type = type,
                 status = JobState.failed,
                 error = errorMsg,
                 result = null
             )
             
             // Now allow the flow to proceed? 
             // If status is failed, it will hit line 104 check.
             
             // line 104: if (jobStatus.status == JobState.failed) { throw Exception(...) }
             
             // So it will throw an exception with the error message.
             // This fulfills "graceful handling" (clear error message instead of serialization crash).
             // And allows the user's logic to catch "Job failed" exception if they want.
             
             return handleJobStatus(syntheticStatus)
        }
        
        var jobStatus = submitRes.body<JobStatus>()
        return handleJobStatus(jobStatus)
    }

    private suspend fun handleJobStatus(initialStatus: JobStatus): JsonObject {
        var jobStatus = initialStatus
        println("Job ${jobStatus.job_id} (${jobStatus.type}) submitted. Status: ${jobStatus.status}")

        while (jobStatus.status == JobState.queued || jobStatus.status == JobState.running) {
            delay(2000) // Poll every 2 seconds
            jobStatus = client.get("$baseUrl/jobs/${jobStatus.job_id}").body()
            // println("Job ${jobStatus.job_id} status: ${jobStatus.status}")
        }

        if (jobStatus.status == JobState.failed) {
            throw Exception("Job ${jobStatus.job_id} failed: ${jobStatus.error}")
        }

        println("Job ${jobStatus.job_id} completed.")
        return jobStatus.result ?: JsonObject(emptyMap())
    }

    private fun resizeAndEncodeImage(bytes: ByteArray): String {
        try {
            val inputStream = java.io.ByteArrayInputStream(bytes)
            val originalImage = javax.imageio.ImageIO.read(inputStream)
            
            // Calculate new dimensions (max width 640)
            val targetWidth = 640
            val ratio = targetWidth.toDouble() / originalImage.width
            val targetHeight = (originalImage.height * ratio).toInt()

            val resizedImage = java.awt.image.BufferedImage(targetWidth, targetHeight, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val graphics = resizedImage.createGraphics()
            graphics.composite = java.awt.AlphaComposite.Src
            graphics.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null)
            graphics.dispose()

            val outputStream = java.io.ByteArrayOutputStream()
            javax.imageio.ImageIO.write(resizedImage, "jpg", outputStream)
            return Base64.getEncoder().encodeToString(outputStream.toByteArray())
        } catch (e: Exception) {
            println("Failed to compress image: ${e.message}")
            return Base64.getEncoder().encodeToString(bytes) // Fallback
        }
    }

    suspend fun controlPump(command: PumpCommand): JsonObject {
        throttle()
        return client.post("$baseUrl/control/pump") {
            setBody(command)
            header("Content-Type", "application/json")
        }.body()
    }

    suspend fun controlAcRelay(state: String): JsonObject {
        throttle()
        return client.post("$baseUrl/control/ac_relay") {
            parameter("state", state)
        }.body()
    }

    suspend fun getHardwareStatus(): JsonObject {
        throttle()
        return client.get("$baseUrl/hardware/status").body()
    }

    suspend fun fillToMax(): JsonObject {
        return executeJob(JobType.fill_to_max)
    }

    suspend fun emptyTank(): JsonObject {
        return executeJob(JobType.empty_tank)
    }

    suspend fun systemFlush(): JsonObject {
        return executeJob(JobType.system_flush)
    }

    suspend fun capturePhoto(): JsonObject {
        throttle()
        val bytes: ByteArray = client.get("$baseUrl/sensors/camera/capture").body()
        val base64 = resizeAndEncodeImage(bytes)
        return JsonObject(mapOf("base64_data" to JsonPrimitive(base64)))
    }

    suspend fun getPh(): JsonObject {
        throttle()
        return client.get("$baseUrl/sensors/ph").body()
    }

    suspend fun capturePlantPhoto(): JsonObject {
        throttle()
        val bytes: ByteArray = client.get("$baseUrl/sensors/camera/plant").body()
        val base64 = resizeAndEncodeImage(bytes)
        return JsonObject(mapOf("base64_data" to JsonPrimitive(base64)))
    }

    suspend fun recordAudio(duration: Int = 5): JsonObject {
        throttle()
        val bytes: ByteArray = client.get("$baseUrl/sensors/microphone/record") {
            parameter("duration", duration)
        }.body()
        val base64 = Base64.getEncoder().encodeToString(bytes)
        return JsonObject(mapOf("base64_data" to JsonPrimitive(base64)))
    }

    suspend fun smartFeed(request: FeedRequest): JsonObject {
        // Encode FeedRequest to JsonObject for params
        val params = json.encodeToJsonElement(request) as? JsonObject
        return executeJob(JobType.feed, params)
    }

    suspend fun trueFlush(soakDuration: Int = 180): JsonObject {
        // Composed Sequence
        // 1. Check initial state
        val statusState = getHardwareStatus()
        val acPower = statusState["ac_power"]?.jsonPrimitive?.content ?: "off"
        val wasOn = acPower.equals("on", ignoreCase = true)

        // 2. Empty Tank
        executeJob(JobType.empty_tank)

        // 3. Fill to Max
        executeJob(JobType.fill_to_max)

        // 4. Mix / Soak (Lights/Air Stones ON)
        controlAcRelay("on")
        println("Soaking for ${soakDuration}s...")
        delay(soakDuration * 1000L)

        // 5. Restore State (Lights OFF or ON)
        controlAcRelay(if (wasOn) "on" else "off")

        // 6. Empty Tank
        executeJob(JobType.empty_tank)

        // 7. Fill to Max (Final)
        return executeJob(JobType.fill_to_max)
    }

    suspend fun diagnose(): JsonObject {
        return executeJob(JobType.diagnose)
    }

    suspend fun dose(request: DoseRequest): JsonObject {
        throttle()
        return client.post("$baseUrl/tools/dose") {
            setBody(request)
            header("Content-Type", "application/json")
        }.body()
    }

    suspend fun fixOverflow(): JsonObject {
        throttle()
        return client.post("$baseUrl/control/fix_overflow").body()
    }
}
