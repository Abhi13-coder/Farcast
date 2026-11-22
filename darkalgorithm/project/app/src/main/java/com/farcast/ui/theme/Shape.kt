package com.farcast.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Deliberately near-square — this is a manifest/report, not a SaaS card
 * grid. The only generous radius is on the FAB-equivalent "feed JSON"
 * action, which is the one place a soft, inviting shape is earned (it's
 * the single most important button in the app — starting autonomous work).
 */
val FarcastShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(28.dp) // reserved for the primary autonomous-run action only
)
