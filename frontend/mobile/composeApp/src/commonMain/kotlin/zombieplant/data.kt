
package zombieplant

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

@Serializable
data class HardwareStatusResponse(
    val pumps: Map<String, Int>,
    val ac_power: String,
    val water_level: WaterLevelStatus,
    val tds: TDSStatus,
    val ph: PHStatus,
    val environment: EnvironmentStatus
)

@Serializable
data class WaterLevelStatus(
    val full: Boolean,
    val empty: Boolean
)

@Serializable
data class TDSStatus(
    val ppm: Float,
    val voltage: Float
)

@Serializable
data class PHStatus(
    val ph: Float,
    val voltage: Float
)

@Serializable(with = EnvironmentStatusSerializer::class)
sealed class EnvironmentStatus

@Serializable
data class DHTSuccess(
    val temperature_f: Float,
    val humidity_percent: Float
) : EnvironmentStatus()

@Serializable
data class DHTError(
    val error: String
) : EnvironmentStatus()

object EnvironmentStatusSerializer : JsonContentPolymorphicSerializer<EnvironmentStatus>(EnvironmentStatus::class) {
    override fun selectDeserializer(element: JsonElement) = when {
        "temperature_f" in element.jsonObject -> DHTSuccess.serializer()
        "error" in element.jsonObject -> DHTError.serializer()
        else -> throw Exception("Unknown EnvironmentStatus type")
    }
}

@Serializable
data class ACRelayResponse(
    val status: String,
    val ac_power: String
)

@Serializable
data class PumpResponse(
    val status: String,
    val pump: String,
    val duration: Float
)
