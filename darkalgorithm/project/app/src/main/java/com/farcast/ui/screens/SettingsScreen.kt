package com.farcast.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farcast.agent.darkalgorithm.privacy.TorGateway
import com.farcast.ui.theme.*

@Composable
fun SettingsScreen(
    torStatus: TorGateway.TorStatus,
    onOpenOrbot: () -> Unit,
    onEnableWholeDeviceVpn: () -> Unit,
    searxngInstanceUrl: String,
    onSearxngUrlChange: (String) -> Unit,
    mcpEnabled: Boolean,
    onToggleMcp: () -> Unit,
    mcpPairingToken: String?,
    savedKeyLabels: List<String>,   // which SecureKeyVault entries already have a value — never show the value itself here
    onEditKey: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().background(FarcastBackground).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Settings", style = FarcastTypography.headlineMedium, color = FarcastTextPrimary)
        Spacer(Modifier.height(20.dp))

        SectionHeader("Tor")
        TorStatusRow(torStatus, onOpenOrbot)
        TextButton(onClick = onEnableWholeDeviceVpn) {
            Text("Protect whole device (opens Orbot's VPN toggle)", color = FarcastAmber, style = FarcastTypography.bodyMedium)
        }

        Spacer(Modifier.height(24.dp))
        SectionHeader("Search")
        OutlinedTextField(
            value = searxngInstanceUrl,
            onValueChange = onSearxngUrlChange,
            label = { Text("SearXNG instance URL") },
            placeholder = { Text("https://your-instance.onion or https://...") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = FarcastAmber, unfocusedBorderColor = FarcastHairline)
        )
        Text(
            "No default instance — pick one from searx.space or self-host. JSON output must be enabled on it.",
            style = FarcastDataStyleSmall, color = FarcastTextMuted, modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(24.dp))
        SectionHeader("AI access (MCP)")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = mcpEnabled, onCheckedChange = { onToggleMcp() },
                colors = SwitchDefaults.colors(checkedTrackColor = FarcastAmber)
            )
            Spacer(Modifier.width(10.dp))
            Text("Let a connected AI assistant read case data", style = FarcastTypography.bodyMedium, color = FarcastTextPrimary)
        }
        if (mcpEnabled && mcpPairingToken != null) {
            Text(
                "Pairing token (copy into your MCP client config):",
                style = FarcastDataStyleSmall, color = FarcastTextMuted, modifier = Modifier.padding(top = 8.dp)
            )
            Text(mcpPairingToken, style = FarcastDataStyle, color = FarcastAmber)
            Text(
                "Loopback only — reach it from a desktop with: adb forward tcp:8765 tcp:8765",
                style = FarcastDataStyleSmall, color = FarcastTextMuted
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionHeader("API keys")
        listOf("urlscan" to savedKeyLabels.contains("urlscan"),
            "AbuseIPDB" to savedKeyLabels.contains("abuse"),
            "Windy" to savedKeyLabels.contains("windy"),
            "Google Vision" to savedKeyLabels.contains("google_vision")
        ).forEach { (label, hasValue) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = FarcastTypography.bodyMedium, color = FarcastTextPrimary)
                TextButton(onClick = { onEditKey(label) }) {
                    Text(if (hasValue) "Edit" else "Add", color = FarcastAmber, style = FarcastTypography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = FarcastTypography.titleMedium, color = FarcastTextPrimary, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun TorStatusRow(status: TorGateway.TorStatus, onOpenOrbot: () -> Unit) {
    val (label, color) = when (status) {
        is TorGateway.TorStatus.On -> "Connected — ${status.socksHost}:${status.socksPort}" to FarcastCyan
        TorGateway.TorStatus.Starting -> "Starting…" to FarcastAmber
        TorGateway.TorStatus.Off -> "Off" to FarcastTextMuted
        TorGateway.TorStatus.Stopping -> "Stopping…" to FarcastAmber
        TorGateway.TorStatus.OrbotNotInstalled -> "Orbot not installed" to FarcastBrick
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = FarcastDataStyle, color = color)
        if (status is TorGateway.TorStatus.OrbotNotInstalled) {
            TextButton(onClick = onOpenOrbot) { Text("Get Orbot", color = FarcastAmber) }
        }
    }
}
