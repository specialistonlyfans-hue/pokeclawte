// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.integrations.kali

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Small, safe client for the Kali Security Orchestrator.
 *
 * This is intentionally limited to the orchestrator's policy-checked actions.
 * There is no arbitrary shell, no exploit runner, no credential attack helper,
 * no phishing flow, and no Wi-Fi disruption helper here.
 */
class KaliOrchestratorClient(private val context: Context) {

    data class ParsedCommand(
        val kind: Kind,
        val action: String? = null,
        val workflow: String? = null,
        val target: String? = null,
        val topPorts: Int = 50,
        val url: String? = null,
        val token: String? = null,
    ) {
        enum class Kind { HELP, STATUS, CONFIG, CLEAR, RUN, WORKFLOW }
    }

    companion object {
        private const val PREFS = "kali_orchestrator"
        private const val KEY_URL = "url"
        private const val KEY_TOKEN = "token"
        private const val DEFAULT_URL = "http://127.0.0.1:8899"

        private val ALIASES = mapOf(
            "dns" to "dns_check",
            "web" to "web_check",
            "web_headers" to "web_check",
            "tls" to "tls_check",
            "inventory" to "service_inventory",
        )

        private val SAFE_ACTIONS = setOf(
            "ping",
            "dns_check",
            "web_check",
            "tls_check",
            "scan_host",
            "service_inventory",
        )

        fun looksLikeCommand(text: String): Boolean {
            val t = text.trim().lowercase(Locale.US)
            return t == "/kali" || t.startsWith("/kali ") || t == "kali" || t.startsWith("kali ")
        }

        fun helpText(): String = """
            Kali Orchestrator im PokeClaw Chat

            Setup:
            /kali config http://KALI_IP:8899 DEIN_TOKEN

            Status:
            /kali status

            Einzel-Aktionen:
            /kali ping 192.168.1.20
            /kali dns_check scanme.nmap.org
            /kali web_check http://192.168.1.20
            /kali tls_check example.com
            /kali scan_host 192.168.1.20 50
            /kali service_inventory 192.168.1.20 50

            Workflows:
            /kali workflow quick_host 192.168.1.20 50
            /kali workflow web_audit http://192.168.1.20 50

            Aliase:
            dns, web, web_headers, tls, inventory

            Hinweis: Der Kali-Orchestrator prüft Token + allowed_targets und blockt nicht erlaubte Aktionen.
        """.trimIndent()
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun parse(raw: String): ParsedCommand {
        val trimmed = raw.trim()
        val body = when {
            trimmed.startsWith("/kali", ignoreCase = true) -> trimmed.removePrefixIgnoreCase("/kali").trim()
            trimmed.startsWith("kali", ignoreCase = true) -> trimmed.removePrefixIgnoreCase("kali").trim()
            else -> trimmed
        }
        if (body.isBlank() || body.equals("help", ignoreCase = true)) return ParsedCommand(ParsedCommand.Kind.HELP)

        val parts = body.split(Regex("\\s+")).filter { it.isNotBlank() }
        val first = parts.getOrNull(0)?.lowercase(Locale.US) ?: return ParsedCommand(ParsedCommand.Kind.HELP)

        if (first == "status") return ParsedCommand(ParsedCommand.Kind.STATUS)
        if (first == "clear" || first == "reset") return ParsedCommand(ParsedCommand.Kind.CLEAR)
        if (first == "config") {
            val url = parts.getOrNull(1)
            val token = parts.getOrNull(2)
            return ParsedCommand(ParsedCommand.Kind.CONFIG, url = url, token = token)
        }
        if (first == "workflow" || first == "wf") {
            val workflow = parts.getOrNull(1)
            val target = parts.getOrNull(2)
            val topPorts = parts.getOrNull(3)?.toIntOrNull()?.coerceIn(1, 200) ?: 50
            return ParsedCommand(
                kind = ParsedCommand.Kind.WORKFLOW,
                workflow = workflow,
                target = target,
                topPorts = topPorts,
            )
        }

        val action = normalizeAction(first)
        val target = parts.getOrNull(1)
        val topPorts = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 200) ?: 50
        return ParsedCommand(
            kind = ParsedCommand.Kind.RUN,
            action = action,
            target = target,
            topPorts = topPorts,
        )
    }

    fun run(parsed: ParsedCommand): String {
        return when (parsed.kind) {
            ParsedCommand.Kind.HELP -> helpText()
            ParsedCommand.Kind.STATUS -> status()
            ParsedCommand.Kind.CLEAR -> clear()
            ParsedCommand.Kind.CONFIG -> configure(parsed.url, parsed.token)
            ParsedCommand.Kind.RUN -> runAction(parsed.action, parsed.target, parsed.topPorts)
            ParsedCommand.Kind.WORKFLOW -> runWorkflow(parsed.workflow, parsed.target, parsed.topPorts)
        }
    }

    private fun configure(url: String?, token: String?): String {
        if (url.isNullOrBlank() || token.isNullOrBlank()) {
            return "Usage: /kali config http://KALI_IP:8899 DEIN_TOKEN"
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "Config rejected: URL must start with http:// or https://"
        }
        prefs.edit()
            .putString(KEY_URL, url.trimEnd('/'))
            .putString(KEY_TOKEN, token)
            .apply()
        return "Kali-Orchestrator config gespeichert. Teste jetzt: /kali status"
    }

    private fun clear(): String {
        prefs.edit().clear().apply()
        return "Kali-Orchestrator config gelöscht."
    }

    private fun status(): String {
        val token = token()
        if (token.isBlank()) {
            return "Kali-Orchestrator nicht konfiguriert. Nutze: /kali config http://KALI_IP:8899 DEIN_TOKEN"
        }
        val health = getJson("/health", includeToken = false)
        val actions = getJson("/actions", includeToken = true)
        val workflows = getJson("/workflows", includeToken = true)
        return buildString {
            appendLine("Kali-Orchestrator Status")
            appendLine("URL: ${baseUrl()}")
            appendLine()
            appendLine("Health:")
            appendLine(shortJson(health))
            appendLine()
            appendLine("Actions:")
            appendLine(shortJson(actions))
            appendLine()
            appendLine("Workflows:")
            appendLine(shortJson(workflows))
        }.trim()
    }

    private fun runAction(action: String?, target: String?, topPorts: Int): String {
        val a = action?.lowercase(Locale.US)?.trim().orEmpty()
        if (a !in SAFE_ACTIONS) {
            return "Action nicht erlaubt oder unbekannt: $action\n\n${helpText()}"
        }
        if (target.isNullOrBlank()) {
            return "Target fehlt. Beispiel: /kali $a 192.168.1.20"
        }
        val token = token()
        if (token.isBlank()) {
            return "Kali-Orchestrator nicht konfiguriert. Nutze: /kali config http://KALI_IP:8899 DEIN_TOKEN"
        }

        val payload = JSONObject()
            .put("action", a)
            .put("target", target)
            .put("args", JSONObject().put("top_ports", topPorts))

        val response = postJson("/run", payload)
        return formatRunResponse(response)
    }

    private fun runWorkflow(workflow: String?, target: String?, topPorts: Int): String {
        if (workflow.isNullOrBlank()) {
            return "Workflow fehlt. Beispiel: /kali workflow quick_host 192.168.1.20 50"
        }
        if (target.isNullOrBlank()) {
            return "Target fehlt. Beispiel: /kali workflow $workflow 192.168.1.20 50"
        }
        val token = token()
        if (token.isBlank()) {
            return "Kali-Orchestrator nicht konfiguriert. Nutze: /kali config http://KALI_IP:8899 DEIN_TOKEN"
        }
        val payload = JSONObject()
            .put("workflow", workflow)
            .put("target", target)
            .put("args", JSONObject().put("top_ports", topPorts))
        val response = postJson("/workflow", payload)
        return formatWorkflowResponse(response)
    }

    private fun normalizeAction(action: String): String {
        return ALIASES[action.lowercase(Locale.US)] ?: action.lowercase(Locale.US)
    }

    private fun baseUrl(): String = prefs.getString(KEY_URL, DEFAULT_URL)?.trimEnd('/') ?: DEFAULT_URL

    private fun token(): String = prefs.getString(KEY_TOKEN, "") ?: ""

    private fun getJson(path: String, includeToken: Boolean): JSONObject {
        val url = URL(baseUrl() + path)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            if (includeToken) setRequestProperty("X-Orchestrator-Token", token())
        }
        return readJson(conn)
    }

    private fun postJson(path: String, payload: JSONObject): JSONObject {
        val url = URL(baseUrl() + path)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Orchestrator-Token", token())
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
        return readJson(conn)
    }

    private fun readJson(conn: HttpURLConnection): JSONObject {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText).orEmpty()
        return try {
            JSONObject(body).put("http_status", conn.responseCode)
        } catch (_: Exception) {
            JSONObject().put("ok", false).put("http_status", conn.responseCode).put("raw", body.take(2000))
        } finally {
            conn.disconnect()
        }
    }

    private fun formatRunResponse(data: JSONObject): String {
        if (!data.optBoolean("ok", false)) {
            return "Kali failed: ${data.optString("error", data.toString()).take(2500)}"
        }
        val action = data.optString("action", "n/a")
        val target = data.optString("target", "n/a")
        val report = data.optJSONObject("report")
        val result = data.optJSONObject("result") ?: JSONObject()
        val reportId = report?.optString("report_id", "n/a") ?: "n/a"

        val summary = when {
            result.has("inventory") -> "Inventory completed. Report: $reportId"
            result.has("stdout") -> result.optString("stdout").ifBlank { "No output." }.take(2600)
            result.has("headers") -> "HTTP ${result.optInt("status_code")} server=${result.optString("server")} content_type=${result.optString("content_type")}"
            result.has("resolved_ips") -> "Resolved: ${result.optJSONArray("resolved_ips")?.join(", ") ?: "n/a"}"
            result.has("not_after") -> "TLS valid until ${result.optString("not_after")} cipher=${result.optString("cipher")}"
            else -> result.toString(2).take(2600)
        }

        return buildString {
            appendLine("Kali result")
            appendLine("Action: $action")
            appendLine("Target: $target")
            appendLine("Report: $reportId")
            appendLine()
            appendLine(summary)
        }.trim()
    }

    private fun formatWorkflowResponse(data: JSONObject): String {
        if (!data.optBoolean("ok", false)) {
            return "Kali workflow failed: ${data.optString("error", data.toString()).take(2500)}"
        }
        val workflow = data.optString("workflow", "n/a")
        val target = data.optString("target", "n/a")
        val report = data.optJSONObject("report")
        val result = data.optJSONObject("result") ?: JSONObject()
        val reportId = report?.optString("report_id", "n/a") ?: "n/a"
        val stepsOk = result.optInt("steps_ok", 0)
        val stepsTotal = result.optInt("steps_total", 0)
        val steps = result.optJSONArray("steps")
        val lines = mutableListOf<String>()
        lines.add("Kali workflow result")
        lines.add("Workflow: $workflow")
        lines.add("Target: $target")
        lines.add("Steps: $stepsOk/$stepsTotal OK")
        lines.add("Report: $reportId")
        lines.add("")
        if (steps != null) {
            for (i in 0 until minOf(steps.length(), 8)) {
                val step = steps.optJSONObject(i) ?: continue
                lines.add("- ${step.optString("id")}: ${step.optString("action")} ok=${step.optBoolean("ok")}")
            }
        }
        return lines.joinToString("\n")
    }

    private fun shortJson(obj: JSONObject): String = obj.toString(2).take(1800)
}

private fun String.removePrefixIgnoreCase(prefix: String): String {
    return if (this.regionMatches(0, prefix, 0, prefix.length, ignoreCase = true)) {
        this.substring(prefix.length)
    } else {
        this
    }
}
