// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.integrations.kali

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Small, safe client for the Kali Security Orchestrator.
 *
 * This client only calls policy-checked Orchestrator endpoints. It does not
 * expose arbitrary shell, exploit execution, credential attacks, phishing,
 * Wi-Fi disruption, payload generation, or stealth features.
 */
class KaliOrchestratorClient(private val context: Context) {

    data class ParsedCommand(
        val kind: Kind,
        val action: String? = null,
        val workflow: String? = null,
        val target: String? = null,
        val targetName: String? = null,
        val targetType: String? = null,
        val reportId: String? = null,
        val topPorts: Int = 50,
        val url: String? = null,
        val token: String? = null,
    ) {
        enum class Kind {
            HELP, STATUS, CONFIG, CLEAR, RUN, WORKFLOW,
            REPORTS, REPORT,
            TARGETS, ADD_TARGET,
            FINDINGS, EVIDENCE, JOBS
        }
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
            Kali Orchestrator

            Setup:
            /kali config http://KALI_IP:8899 DEIN_TOKEN

            Status:
            /kali status

            Targets:
            /kali targets
            /kali target add NAME TARGET TYPE

            Actions:
            /kali ping 192.168.1.20
            /kali dns_check scanme.nmap.org
            /kali web_check http://192.168.1.20
            /kali tls_check example.com
            /kali scan_host 192.168.1.20 50
            /kali service_inventory 192.168.1.20 50

            Workflows:
            /kali workflow quick_host 192.168.1.20 50
            /kali workflow web_audit http://192.168.1.20 50

            Reports:
            /kali reports
            /kali report REPORT_ID

            State:
            /kali findings
            /kali evidence
            /kali jobs
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
        if (body.isBlank() || body.equals("help", ignoreCase = true)) {
            return ParsedCommand(ParsedCommand.Kind.HELP)
        }

        val parts = body.split(Regex("\\s+")).filter { it.isNotBlank() }
        val first = parts.getOrNull(0)?.lowercase(Locale.US) ?: return ParsedCommand(ParsedCommand.Kind.HELP)

        if (first == "status") return ParsedCommand(ParsedCommand.Kind.STATUS)
        if (first == "clear" || first == "reset") return ParsedCommand(ParsedCommand.Kind.CLEAR)
        if (first == "targets") return ParsedCommand(ParsedCommand.Kind.TARGETS)
        if (first == "findings") return ParsedCommand(ParsedCommand.Kind.FINDINGS)
        if (first == "evidence") return ParsedCommand(ParsedCommand.Kind.EVIDENCE)
        if (first == "jobs" || first == "history") return ParsedCommand(ParsedCommand.Kind.JOBS)
        if (first == "reports" || first == "report-list") return ParsedCommand(ParsedCommand.Kind.REPORTS)
        if (first == "report") return ParsedCommand(ParsedCommand.Kind.REPORT, reportId = parts.getOrNull(1))

        if (first == "config") {
            return ParsedCommand(
                ParsedCommand.Kind.CONFIG,
                url = parts.getOrNull(1),
                token = parts.getOrNull(2),
            )
        }

        if (first == "target" && parts.getOrNull(1)?.lowercase(Locale.US) == "add") {
            return ParsedCommand(
                ParsedCommand.Kind.ADD_TARGET,
                targetName = parts.getOrNull(2),
                target = parts.getOrNull(3),
                targetType = parts.getOrNull(4) ?: "host",
            )
        }
        if (first == "add-target") {
            return ParsedCommand(
                ParsedCommand.Kind.ADD_TARGET,
                targetName = parts.getOrNull(1),
                target = parts.getOrNull(2),
                targetType = parts.getOrNull(3) ?: "host",
            )
        }

        if (first == "workflow" || first == "wf") {
            return ParsedCommand(
                kind = ParsedCommand.Kind.WORKFLOW,
                workflow = parts.getOrNull(1),
                target = parts.getOrNull(2),
                topPorts = parts.getOrNull(3)?.toIntOrNull()?.coerceIn(1, 200) ?: 50,
            )
        }

        return ParsedCommand(
            kind = ParsedCommand.Kind.RUN,
            action = normalizeAction(first),
            target = parts.getOrNull(1),
            topPorts = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 200) ?: 50,
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
            ParsedCommand.Kind.REPORTS -> listReports()
            ParsedCommand.Kind.REPORT -> getReport(parsed.reportId)
            ParsedCommand.Kind.TARGETS -> listTargets()
            ParsedCommand.Kind.ADD_TARGET -> addTarget(parsed.targetName, parsed.target, parsed.targetType)
            ParsedCommand.Kind.FINDINGS -> listFindings()
            ParsedCommand.Kind.EVIDENCE -> listEvidence()
            ParsedCommand.Kind.JOBS -> listJobs()
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
        requireConfigured()?.let { return it }
        val health = getJson("/health", includeToken = false)
        val actions = getJson("/actions", includeToken = true)
        val workflows = getJson("/workflows", includeToken = true)
        val targets = getJson("/targets", includeToken = true)
        val reports = getJson("/reports?limit=5", includeToken = true)
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
            appendLine()
            appendLine(formatTargetsResponse(targets, 8))
            appendLine()
            appendLine(formatReportsResponse(reports, 5))
        }.trim()
    }

    private fun runAction(action: String?, target: String?, topPorts: Int): String {
        val a = action?.lowercase(Locale.US)?.trim().orEmpty()
        if (a !in SAFE_ACTIONS) return "Action nicht erlaubt oder unbekannt: $action\n\n${helpText()}"
        if (target.isNullOrBlank()) return "Target fehlt. Beispiel: /kali $a 192.168.1.20"
        requireConfigured()?.let { return it }

        val payload = JSONObject()
            .put("action", a)
            .put("target", target)
            .put("args", JSONObject().put("top_ports", topPorts))

        return formatRunResponse(postJson("/run", payload))
    }

    private fun runWorkflow(workflow: String?, target: String?, topPorts: Int): String {
        if (workflow.isNullOrBlank()) return "Workflow fehlt. Beispiel: /kali workflow quick_host 192.168.1.20 50"
        if (target.isNullOrBlank()) return "Target fehlt. Beispiel: /kali workflow $workflow 192.168.1.20 50"
        requireConfigured()?.let { return it }

        val payload = JSONObject()
            .put("workflow", workflow)
            .put("target", target)
            .put("args", JSONObject().put("top_ports", topPorts))

        return formatWorkflowResponse(postJson("/workflow", payload))
    }

    private fun listTargets(): String {
        requireConfigured()?.let { return it }
        return formatTargetsResponse(getJson("/targets", includeToken = true), 30)
    }

    private fun addTarget(name: String?, target: String?, type: String?): String {
        if (name.isNullOrBlank() || target.isNullOrBlank()) return "Usage: /kali target add NAME TARGET TYPE"
        requireConfigured()?.let { return it }

        val payload = JSONObject()
            .put("name", name)
            .put("value", target)
            .put("type", type ?: "host")

        val response = postJson("/targets", payload)
        if (!response.optBoolean("ok", false)) return "Target save failed: ${response.optString("error", response.toString()).take(2000)}"

        val item = response.optJSONObject("target") ?: JSONObject()
        return buildString {
            appendLine("Target saved")
            appendLine("Name: ${item.optString("name")}")
            appendLine("Value: ${item.optString("value")}")
            appendLine("Allowed: ${item.optBoolean("allowed")}")
            appendLine("Reason: ${item.optString("allow_reason")}")
        }.trim()
    }

    private fun listReports(): String {
        requireConfigured()?.let { return it }
        return formatReportsResponse(getJson("/reports?limit=20", includeToken = true), 20)
    }

    private fun getReport(reportId: String?): String {
        val id = reportId?.trim().orEmpty()
        if (id.isBlank()) return "Report-ID fehlt. Beispiel: /kali report REPORT_ID"
        requireConfigured()?.let { return it }
        return formatReportDetail(getJson("/reports/$id.json", includeToken = true))
    }

    private fun listFindings(): String {
        requireConfigured()?.let { return it }
        return formatFindingsResponse(getJson("/findings?limit=50", includeToken = true))
    }

    private fun listEvidence(): String {
        requireConfigured()?.let { return it }
        return formatEvidenceResponse(getJson("/evidence?limit=50", includeToken = true))
    }

    private fun listJobs(): String {
        requireConfigured()?.let { return it }
        return formatJobsResponse(getJson("/jobs?limit=50", includeToken = true))
    }

    private fun requireConfigured(): String? {
        return if (token().isBlank()) {
            "Kali-Orchestrator nicht konfiguriert. Nutze: /kali config http://KALI_IP:8899 DEIN_TOKEN"
        } else null
    }

    private fun normalizeAction(action: String): String {
        return ALIASES[action.lowercase(Locale.US)] ?: action.lowercase(Locale.US)
    }

    private fun baseUrl(): String = prefs.getString(KEY_URL, DEFAULT_URL)?.trimEnd('/') ?: DEFAULT_URL

    private fun token(): String = prefs.getString(KEY_TOKEN, "") ?: ""

    private fun getJson(path: String, includeToken: Boolean): JSONObject {
        val conn = (URL(baseUrl() + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            if (includeToken) setRequestProperty("X-Orchestrator-Token", token())
        }
        return readJson(conn)
    }

    private fun postJson(path: String, payload: JSONObject): JSONObject {
        val conn = (URL(baseUrl() + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Orchestrator-Token", token())
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer -> writer.write(payload.toString()) }
        return readJson(conn)
    }

    private fun readJson(conn: HttpURLConnection): JSONObject {
        val responseCode = conn.responseCode
        val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { reader -> reader.readText() }.orEmpty()
        return try {
            JSONObject(body).put("http_status", responseCode)
        } catch (_: Exception) {
            JSONObject().put("ok", false).put("http_status", responseCode).put("raw", body.take(2000))
        } finally {
            conn.disconnect()
        }
    }

    private fun formatRunResponse(data: JSONObject): String {
        if (!data.optBoolean("ok", false)) return "Kali failed: ${data.optString("error", data.toString()).take(2500)}"

        val result = data.optJSONObject("result") ?: JSONObject()
        val reportId = data.optJSONObject("report")?.optString("report_id", "n/a") ?: "n/a"
        return buildString {
            appendLine("Kali result")
            appendLine("Action: ${data.optString("action", "n/a")}")
            appendLine("Target: ${data.optString("target", "n/a")}")
            appendLine("Report: $reportId")
            appendLine()
            appendLine(resultSummary(result, reportId))
        }.trim()
    }

    private fun formatWorkflowResponse(data: JSONObject): String {
        if (!data.optBoolean("ok", false)) return "Kali workflow failed: ${data.optString("error", data.toString()).take(2500)}"

        val result = data.optJSONObject("result") ?: JSONObject()
        val reportId = data.optJSONObject("report")?.optString("report_id", "n/a") ?: "n/a"
        val lines = mutableListOf<String>()
        lines.add("Kali workflow result")
        lines.add("Workflow: ${data.optString("workflow", "n/a")}")
        lines.add("Target: ${data.optString("target", "n/a")}")
        lines.add("Steps: ${result.optInt("steps_ok", 0)}/${result.optInt("steps_total", 0)} OK")
        lines.add("Report: $reportId")
        lines.add("")
        val steps = result.optJSONArray("steps")
        if (steps != null) {
            for (i in 0 until minOf(steps.length(), 8)) {
                val step = steps.optJSONObject(i) ?: continue
                lines.add("- ${step.optString("id")}: ${step.optString("action")} ok=${step.optBoolean("ok")}")
            }
        }
        return lines.joinToString("\n")
    }

    private fun formatTargetsResponse(data: JSONObject, maxItems: Int): String {
        if (!data.optBoolean("ok", false)) return "Targets failed: ${data.optString("error", data.toString()).take(2000)}"
        val targets = data.optJSONArray("targets") ?: return "No targets saved yet."
        if (targets.length() == 0) return "No targets saved yet."

        val lines = mutableListOf("Targets", "")
        for (i in 0 until minOf(targets.length(), maxItems)) {
            val item = targets.optJSONObject(i) ?: continue
            lines.add("${i + 1}. ${item.optString("name")} [${item.optString("type")}]")
            lines.add("   ${item.optString("value")}")
            lines.add("   allowed=${item.optBoolean("allowed")} ${item.optString("allow_reason")}")
        }
        return lines.joinToString("\n")
    }

    private fun formatReportsResponse(data: JSONObject, maxItems: Int): String {
        if (!data.optBoolean("ok", false)) return "Reports failed: ${data.optString("error", data.toString()).take(2500)}"
        val reports = data.optJSONArray("reports") ?: return "No reports yet. Run an action or workflow first."
        if (reports.length() == 0) return "No reports yet. Run an action or workflow first."

        val lines = mutableListOf("Reports", "")
        for (i in 0 until minOf(reports.length(), maxItems)) {
            val item = reports.optJSONObject(i) ?: continue
            lines.add("${i + 1}. ${item.optString("created_at")}")
            lines.add("   ID: ${item.optString("id")}")
            lines.add("   Action: ${item.optString("action")}")
            lines.add("   Target: ${item.optString("target")}")
            lines.add("   Summary: ${item.optString("summary").take(180)}")
            lines.add("")
        }
        lines.add("Open detail: /kali report REPORT_ID")
        return lines.joinToString("\n").trim()
    }

    private fun formatReportDetail(data: JSONObject): String {
        if (data.has("error") && !data.optBoolean("ok", true)) return "Report failed: ${data.optString("error", data.toString()).take(2500)}"
        val result = data.optJSONObject("result") ?: JSONObject()
        return buildString {
            appendLine("Report Detail")
            appendLine("ID: ${data.optString("id", "n/a")}")
            appendLine("Created: ${data.optString("created_at", "n/a")}")
            appendLine("Action: ${data.optString("action", "n/a")}")
            appendLine("Target: ${data.optString("target", "n/a")}")
            appendLine("Return code: ${result.optString("returncode", "n/a")}")
            appendLine()
            appendLine("Summary:")
            appendLine(resultSummary(result, "n/a"))
            appendLine()
            appendLine("Raw preview:")
            appendLine(result.toString(2).take(3000))
        }.trim()
    }

    private fun formatFindingsResponse(data: JSONObject): String {
        if (!data.optBoolean("ok", false)) return "Findings failed: ${data.optString("error", data.toString()).take(2000)}"
        return formatList(data.optJSONArray("findings"), "Findings") { index, item ->
            "${index + 1}. [${item.optString("severity")}] ${item.optString("title")}\n   Target: ${item.optString("target")}\n   Report: ${item.optString("report_id")}"
        }
    }

    private fun formatEvidenceResponse(data: JSONObject): String {
        if (!data.optBoolean("ok", false)) return "Evidence failed: ${data.optString("error", data.toString()).take(2000)}"
        return formatList(data.optJSONArray("evidence"), "Evidence") { index, item ->
            "${index + 1}. ${item.optString("name")} (${item.optString("type")}, ${item.optInt("size_bytes")} bytes)\n   ID: ${item.optString("id")}"
        }
    }

    private fun formatJobsResponse(data: JSONObject): String {
        if (!data.optBoolean("ok", false)) return "Jobs failed: ${data.optString("error", data.toString()).take(2000)}"
        return formatList(data.optJSONArray("jobs"), "Job History") { index, item ->
            "${index + 1}. ${item.optString("created_at")} ${item.optString("kind")} ${item.optString("status")}\n   Target: ${item.optString("target")}\n   Report: ${item.optString("report_id")}"
        }
    }

    private fun formatList(array: JSONArray?, title: String, formatter: (Int, JSONObject) -> String): String {
        if (array == null || array.length() == 0) return "No ${title.lowercase(Locale.US)} yet."
        val lines = mutableListOf(title, "")
        for (i in 0 until minOf(array.length(), 30)) {
            val item = array.optJSONObject(i) ?: continue
            lines.add(formatter(i, item))
        }
        return lines.joinToString("\n")
    }

    private fun resultSummary(result: JSONObject, reportId: String): String {
        return when {
            result.has("workflow") -> "Workflow ${result.optString("workflow")} completed: ${result.optInt("steps_ok")}/${result.optInt("steps_total")} steps OK."
            result.has("inventory") -> "Inventory completed. Report: $reportId"
            result.has("stdout") -> result.optString("stdout").ifBlank { "No stdout." }.take(1600)
            result.has("headers") -> "HTTP ${result.optInt("status_code")} server=${result.optString("server")} content_type=${result.optString("content_type")}"
            result.has("resolved_ips") -> "Resolved: ${jsonArrayToList(result.optJSONArray("resolved_ips")).joinToString(", ").ifBlank { "n/a" }}"
            result.has("not_after") -> "TLS valid until ${result.optString("not_after")} cipher=${result.optString("cipher")}"
            else -> result.toString(2).take(1600)
        }
    }

    private fun jsonArrayToList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until array.length()) out.add(array.optString(i))
        return out
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
