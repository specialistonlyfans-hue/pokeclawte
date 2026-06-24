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
import io.agents.pokeclaw.widget.ConfirmDialog
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
        permBattery?.setTrailingText(if (capabilities.batteryOptimizationIgnored) "Unrestricted" else "Restricted")
        permStorage?.setTrailingText(if (capabilities.storageAccessGranted) "Enabled" else "Disabled")
    }

    private fun refreshExternalAutomation() {
        externalAutomationItem?.setTrailingText(if (KVUtils.isExternalAutomationEnabled()) "Enabled" else "Disabled")
    }

    private fun refreshGlobalPromptStatus() {
        val current = KVUtils.getGlobalPrompt()
        globalPromptItem?.setTrailingText(
            if (current.isBlank()) getString(R.string.global_prompt_not_set)
            else getString(R.string.global_prompt_set_status, current.length)
        )
    }

    private fun refreshCustomModelUrlStatus() {
        customModelUrlItem?.setTrailingText(
            if (KVUtils.getCustomLocalModelUrl().isBlank()) getString(R.string.custom_local_model_url_not_set)
            else getString(R.string.custom_local_model_url_set)
        )
    }

    private fun refreshManageToolsStatus() {
        manageToolsItem?.setTrailingText(ToolSettingsDialog.summary())
    }

    private fun refreshTelegramStatus() {
        telegramItem?.setTrailingText(if (KVUtils.getTelegramBotToken().isNotEmpty()) "Connected" else "Not connected")
    }

    private fun toggleExternalAutomation() {
        if (KVUtils.isExternalAutomationEnabled()) {
            KVUtils.setExternalAutomationEnabled(false)
            refreshExternalAutomation()
            Toast.makeText(this, "External Automation disabled", Toast.LENGTH_SHORT).show()
            return
        }

        ConfirmDialog.showWarm(
            context = this,
            title = "Enable External Automation?",
            message = "This lets trusted apps like Tasker, MacroDroid, or ADB start PhoneAgent Lab tasks with explicit Android intents. Keep it off unless you control the automation that will call it.",
            actionTitle = "Enable",
            cancelTitle = getString(R.string.common_cancel),
            onAction = {
                KVUtils.setExternalAutomationEnabled(true)
                refreshExternalAutomation()
                Toast.makeText(this, "External Automation enabled", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showGlobalPromptDialog() {
        val current = KVUtils.getGlobalPrompt()
        InputDialog.show(
            context = this,
            title = getString(R.string.global_prompt_dialog_title),
            presetText = current,
            hint = getString(R.string.global_prompt_hint),
            maxLength = 2000,
        ) { text ->
            KVUtils.setGlobalPrompt(text)
            refreshGlobalPromptStatus()
            Toast.makeText(this, "Global instructions saved", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCustomModelUrlDialog() {
        val current = KVUtils.getCustomLocalModelUrl()
        InputDialog.show(
            context = this,
            title = getString(R.string.custom_local_model_url_dialog_title),
            presetText = current,
            hint = getString(R.string.custom_local_model_url_hint),
            maxLength = 1000,
            inputValidate = { text ->
                val lower = text.trim().lowercase()
                if (lower.isEmpty() || lower.startsWith("http://") || lower.startsWith("https://")) {
                    InputDialog.ValidateResult(true, null)
                } else {
                    InputDialog.ValidateResult(false, getString(R.string.custom_local_model_url_invalid))
                }
            },
        ) { text ->
            val trimmed = text.trim().let { raw ->
                when {
                    raw.startsWith("HTTPS://", ignoreCase = false) -> "https://" + raw.substring(8)
                    raw.startsWith("HTTP://", ignoreCase = false) -> "http://" + raw.substring(7)
                    else -> raw
                }
            }
            KVUtils.setCustomLocalModelUrl(trimmed)
            refreshCustomModelUrlStatus()
            Toast.makeText(this, "Custom model URL saved", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showBudgetDialog() {
        val currentTokens = io.agents.pokeclaw.agent.TaskBudget.getConfiguredMaxTokens()
        val currentCost = io.agents.pokeclaw.agent.TaskBudget.getConfiguredMaxCost()

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val tokenLabel = android.widget.TextView(this).apply {
            text = "Max tokens per task"
            setTextColor(getColor(R.color.colorTextPrimary))
        }
        layout.addView(tokenLabel)

        val tokenOptions = arrayOf("Unlimited", "10K", "50K", "100K", "200K", "250K", "500K")
        val tokenValues = arrayOf<Int?>(null, 10_000, 50_000, 100_000, 200_000, 250_000, 500_000)
        val selectedTokenIndex = when (currentTokens) {
            null -> 0
            else -> tokenValues.indexOfFirst { it == currentTokens }.takeIf { it >= 0 } ?: 0
        }

        val tokenSpinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, tokenOptions)
            setSelection(selectedTokenIndex)
        }
        layout.addView(tokenSpinner)

        val costLabel = android.widget.TextView(this).apply {
            text = "\nMax cost per task (USD)"
            setTextColor(getColor(R.color.colorTextPrimary))
        }
        layout.addView(costLabel)

        val costInput = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Blank = no cost cap"
            setText(currentCost?.let { String.format("%.2f", it) } ?: "")
            setTextColor(getColor(R.color.colorTextPrimary))
        }
        layout.addView(costInput)

        android.app.AlertDialog.Builder(this)
            .setTitle("Task Budget")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newTokens = tokenValues[tokenSpinner.selectedItemPosition]
                val newCost = costInput.text.toString().trim().toDoubleOrNull()

                when (newTokens) {
                    null -> io.agents.pokeclaw.agent.TaskBudget.clearMaxTokens()
                    else -> io.agents.pokeclaw.agent.TaskBudget.saveMaxTokens(newTokens)
                }
                when {
                    newCost == null || newCost <= 0.0 -> io.agents.pokeclaw.agent.TaskBudget.clearMaxCost()
                    else -> io.agents.pokeclaw.agent.TaskBudget.saveMaxCost(newCost)
                }

                Toast.makeText(this, "Budget: ${io.agents.pokeclaw.agent.TaskBudget.describeCurrentBudget()}", Toast.LENGTH_SHORT).show()
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun reportBug() {
        buildSupportBundle("Preparing bug report…") { report ->
            AlertDialog.show(
                context = this,
                title = "Bug report ready",
                message = "${report.name} is ready. Open GitHub Issue and attach the ZIP file.",
                actionTitle = "Open GitHub Issue",
                cancelTitle = "Share ZIP",
                onAction = { openGitHubIssue(report) },
                onCancel = {
                    shareReportFile(
                        report = report,
                        chooserTitle = "Share bug report ZIP",
                        subject = "PhoneAgent Lab bug report ${io.agents.pokeclaw.BuildConfig.VERSION_NAME}",
                        body = "Attach this ZIP to your GitHub issue."
                    )
                }
            )
        }
    }

    private fun shareDebugReport() {
        buildSupportBundle("Preparing debug report…") { report ->
            shareReportFile(
                report = report,
                chooserTitle = "Share debug report",
                subject = "PhoneAgent Lab debug report ${io.agents.pokeclaw.BuildConfig.VERSION_NAME}",
                body = "Attach this debug report when reporting an issue."
            )
        }
    }

    private fun buildSupportBundle(preparingToast: String, onReportReady: (java.io.File) -> Unit) {
        lifecycleScope.launch {
            Toast.makeText(this@SettingsActivity, preparingToast, Toast.LENGTH_SHORT).show()
            runCatching {
                withContext(Dispatchers.IO) {
                    DebugReportManager.buildReport(this@SettingsActivity)
                }
            }.onSuccess { report ->
                onReportReady(report)
            }.onFailure { error ->
                XLog.e("SettingsActivity", "Failed to build debug report", error)
                Toast.makeText(this@SettingsActivity, "Failed to build debug report", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openGitHubIssue(report: java.io.File) {
        val issueUri = "https://github.com/specialistonlyfans-hue/pokeclawte/issues/new".toUri()
            .buildUpon()
            .appendQueryParameter("title", "[Bug] ${Build.MANUFACTURER} ${Build.MODEL} - ")
            .appendQueryParameter("body", buildGitHubIssueBody(report))
            .build()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, issueUri))
            Toast.makeText(this, "Attach ${report.name} to the GitHub issue after the page opens", Toast.LENGTH_LONG).show()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No app available to open GitHub", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildGitHubIssueBody(report: java.io.File): String {
        return """
            ## What happened
            -

            ## What you expected
            -

            ## Exact steps to reproduce
            1.
            2.
            3.

            ## Device
            - Manufacturer: ${Build.MANUFACTURER}
            - Model: ${Build.MODEL}
            - Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})

            ## Attachments
            - Attach this ZIP from PhoneAgent Lab: `${report.name}`

            Generated by PhoneAgent Lab ${io.agents.pokeclaw.BuildConfig.VERSION_NAME}.
        """.trimIndent()
    }

    private fun shareReportFile(report: java.io.File, chooserTitle: String, subject: String, body: String) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", report)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, chooserTitle))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No app available to share the report", Toast.LENGTH_LONG).show()
        }
    }

    private fun applyThemeToGroups(tc: io.agents.pokeclaw.ui.chat.ThemeManager.ChatColors) {
        val groups = listOf(
            R.id.permissionsGroup,
            R.id.channelGroup,
            R.id.modelGroup,
            R.id.appearanceGroup,
            R.id.toolsGroup,
            R.id.remoteGroup,
            R.id.aboutGroup
        )
        for (id in groups) {
            val group = findViewById<MenuGroup>(id) ?: continue
            group.setTitleColor(tc.aiText)
            group.setCardBackgroundColor(tc.toolbarBg)
            for (i in 0 until group.getMenuItemCount()) {
                group.getMenuItemAt(i)?.apply {
                    setTitleColor(tc.aiText)
                    setTrailingTextColor(tc.sendColor)
                    setLeadingIconColor(tc.aiText)
                    setTrailingIconColor(tc.aiText)
                }
            }
        }
        findViewById<CommonToolbar>(R.id.toolbar)?.apply {
            setBackgroundColor(tc.toolbarBg)
            setTitleColor(tc.aiText)
            findViewById<android.widget.ImageView>(R.id.ivBack)?.setColorFilter(tc.aiText)
        }
    }
}
