// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import io.agents.pokeclaw.AppCapabilityCoordinator
import io.agents.pokeclaw.AppRequirement
import io.agents.pokeclaw.R
import io.agents.pokeclaw.base.BaseActivity
import io.agents.pokeclaw.support.DebugReportManager
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import io.agents.pokeclaw.widget.AlertDialog
import io.agents.pokeclaw.widget.CommonToolbar
import io.agents.pokeclaw.widget.InputDialog
import io.agents.pokeclaw.widget.MenuGroup
import io.agents.pokeclaw.widget.MenuItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings screen.
 *
 * This keeps the existing grouped settings layout but makes Tools -> Manage Tools real:
 * the row now opens ToolSettingsDialog, and ToolRegistry enforces the saved state.
 */
class SettingsActivity : BaseActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val permPoller = object : Runnable {
        override fun run() {
            refreshPermissions()
            handler.postDelayed(this, 1000)
        }
    }

    private var permAccessibility: MenuItem? = null
    private var permNotification: MenuItem? = null
    private var permNotifAccess: MenuItem? = null
    private var permOverlay: MenuItem? = null
    private var permBattery: MenuItem? = null
    private var permStorage: MenuItem? = null
    private var externalAutomationItem: MenuItem? = null
    private var globalPromptItem: MenuItem? = null
    private var customModelUrlItem: MenuItem? = null
    private var manageToolsItem: MenuItem? = null
    private var telegramItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val themeColors = io.agents.pokeclaw.ui.chat.ThemeManager.getColors()
        window.statusBarColor = themeColors.toolbarBg
        window.decorView.setBackgroundColor(themeColors.bg)

        setContentView(R.layout.activity_settings)

        val contentFrame = findViewById<android.view.ViewGroup>(android.R.id.content)
        contentFrame?.setBackgroundColor(themeColors.bg)
        (contentFrame?.getChildAt(0) as? android.view.View)?.setBackgroundColor(themeColors.bg)

        initToolbar()
        initMenuGroups()
        applyThemeToGroups(themeColors)
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        refreshExternalAutomation()
        refreshGlobalPromptStatus()
        refreshCustomModelUrlStatus()
        refreshManageToolsStatus()
        refreshTelegramStatus()
        handler.removeCallbacks(permPoller)
        handler.postDelayed(permPoller, 1000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(permPoller)
    }

    private fun initToolbar() {
        findViewById<CommonToolbar>(R.id.toolbar).apply {
            setTitle(getString(R.string.settings_title))
            showBackButton(true) { finish() }
        }
    }

    private fun initMenuGroups() {
        initPermissionsGroup()
        initChannelsGroup()
        initModelGroup()
        initAppearanceGroup()
        initToolsGroup()
        initRemoteGroup()
        initAboutGroup()
    }

    private fun initPermissionsGroup() {
        val group = findViewById<MenuGroup>(R.id.permissionsGroup)
        group.setTitle("Permissions")

        permAccessibility = group.addMenuItem(
            leadingIcon = R.drawable.ic_accessibility,
            title = getString(R.string.home_card_accessibility_title),
            onClick = {
                AppCapabilityCoordinator.openSystemSettings(this, AppRequirement.ACCESSIBILITY)
                Toast.makeText(this, R.string.home_enable_accessibility, Toast.LENGTH_LONG).show()
            },
            showDivider = true
        )

        permNotification = group.addMenuItem(
            leadingIcon = R.drawable.ic_notification,
            title = getString(R.string.home_card_notification_title),
            onClick = {
                if (!AppCapabilityCoordinator.isNotificationPermissionGranted(this)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
                    }
                } else {
                    Toast.makeText(this, R.string.home_notification_enabled, Toast.LENGTH_SHORT).show()
                }
            },
            showDivider = true
        )

        permNotifAccess = group.addMenuItem(
            leadingIcon = R.drawable.ic_notification,
            title = "Notification Access",
            onClick = { AppCapabilityCoordinator.openSystemSettings(this, AppRequirement.NOTIFICATION_ACCESS) },
            showDivider = true
        )

        permOverlay = group.addMenuItem(
            leadingIcon = R.drawable.ic_window,
            title = getString(R.string.home_card_system_window_title),
            onClick = {
                if (AppCapabilityCoordinator.snapshot(this).overlayGranted) {
                    Toast.makeText(this, R.string.home_overlay_enabled, Toast.LENGTH_SHORT).show()
                } else {
                    AppCapabilityCoordinator.openSystemSettings(this, AppRequirement.OVERLAY)
                }
            },
            showDivider = true
        )

        permBattery = group.addMenuItem(
            leadingIcon = R.drawable.ic_battery,
            title = getString(R.string.home_card_battery_title),
            onClick = {
                if (AppCapabilityCoordinator.snapshot(this).batteryOptimizationIgnored) {
                    Toast.makeText(this, R.string.home_battery_ignored, Toast.LENGTH_SHORT).show()
                } else {
                    AppCapabilityCoordinator.openSystemSettings(this, AppRequirement.BATTERY_OPTIMIZATION)
                }
            },
            showDivider = true
        )

        permStorage = group.addMenuItem(
            leadingIcon = R.drawable.ic_storage,
            title = getString(R.string.home_card_storage_title),
            onClick = {
                if (AppCapabilityCoordinator.snapshot(this).storageAccessGranted) {
                    Toast.makeText(this, R.string.home_storage_enabled, Toast.LENGTH_SHORT).show()
                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    requestPermissions(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        ),
                        101,
                    )
                } else {
                    AppCapabilityCoordinator.openSystemSettings(this, AppRequirement.STORAGE)
                }
            },
            showDivider = false
        )
    }

    private fun initChannelsGroup() {
        val group = findViewById<MenuGroup>(R.id.channelGroup)
        group.setTitle(getString(R.string.settings_group_channel))

        group.addMenuItem(
            leadingIcon = R.drawable.ic_channel_discord,
            title = getString(R.string.menu_discord),
            onClick = { ChannelConfigActivity.start(this, ChannelConfigActivity.ChannelType.DISCORD) },
            showDivider = true
        ).apply {
            setTrailingText(if (KVUtils.getDiscordBotToken().isNotEmpty()) "Connected" else "Not connected")
        }

        group.addMenuItem(
            leadingIcon = R.drawable.ic_channel_telegram,
            title = getString(R.string.menu_telegram),
            onClick = { ChannelConfigActivity.start(this, ChannelConfigActivity.ChannelType.TELEGRAM) },
            showDivider = true
        ).apply {
            setTrailingText(if (KVUtils.getTelegramBotToken().isNotEmpty()) "Connected" else "Not connected")
        }

        group.addMenuItem(
            leadingIcon = R.drawable.ic_channel_wechat,
            title = getString(R.string.menu_wechat),
            onClick = { Toast.makeText(this, "WeChat setup is not wired in this fork yet", Toast.LENGTH_SHORT).show() },
            showDivider = true
        ).apply {
            setTrailingText(if (KVUtils.getWechatBotToken().isNotEmpty()) "Connected" else "Not connected")
        }

        group.addMenuItem(
            leadingIcon = R.drawable.ic_lan_config,
            title = getString(R.string.menu_lan_config),
            onClick = { Toast.makeText(this, "LAN Config is disabled in this fork build", Toast.LENGTH_SHORT).show() },
            showDivider = false
        ).apply {
            setTrailingText(getString(R.string.lan_config_stopped))
        }
    }

    private fun initModelGroup() {
        val group = findViewById<MenuGroup>(R.id.modelGroup)
        group.setTitle(getString(R.string.settings_group_model))

        group.addMenuItem(
            leadingIcon = R.drawable.icon_current_model,
            title = getString(R.string.menu_llm_config),
            onClick = { startActivity(Intent(this, LlmConfigActivity::class.java)) },
            showDivider = true
        ).apply {
            val cloud = if (KVUtils.hasDefaultCloudModel()) "Cloud set" else "Cloud not set"
            val local = if (KVUtils.hasDefaultLocalModel()) "Local set" else "Local not set"
            setTrailingText("$cloud / $local")
        }

        group.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_recent_history,
            title = "Task Budget",
            onClick = { showBudgetDialog() },
            showDivider = true
        ).apply {
            setTrailingText(io.agents.pokeclaw.agent.TaskBudget.describeCurrentBudget())
        }

        globalPromptItem = group.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_edit,
            title = getString(R.string.global_prompt_title),
            onClick = { showGlobalPromptDialog() },
            showDivider = true
        )

        customModelUrlItem = group.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_share,
            title = getString(R.string.custom_local_model_url_title),
            onClick = { showCustomModelUrlDialog() },
            showDivider = false
        )
    }

    private fun initAppearanceGroup() {
        val group = findViewById<MenuGroup>(R.id.appearanceGroup)
        group.setTitle("Appearance")
        group.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_slideshow,
            title = "Theme",
            onClick = { startActivity(Intent(this, ThemeActivity::class.java)) },
            showDivider = false
        ).apply {
            val themeId = KVUtils.getString("THEME_ID", "abyss_dark")
            setTrailingText(themeId.replace("_", " ").replaceFirstChar { it.uppercase() })
        }
    }

    private fun initToolsGroup() {
        val group = findViewById<MenuGroup>(R.id.toolsGroup)
        group.setTitle("Tools")

        manageToolsItem = group.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_manage,
            title = "Manage Tools",
            onClick = {
                ToolSettingsDialog.show(this) {
                    refreshManageToolsStatus()
                }
            },
            showDivider = false
        )
        refreshManageToolsStatus()
    }

    private fun initRemoteGroup() {
        val group = findViewById<MenuGroup>(R.id.remoteGroup)
        group.setTitle("Remote Control")

        telegramItem = group.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_send,
            title = "Telegram Bot",
            onClick = { ChannelConfigActivity.start(this, ChannelConfigActivity.ChannelType.TELEGRAM) },
            showDivider = true
        )

        externalAutomationItem = group.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_share,
            title = "External Automation",
            onClick = { toggleExternalAutomation() },
            showDivider = true
        )

        group.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_call,
            title = "WhatsApp",
            onClick = { Toast.makeText(this, "WhatsApp remote setup coming later", Toast.LENGTH_SHORT).show() },
            showDivider = true
        ).apply {
            setTrailingText("Coming soon")
        }

        group.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_myplaces,
            title = "Web Dashboard",
            onClick = { Toast.makeText(this, "Web dashboard coming later", Toast.LENGTH_SHORT).show() },
            showDivider = false
        ).apply {
            setTrailingText("Coming soon")
        }
    }

    private fun initAboutGroup() {
        val group = findViewById<MenuGroup>(R.id.aboutGroup)
        group.setTitle("About")

        group.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_info_details,
            title = "PhoneAgent Lab",
            onClick = { },
            showDivider = true
        ).apply {
            setTrailingText("v${io.agents.pokeclaw.BuildConfig.VERSION_NAME}")
        }

        group.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_send,
            title = "Report a Bug",
            onClick = { reportBug() },
            showDivider = true
        ).apply {
            setTrailingText("GitHub + ZIP")
        }

        group.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_upload,
            title = "Share Debug Report",
            onClick = { shareDebugReport() },
            showDivider = true
        ).apply {
            setTrailingText("ZIP logs + state")
        }

        group.addMenuItem(
            leadingIcon = android.R.drawable.ic_menu_share,
            title = "GitHub",
            onClick = { startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/specialistonlyfans-hue/pokeclawte".toUri())) },
            showDivider = false
        ).apply {
            setTrailingText("fork repo")
        }
    }

    private fun refreshPermissions() {
        val capabilities = AppCapabilityCoordinator.snapshot(this)
        permAccessibility?.setTrailingText(capabilities.accessibilityStatusLabel)
        permNotification?.setTrailingText(capabilities.notificationPermissionStatusLabel)
        permNotifAccess?.setTrailingText(capabilities.notificationAccessStatusLabel)
        permOverlay?.setTrailingText(if (capabilities.overlayGranted) "Enabled" else "Disabled")
        permBattery?.setTrailingText(if (capabilities.batteryOptimizationIgnored) "Ignored" else "Restricted")
        permStorage?.setTrailingText(if (capabilities.storageAccessGranted) "Enabled" else "Disabled")
    }

    private fun refreshExternalAutomation() {
        externalAutomationItem?.setTrailingText(if (KVUtils.isExternalAutomationEnabled()) "Enabled" else "Disabled")
    }

    private fun refreshGlobalPromptStatus() {
        globalPromptItem?.setTrailingText(if (KVUtils.hasGlobalPrompt()) "Custom" else "Default")
    }

    private fun refreshCustomModelUrlStatus() {
        customModelUrlItem?.setTrailingText(if (KVUtils.getString("CUSTOM_LOCAL_MODEL_URL", "").isNotBlank()) "Custom" else "Default")
    }

    private fun refreshManageToolsStatus() {
        manageToolsItem?.setTrailingText(ToolSettingsDialog.summary())
    }

    private fun refreshTelegramStatus() {
        telegramItem?.setTrailingText(if (KVUtils.getTelegramBotToken().isNotEmpty()) "Connected" else "Not connected")
    }

    private fun toggleExternalAutomation() {
        val next = !KVUtils.isExternalAutomationEnabled()
        KVUtils.setExternalAutomationEnabled(next)
        refreshExternalAutomation()
        Toast.makeText(this, if (next) "External automation enabled" else "External automation disabled", Toast.LENGTH_SHORT).show()
    }

    private fun showBudgetDialog() {
        AlertDialog.show(
            context = this,
            title = "Task Budget",
            message = "Current budget: ${io.agents.pokeclaw.agent.TaskBudget.describeCurrentBudget()}",
            actionTitle = "OK",
            cancelTitle = null,
            onAction = { }
        )
    }

    private fun showGlobalPromptDialog() {
        InputDialog.show(
            context = this,
            title = getString(R.string.global_prompt_title),
            hint = getString(R.string.global_prompt_hint),
            initialValue = KVUtils.getGlobalPrompt(),
            positiveText = getString(R.string.common_save),
            negativeText = getString(R.string.common_cancel),
            onConfirm = { value ->
                KVUtils.setGlobalPrompt(value.trim())
                refreshGlobalPromptStatus()
                Toast.makeText(this, getString(R.string.common_saved), Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showCustomModelUrlDialog() {
        InputDialog.show(
            context = this,
            title = getString(R.string.custom_local_model_url_title),
            hint = getString(R.string.custom_local_model_url_hint),
            initialValue = KVUtils.getString("CUSTOM_LOCAL_MODEL_URL", ""),
            positiveText = getString(R.string.common_save),
            negativeText = getString(R.string.common_cancel),
            validator = { value ->
                if (value.isBlank() || value.startsWith("http://") || value.startsWith("https://")) {
                    InputDialog.ValidateResult(true, null)
                } else {
                    InputDialog.ValidateResult(false, "Use http:// or https://")
                }
            },
            onConfirm = { value ->
                KVUtils.putString("CUSTOM_LOCAL_MODEL_URL", value.trim())
                refreshCustomModelUrlStatus()
                Toast.makeText(this, getString(R.string.common_saved), Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun shareDebugReport() {
        lifecycleScope.launch {
            try {
                val report = withContext(Dispatchers.IO) { DebugReportManager.createReport(this@SettingsActivity) }
                val uri = FileProvider.getUriForFile(
                    this@SettingsActivity,
                    "${packageName}.fileprovider",
                    report
                )
                startActivity(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                )
            } catch (e: Exception) {
                XLog.e("SettingsActivity", "Failed to share debug report", e)
                Toast.makeText(this@SettingsActivity, "Failed to share report: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun reportBug() {
        val url = "https://github.com/specialistonlyfans-hue/pokeclawte/issues/new"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, url, Toast.LENGTH_LONG).show()
        }
    }

    private fun applyThemeToGroups(colors: io.agents.pokeclaw.ui.chat.ThemeManager.ChatColors) {
        val groups = listOf(
            R.id.permissionsGroup,
            R.id.channelGroup,
            R.id.modelGroup,
            R.id.appearanceGroup,
            R.id.toolsGroup,
            R.id.remoteGroup,
            R.id.aboutGroup
        )
        groups.forEach { id ->
            findViewById<MenuGroup>(id)?.let { group ->
                for (i in 0 until group.childCount) {
                    group.getChildAt(i)?.setBackgroundColor(colors.toolbarBg)
                }
            }
        }
    }
}
