// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.tool

import io.agents.pokeclaw.agent.knowledge.*
import io.agents.pokeclaw.tool.impl.*
import io.agents.pokeclaw.tool.impl.mobile.*
import io.agents.pokeclaw.tool.impl.tv.*
import io.agents.pokeclaw.utils.KVUtils

object ToolRegistry {

    enum class DeviceType { TV, MOBILE }

    private const val KEY_DISABLED_TOOLS = "KEY_DISABLED_TOOLS"
    private const val TOOL_SEPARATOR = "\n"

    /**
     * Core tools stay enabled so the agent can still observe state and terminate cleanly.
     * Users can manage action/risk tools, but disabling these would make the agent hang.
     */
    private val mandatoryTools = setOf("get_screen_info", "wait", "finish")

    private val tools = LinkedHashMap<String, BaseTool>()
    var deviceType: DeviceType = DeviceType.TV
        private set

    @JvmStatic
    fun getInstance(): ToolRegistry = this

    fun registerAllTools(type: DeviceType = DeviceType.TV) {
        deviceType = type
        tools.clear()
        registerCommonTools()
        when (type) {
            DeviceType.TV -> registerTvTools()
            DeviceType.MOBILE -> registerMobileTools()
        }
    }

    private fun registerCommonTools() {
        register(GetScreenInfoTool())
        register(FindNodeInfoTool())
        register(InputTextTool())
        register(SystemKeyTool())
        register(OpenAppTool())
        register(GetInstalledAppsTool())
        register(TakeScreenshotTool())
        register(WaitTool())
        register(RepeatActionsTool())
        register(ClipboardTool())
        register(SendFileTool())
        register(GetDeviceInfoTool())
        register(GetNotificationsTool())
        register(MakeCallTool())
        register(FinishTool())
        // Knowledge Base tools — shared vault available in all modes
        register(KbWriteTool())
        register(KbReadTool())
        register(KbSearchTool())
        register(KbAppendTool())
        register(KbAddTodoTool())
    }

    private fun registerTvTools() {
        register(DpadUpTool())
        register(DpadDownTool())
        register(DpadLeftTool())
        register(DpadRightTool())
        register(DpadCenterTool())
        register(VolumeUpTool())
        register(VolumeDownTool())
        register(PressMenuTool())
        register(PressPowerTool())
    }

    private fun registerMobileTools() {
        register(TapTool())
        register(TapNodeTool())
        register(LongPressTool())
        register(SwipeTool())
        register(ScrollToFindTool())
        register(FindAndTapTool())
        register(SendMessageTool())
        register(AutoReplyTool())
    }

    fun register(tool: BaseTool) {
        tools[tool.getName()] = tool
    }

    fun getTool(name: String): BaseTool? = tools[name]

    fun getDisplayName(name: String): String = tools[name]?.getDisplayName() ?: name

    /** All registered tools, including disabled ones. Used by Settings -> Manage Tools. */
    fun getRegisteredTools(): List<BaseTool> = tools.values.toList()

    /** Tools exposed to the LLM/tool bridge. Disabled tools are hidden here. */
    fun getAllTools(): List<BaseTool> = tools.values.filter { isToolEnabled(it.getName()) }

    fun getTotalToolCount(): Int = tools.size

    fun getEnabledToolCount(): Int = tools.keys.count { isToolEnabled(it) }

    fun isMandatoryTool(name: String): Boolean = name in mandatoryTools

    fun isToolEnabled(name: String): Boolean {
        if (name in mandatoryTools) return true
        return name !in getDisabledToolNames()
    }

    fun setToolEnabled(name: String, enabled: Boolean) {
        if (name in mandatoryTools) return
        val disabled = getDisabledToolNames().toMutableSet()
        if (enabled) {
            disabled.remove(name)
        } else {
            disabled.add(name)
        }
        setDisabledToolNames(disabled)
    }

    fun setDisabledToolNames(names: Set<String>) {
        val normalized = names
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it !in mandatoryTools }
            .distinct()
            .sorted()
            .joinToString(TOOL_SEPARATOR)
        KVUtils.putString(KEY_DISABLED_TOOLS, normalized)
        KVUtils.sync()
    }

    fun getDisabledToolNames(): Set<String> {
        return KVUtils.getString(KEY_DISABLED_TOOLS, "")
            .split(TOOL_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun resetToolSettings() {
        KVUtils.remove(KEY_DISABLED_TOOLS)
        KVUtils.sync()
    }

    fun executeTool(name: String, params: Map<String, Any>): ToolResult {
        val tool = tools[name] ?: return ToolResult.error("Unknown tool: $name")
        if (!isToolEnabled(name)) {
            return ToolResult.error("Tool disabled in Settings: $name")
        }
        return try {
            tool.executeWithWaitAfter(params)
        } catch (e: Exception) {
            io.agents.pokeclaw.utils.XLog.e("ToolRegistry", "Tool '$name' execution failed with params=$params", e)
            ToolResult.error("Tool execution failed: ${e.message}")
        }
    }
}
