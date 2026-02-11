package org.besomontro.tools

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.ExitTool
import ai.koog.agents.ext.tool.file.ListDirectoryTool
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.agents.ext.tool.file.WriteFileTool
import ai.koog.rag.base.files.JVMFileSystemProvider
import org.besomontro.client.HydroponicApiClient
import org.besomontro.llm.AgentMode
import org.besomontro.tools.tools.*

import org.besomontro.config.LocalProperties

object Tools {

    val BASE_URL = LocalProperties["hydroponic.api.url"] 
        ?: error("Missing 'hydroponic.api.url' in local.properties")

    fun getToolsForRFPSitter(scheduler: Scheduler, mode: AgentMode): ToolRegistry {
        return ToolRegistry {
            tool(CustomSayToUser())
            if (mode == AgentMode.MANUAL) {
                tool(CustomAskUser())
            }
            tool(ScheduleNextRunTool(scheduler))
            tool(GetCurrentTimeTool())
            tool(LogActionTool())
            tool(CalculateNutrientsTool())
            
            val delayMillis = if (mode == AgentMode.AUTO) 2000L else 0L
            val api = HydroponicApiClient(BASE_URL, delayMillis)
            tool(ControlPumpTool(api))
            tool(ControlAcRelayTool(api))
            tool(GetHardwareStatusTool(api))
            tool(FillToMaxTool(api))
            tool(EmptyTankTool(api))
            tool(SystemFlushTool(api))
            tool(CapturePhotoTool(api))
            tool(CapturePlantPhotoTool(api))
            tool(GetPhTool(api))
            tool(RecordAudioTool(api))
            tool(GetTelemetryTool(api))
            tool(ExitTool)
            tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
            tool(WriteFileTool(JVMFileSystemProvider.ReadWrite))
            tool(ListDirectoryTool(JVMFileSystemProvider.ReadOnly))
        }
    }
}