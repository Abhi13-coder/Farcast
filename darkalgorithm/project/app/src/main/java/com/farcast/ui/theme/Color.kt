package com.farcast.ui.theme

import androidx.compose.ui.graphics.Color
import com.farcast.agent.darkalgorithm.LeadCategory
import com.farcast.agent.darkalgorithm.LeadStatus

// Base surfaces
val FarcastBackground = Color(0xFF0E1116)
val FarcastPanel = Color(0xFF161B22)
val FarcastPanelRaised = Color(0xFF1D232C)
val FarcastHairline = Color(0xFF2A313C)

// Text
val FarcastTextPrimary = Color(0xFFE4E7EB)
val FarcastTextMuted = Color(0xFF8A93A0)

// Functional accents — each used for exactly one meaning, never decoratively.
val FarcastAmber = Color(0xFFE8A33D)   // primary / actionable / selected
val FarcastCyan = Color(0xFF4FB8C4)    // live telemetry ONLY — aviation/orbital/quakes/ISS while streaming
val FarcastBrick = Color(0xFFC2473D)   // flagged / blocked / error

/** Color is data, not decoration: every LeadCategory and LeadStatus maps to
 *  exactly one color, reused everywhere a Lead shows up (manifest rows, the
 *  pipeline screen, Moment Finder results) so the same meaning always looks
 *  the same across the whole app. */
fun colorForCategory(category: LeadCategory): Color = when (category) {
    LeadCategory.PUBLIC_RECORD -> FarcastTextMuted
    LeadCategory.SELF_EXPOSURE_AUDIT -> FarcastCyan
    LeadCategory.INFRASTRUCTURE_RECON -> FarcastAmber
    LeadCategory.FLAGGED -> FarcastBrick
    LeadCategory.UNCLASSIFIED -> FarcastTextMuted
}

fun colorForStatus(status: LeadStatus): Color = when (status) {
    LeadStatus.SELECTED, LeadStatus.EXPLORED -> FarcastAmber
    LeadStatus.BLOCKED -> FarcastBrick
    LeadStatus.PENDING, LeadStatus.HYDRATED, LeadStatus.SCORED -> FarcastTextMuted
    LeadStatus.DISCARDED, LeadStatus.CANCELLED -> FarcastHairline
}
