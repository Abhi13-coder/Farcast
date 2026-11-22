package com.farcast.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farcast.agent.darkalgorithm.features.LabelCount
import com.farcast.agent.darkalgorithm.features.MomentFinderService
import com.farcast.ui.theme.*

/**
 * The actual "type a label, get every moment it appeared" screen —
 * MomentFinderService.findMoments() made tappable. "Search by image" is a
 * separate entry point (pick an image -> ImageQueryService) rather than
 * crammed into this same text field, since they're genuinely different
 * inputs with different affordances (a text field vs. an image picker),
 * not one control pretending to handle both.
 */
@Composable
fun MomentFinderScreen(
    availableLabels: List<LabelCount>,
    moments: List<MomentFinderService.Moment>,
    onSearchLabel: (String) -> Unit,
    onPickImage: () -> Unit,
    detectedLabelsFromImage: List<String>?
) {
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(FarcastBackground).padding(20.dp)) {
        Text("Moment Finder", style = FarcastTypography.headlineMedium, color = FarcastTextPrimary)
        Spacer(Modifier.height(4.dp))
        Text("Search every auto-labeled image in this case", style = FarcastTypography.bodyMedium, color = FarcastTextMuted)
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; onSearchLabel(it) },
                label = { Text("Label, e.g. \"car\", \"person\"") },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = FarcastAmber, unfocusedBorderColor = FarcastHairline)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onPickImage) {
                Icon(androidx.compose.material.icons.Icons.Outlined.Image, contentDescription = "Search by image", tint = FarcastAmber)
            }
        }

        detectedLabelsFromImage?.let { labels ->
            Spacer(Modifier.height(8.dp))
            Text("Detected in your image: ${labels.joinToString()}", style = FarcastDataStyleSmall, color = FarcastCyan)
        }

        if (availableLabels.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Available in this case:", style = FarcastDataStyleSmall, color = FarcastTextMuted)
            LazyRowLabels(availableLabels) { query = it; onSearchLabel(it) }
        }

        Spacer(Modifier.height(16.dp))
        LazyColumn {
            items(moments) { moment ->
                MomentRow(moment)
            }
        }
    }
}

@Composable
private fun LazyRowLabels(labels: List<LabelCount>, onTap: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow {
        items(labels) { lc ->
            AssistChip(
                onClick = { onTap(lc.label) },
                label = { Text("${lc.label} (${lc.count})", style = FarcastDataStyleSmall) },
                colors = AssistChipDefaults.assistChipColors(containerColor = FarcastPanelRaised, labelColor = FarcastTextPrimary),
                modifier = Modifier.padding(end = 6.dp)
            )
        }
    }
}

@Composable
private fun MomentRow(moment: MomentFinderService.Moment) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(moment.label, style = FarcastTypography.bodyMedium, color = FarcastTextPrimary)
            Text(moment.leadId, style = FarcastDataStyleSmall, color = FarcastTextMuted)
        }
        Text("${(moment.confidence * 100).toInt()}%", style = FarcastDataStyle, color = FarcastAmber)
    }
}
