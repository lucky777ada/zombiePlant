
package zombieplant

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class ZombiePlantAPI {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    // Replace with your actual API base URL
    private val baseUrl = "http://192.168.1.160"

    suspend fun getHardwareStatus(): HardwareStatusResponse {
        return client.get("$baseUrl/hardware/status").body()
    }

    suspend fun controlAcRelay(state: String): ACRelayResponse {
        return client.post("$baseUrl/control/ac_relay?state=$state") {
            contentType(ContentType.Application.Json)
        }.body()
    }

    suspend fun controlPump(pumpId: String, duration: Float): PumpResponse {
        return client.post("$baseUrl/control/pump") {
            contentType(ContentType.Application.Json)
            setBody(PumpCommand(pump_id = pumpId, duration = duration))
        }.body()
    }

    suspend fun getPlantImage(): ByteArray {
        return client.get("$baseUrl/sensors/camera/plant").body()
    }

    suspend fun getLatestTimelapse(): ByteArray {
        return client.get("$baseUrl/timelapse/latest").body()
    }
}

@kotlinx.serialization.Serializable
data class PumpCommand(val pump_id: String, val duration: Float)
