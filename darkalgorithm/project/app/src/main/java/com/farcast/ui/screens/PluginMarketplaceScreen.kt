package com.farcast.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farcast.agent.darkalgorithm.plugins.PluginCatalogClient
import com.farcast.agent.darkalgorithm.plugins.PluginKind
import com.farcast.ui.theme.*

@Composable
fun PluginMarketplaceScreen(
    catalogue: List<PluginCatalogClient.CatalogueEntry>,
    installedIds: Set<String>,
    installingIds: Set<String>,
    onInstall: (PluginCatalogClient.CatalogueEntry) -> Unit,
    onUninstall: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().background(FarcastBackground)) {
        Text(
            "Plugins", style = FarcastTypography.headlineMedium, color = FarcastTextPrimary,
            modifier = Modifier.padding(20.dp)
        )
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
            items(catalogue) { entry ->
                PluginRow(
                    entry = entry,
                    isInstalled = entry.manifest.id in installedIds,
                    isBusy = entry.manifest.id in installingIds,
                    onInstall = { onInstall(entry) },
                    onUninstall = { onUninstall(entry.manifest.id) }
                )
            }
        }
    }
}

@Composable
private fun PluginRow(
    entry: PluginCatalogClient.CatalogueEntry,
    isInstalled: Boolean,
    isBusy: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit
) {
    val m = entry.manifest
    Column(
        Modifier.fillMaxWidth().padding(vertical = 10.dp).border(0.5.dp, FarcastHairline).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.name, style = FarcastTypography.titleMedium, color = FarcastTextPrimary)
                Text("${m.author} · v${m.version} · ${m.kind.name.lowercase()}", style = FarcastDataStyleSmall, color = FarcastTextMuted)
            }
            PluginActionButton(isInstalled, isBusy, entry.compatible, onInstall, onUninstall)
        }
        Spacer(Modifier.height(6.dp))
        Text(m.description, style = FarcastTypography.bodyMedium, color = FarcastTextMuted, maxLines = 3)
        if (!entry.compatible) {
            Spacer(Modifier.height(6.dp))
            Text("Not compatible: ${entry.incompatibilityReason}", style = FarcastDataStyleSmall, color = FarcastBrick)
        }
        // What this plugin can actually do — shown before install, not buried.
        m.declaredFetchUrlTemplate?.let {
            Spacer(Modifier.height(6.dp))
            Text("Network access: $it", style = FarcastDataStyleSmall, color = FarcastTextMuted)
        }
        if (m.permissions.isNotEmpty()) {
            Text("Declares: ${m.permissions.joinToString()}", style = FarcastDataStyleSmall, color = FarcastTextMuted)
        }
    }
}

@Composable
private fun PluginActionButton(isInstalled: Boolean, isBusy: Boolean, compatible: Boolean, onInstall: () -> Unit, onUninstall: () -> Unit) {
    when {
        isBusy -> CircularProgressIndicator(Modifier.size(20.dp), color = FarcastAmber, strokeWidth = 2.dp)
        isInstalled -> TextButton(onClick = onUninstall) { Text("Remove", color = FarcastBrick, style = FarcastTypography.labelSmall) }
        else -> TextButton(onClick = onInstall, enabled = compatible) {
            Text("Install", color = if (compatible) FarcastAmber else FarcastTextMuted, style = FarcastTypography.labelSmall)
        }
    }
}
