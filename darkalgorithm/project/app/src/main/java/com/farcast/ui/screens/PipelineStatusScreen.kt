package com.farcast.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farcast.agent.darkalgorithm.DarkMixer
import com.farcast.ui.theme.*

private val STAGES = listOf("source", "hydrate", "pre-filter", "score", "post-filter", "side-effects", "done")

/**
 * Nobody else has built UI for an autonomous OSINT pipeline, so there's no
 * existing pattern to lean on — this earns the one numbered sequence
 * DESIGN_NOTES allows, because DarkMixer's stages genuinely run in this
 * fixed order every single pass (Source -> Hydrator -> Filter -> Scorer ->
 * Selector -> SideEffect, per CandidatePipeline.kt).
 */
@Composable
fun PipelineStatusScreen(latestEvent: DarkMixer.ProgressEvent?, isRunning: Boolean) {
    val currentStageIndex = STAGES.indexOf(latestEvent?.stage).let { if (it < 0) -1 else it }

    Column(Modifier.fillMaxSize().background(FarcastBackground).padding(20.dp)) {
        Text("DARK ALGORITHM pipeline", style = FarcastTypography.headlineMedium, color = FarcastTextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            if (isRunning) "Running — re-fires on its own timer" else "Idle",
            style = FarcastDataStyleSmall,
            color = if (isRunning) FarcastCyan else FarcastTextMuted
        )
        Spacer(Modifier.height(24.dp))

        STAGES.forEachIndexed { index, stage ->
            val isPast = index < currentStageIndex
            val isCurrent = index == currentStageIndex
            StageRow(
                number = index + 1,
                label = stage,
                detail = if (isCurrent) latestEvent?.detail else null,
                color = when {
                    isCurrent -> FarcastAmber
                    isPast -> FarcastCyan
                    else -> FarcastTextMuted
                }
            )
        }
    }
}

@Composable
private fun StageRow(number: Int, label: String, detail: String?, color: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("%02d".format(number), style = FarcastDataStyle, color = color)
        Column {
            Text(label, style = FarcastTypography.titleMedium, color = color)
            if (detail != null) Text(detail, style = FarcastDataStyleSmall, color = FarcastTextMuted)
        }
    }
}
