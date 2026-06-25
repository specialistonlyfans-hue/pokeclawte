// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.kali

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.agents.pokeclaw.integrations.kali.KaliOrchestratorClient
import java.util.concurrent.Executors

/**
 * Kali Lab cockpit.
 *
 * UI for the same policy-checked Kali Orchestrator integration that also works
 * from PokeClaw chat with /kali commands.
 */
class KaliLabActivity : ComponentActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var client: KaliOrchestratorClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        client = KaliOrchestratorClient(this)
        setContent {
            KaliLabScreen(
                onBack = { finish() },
                runCommand = { command, callback ->
                    executor.execute {
                        val response = try {
                            client.run(client.parse(command))
                        } catch (e: Exception) {
                            "Kali UI error: ${e.message ?: e.javaClass.simpleName}"
                        }
                        runOnUiThread { callback(response) }
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KaliLabScreen(
    onBack: () -> Unit,
    runCommand: (String, (String) -> Unit) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    var output by remember { mutableStateOf("Ready. Configure Kali Orchestrator, then run status or a workflow.") }
    var running by remember { mutableStateOf(false) }

    fun submit(command: String) {
        running = true
        output = "Running: $command"
        runCommand(command) { result ->
            output = result
            running = false
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Kali Lab") },
                    navigationIcon = {
                        Button(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) { Text("Back") }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(12.dp)
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    listOf("Run", "Workflows", "Reports", "Settings").forEachIndexed { index, label ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                when (selectedTab) {
                    0 -> RunTab(running = running, onRun = ::submit)
                    1 -> WorkflowTab(running = running, onRun = ::submit)
                    2 -> ReportsTab(running = running, output = output, onRun = ::submit)
                    3 -> SettingsTab(running = running, onRun = ::submit)
                }
                Spacer(modifier = Modifier.height(12.dp))
                ResultBox(output = output)
            }
        }
    }
}

@Composable
private fun RunTab(running: Boolean, onRun: (String) -> Unit) {
    var target by remember { mutableStateOf("192.168.1.20") }
    var topPorts by remember { mutableStateOf("50") }
    var action by remember { mutableStateOf("scan_host") }
    val actions = listOf("ping", "dns_check", "web_check", "tls_check", "scan_host", "service_inventory")

    SectionCard(title = "Single Action") {
        OutlinedTextField(
            value = target,
            onValueChange = { target = it },
            label = { Text("Target / URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = topPorts,
            onValueChange = { topPorts = it.filter(Char::isDigit).take(3) },
            label = { Text("Top ports") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChipRow(items = actions, selected = action, onSelect = { action = it })
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            enabled = !running,
            onClick = { onRun("/kali $action $target ${topPorts.ifBlank { "50" }}") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (running) "Running…" else "Run Action") }
    }

    Spacer(modifier = Modifier.height(12.dp))
    SectionCard(title = "Quick Buttons") {
        Button(enabled = !running, onClick = { onRun("/kali status") }, modifier = Modifier.fillMaxWidth()) { Text("Status") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(enabled = !running, onClick = { onRun("/kali ping $target") }, modifier = Modifier.fillMaxWidth()) { Text("Ping Target") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(enabled = !running, onClick = { onRun("/kali service_inventory $target ${topPorts.ifBlank { "50" }}") }, modifier = Modifier.fillMaxWidth()) { Text("Service Inventory") }
    }
}

@Composable
private fun WorkflowTab(running: Boolean, onRun: (String) -> Unit) {
    var target by remember { mutableStateOf("192.168.1.20") }
    var topPorts by remember { mutableStateOf("50") }
    var workflow by remember { mutableStateOf("quick_host") }
    val workflows = listOf("quick_host", "web_audit")

    SectionCard(title = "Workflow Runner") {
        Text("Run several safe checks in one command.")
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = target,
            onValueChange = { target = it },
            label = { Text("Target / URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = topPorts,
            onValueChange = { topPorts = it.filter(Char::isDigit).take(3) },
            label = { Text("Top ports") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChipRow(items = workflows, selected = workflow, onSelect = { workflow = it })
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            enabled = !running,
            onClick = { onRun("/kali workflow $workflow $target ${topPorts.ifBlank { "50" }}") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (running) "Running…" else "Start Workflow") }
    }

    Spacer(modifier = Modifier.height(12.dp))
    SectionCard(title = "Workflow Steps") {
        Text("quick_host: dns_check → ping → web_check → tls_check → scan_host")
        Spacer(modifier = Modifier.height(6.dp))
        Text("web_audit: web_check → tls_check → scan_host")
    }
}

@Composable
private fun ReportsTab(running: Boolean, output: String, onRun: (String) -> Unit) {
    var reportId by remember { mutableStateOf("") }

    SectionCard(title = "Reports") {
        Text("Load recent reports from the Kali Orchestrator, then paste an ID to open details.")
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            enabled = !running,
            onClick = { onRun("/kali reports") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (running) "Loading…" else "Load Reports") }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = reportId,
            onValueChange = { reportId = it.trim() },
            label = { Text("Report ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            enabled = !running && reportId.isNotBlank(),
            onClick = { onRun("/kali report $reportId") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Open Report") }
    }
    Spacer(modifier = Modifier.height(12.dp))
    SectionCard(title = "Latest Result") {
        Text(output.take(1600), fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun SettingsTab(running: Boolean, onRun: (String) -> Unit) {
    var url by remember { mutableStateOf("http://192.168.1.50:8899") }
    var token by remember { mutableStateOf("") }

    SectionCard(title = "Connection") {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Kali Orchestrator URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("API Token") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            enabled = !running,
            onClick = { onRun("/kali config $url $token") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save Connection") }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(enabled = !running, onClick = { onRun("/kali status") }, modifier = Modifier.weight(1f)) { Text("Test") }
            Button(enabled = !running, onClick = { onRun("/kali clear") }, modifier = Modifier.weight(1f)) { Text("Clear") }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable Column.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ChipRow(items: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { item ->
                    FilterChip(
                        selected = selected == item,
                        onClick = { onSelect(item) },
                        label = { Text(item) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ResultBox(output: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(output, fontFamily = FontFamily.Monospace)
    }
}
