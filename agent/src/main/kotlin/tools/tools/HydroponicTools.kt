package org.besomontro.tools.tools

import ai.koog.agents.core.tools.Tool
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import org.besomontro.client.HydroponicApiClient
import org.besomontro.client.PumpCommand
import org.besomontro.db.DatabaseLogger

@Serializable
data class AcRelayArgs(val state: String)

@Serializable
data class RecordAudioArgs(val duration: Int)

class ControlPumpTool(private val api: HydroponicApiClient) : Tool<PumpCommand, JsonObject>(
    PumpCommand.serializer(),
    JsonObject.serializer(),
    "control_pump",
    "Manually dispense from a specific pump. pump_id can be 'water_out', 'water_in', 'flora_micro', 'flora_gro', 'flora_bloom'. Duration is in seconds."
) {
    override suspend fun execute(args: PumpCommand): JsonObject {
        return api.controlPump(args)
    }
}

class ControlAcRelayTool(private val api: HydroponicApiClient) : Tool<AcRelayArgs, JsonObject>(
    AcRelayArgs.serializer(),
    JsonObject.serializer(),
    "control_ac_relay",
    "Turns the main grow light ON or OFF. state should be 'on' or 'off'."
) {
    override suspend fun execute(args: AcRelayArgs): JsonObject {
        return api.controlAcRelay(args.state)
    }
}

class GetHardwareStatusTool(private val api: HydroponicApiClient) : Tool<Unit, JsonObject>(
    Unit.serializer(),
    JsonObject.serializer(),
    "get_hardware_status",
    "Check water levels and pump status. Returns hardware telemetry."
) {
    override suspend fun execute(args: Unit): JsonObject {
        val result = api.getHardwareStatus()
        DatabaseLogger.log("SENSOR_STATUS", result.toString())
        return result
    }
}

class FillToMaxTool(private val api: HydroponicApiClient) : Tool<Unit, JsonObject>(
    Unit.serializer(),
    JsonObject.serializer(),
    "fill_to_max",
    "Fills the tank to the maximum level using the water_in pump."
) {
    override suspend fun execute(args: Unit): JsonObject {
        return api.fillToMax()
    }
}

class EmptyTankTool(private val api: HydroponicApiClient) : Tool<Unit, JsonObject>(
    Unit.serializer(),
    JsonObject.serializer(),
    "empty_tank",
    "Empties the tank completely using the water_out pump."
) {
    override suspend fun execute(args: Unit): JsonObject {
        return api.emptyTank()
    }
}

class SystemFlushTool(private val api: HydroponicApiClient) : Tool<Unit, JsonObject>(
    Unit.serializer(),
    JsonObject.serializer(),
    "system_flush",
    "Performs a full system flush: primes pumps, refills, empties, and refills again."
) {
    override suspend fun execute(args: Unit): JsonObject {
        return api.systemFlush()
    }
}

class CapturePhotoTool(private val api: HydroponicApiClient) : Tool<Unit, JsonObject>(
    Unit.serializer(),
    JsonObject.serializer(),
    "capture_photo",
    "Captures a raw photo from the camera."
) {
    override suspend fun execute(args: Unit): JsonObject {
        val result = api.capturePhoto()
        DatabaseLogger.log("CAPTURE_PHOTO", result.toString())
        return result
    }
}

class CapturePlantPhotoTool(private val api: HydroponicApiClient) : Tool<Unit, JsonObject>(
    Unit.serializer(),
    JsonObject.serializer(),
    "capture_plant_photo",
    "Captures a photo of the plant with the grow light ON. Optimized for plant inspection."
) {
    override suspend fun execute(args: Unit): JsonObject {
        val result = api.capturePlantPhoto()
        DatabaseLogger.log("CAPTURE_PLANT_PHOTO", result.toString())
        return result
    }
}

class GetPhTool(private val api: HydroponicApiClient) : Tool<Unit, JsonObject>(
    Unit.serializer(),
    JsonObject.serializer(),
    "get_ph",
    "Reads the current pH level from the sensor."
) {
    override suspend fun execute(args: Unit): JsonObject {
        val result = api.getPh()
        DatabaseLogger.log("SENSOR_PH", result.toString())
        return result
    }
}

class RecordAudioTool(private val api: HydroponicApiClient) : Tool<RecordAudioArgs, JsonObject>(
    RecordAudioArgs.serializer(),
    JsonObject.serializer(),
    "record_audio",
    "Records audio for a specified duration (default 5 seconds)."
) {
    override suspend fun execute(args: RecordAudioArgs): JsonObject {
        return api.recordAudio(args.duration)
    }
}

class GetTelemetryTool(private val api: HydroponicApiClient) : Tool<Unit, JsonObject>(
    Unit.serializer(),
    JsonObject.serializer(),
    "get_telemetry",
    "Returns a combined status report including Hardware Status and pH level. Use this instead of calling get_hardware_status and get_ph separately."
) {
    override suspend fun execute(args: Unit): JsonObject {
        val hardware = api.getHardwareStatus()
        val ph = api.getPh()
        
        val telemetry = JsonObject(mapOf(
            "hardware" to hardware,
            "ph" to ph
        ))
        
        DatabaseLogger.log("TELEMETRY_CHECK", telemetry.toString())
        return telemetry
    }
}
