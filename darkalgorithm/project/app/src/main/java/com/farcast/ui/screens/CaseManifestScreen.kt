package com.farcast.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.farcast.agent.darkalgorithm.CaseFile
import com.farcast.agent.darkalgorithm.Lead
import com.farcast.agent.darkalgorithm.LeadStatus
import com.farcast.ui.theme.*

/**
 * Home screen. Opening FARCAST opens your active case's manifest directly —
 * per DESIGN_NOTES principle 2, there's no marketing-style landing page with
 * feature cards to click through first. If there's no active case, the
 * empty state (below) IS the landing experience, and it leads straight to
 * the one action that matters: generate or feed a case.
 */
@Composable
fun CaseManifestScreen(
    caseFile: CaseFile?,
    torConnected: Boolean,
    installedPluginCount: Int,
    isWorkerRunning: Boolean,
    onFeedJson: () -> Unit,
    onOpenLead: (Lead) -> Unit
) {
    Scaffold(
        containerColor = FarcastBackground,
        topBar = { StatusStrip(caseFile, torConnected, installedPluginCount) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onFeedJson,
                containerColor = FarcastAmber,
                contentColor = FarcastBackground,
                icon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
                text = { Text(if (isWorkerRunning) "Running" else "Feed JSON", style = FarcastTypography.titleMedium) }
            )
        }
    ) { padding ->
        if (caseFile == null) {
            EmptyManifestState(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Text(caseFile.title, style = FarcastTypography.headlineMedium, color = FarcastTextPrimary)
                    Text(caseFile.ownerAuthorizedScope.ifBlank { "No authorized scope set" },
                        style = FarcastTypography.bodyMedium, color = FarcastTextMuted)
                    Spacer(Modifier.height(16.dp))
                }
                items(caseFile.leads.sortedByDescending { it.score ?: 0.0 }) { lead ->
                    LeadRow(lead, onClick = { onOpenLead(lead) })
                }
                item { Spacer(Modifier.height(80.dp)) } // clearance for the FAB
            }
        }
    }
}

@Composable
private fun StatusStrip(caseFile: CaseFile?, torConnected: Boolean, pluginCount: Int) {
    Row(
        Modifier.fillMaxWidth().background(FarcastPanel).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(color = if (torConnected) FarcastCyan else FarcastTextMuted)
        Text(if (torConnected) "Tor connected" else "Tor offline", style = FarcastDataStyleSmall, color = FarcastTextMuted)
        Spacer(Modifier.weight(1f))
        Text("${caseFile?.leads?.size ?: 0} leads", style = FarcastDataStyleSmall, color = FarcastTextMuted)
        Text("$pluginCount plugins", style = FarcastDataStyleSmall, color = FarcastTextMuted)
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
private fun LeadRow(lead: Lead, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(0.5.dp, FarcastHairline)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatusDot(color = colorForCategory(lead.category))
        Column(Modifier.weight(1f)) {
            Text(lead.branch, style = FarcastDataStyleSmall, color = FarcastTextMuted)
            Text(lead.summary, style = FarcastTypography.bodyMedium, color = FarcastTextPrimary, maxLines = 2)
        }
        lead.score?.let {
            Text("%.2f".format(it), style = FarcastDataStyle, color = colorForStatus(lead.status))
        }
    }
}

@Composable
private fun EmptyManifestState(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No active case", style = FarcastTypography.headlineMedium, color = FarcastTextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Generate a case from what you know about a target, or feed in a JSON file to start DARK ALGORITHM running.",
            style = FarcastTypography.bodyMedium, color = FarcastTextMuted
        )
    }
}
