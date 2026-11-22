package com.farcast.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.farcast.R

/**
 * Two families, two jobs — not a monotone hacker-terminal look:
 *  - PlexSans: navigation, headers, body copy, anything a person reads as prose.
 *  - MonoData: anything that IS data — coordinates, hashes, case IDs, scores,
 *    confidence percentages, timestamps. If it would look different pasted
 *    into a spreadsheet cell, it's MonoData; if it's a sentence, it's PlexSans.
 *
 * Both are open-source (Google Fonts), fitting an open-source app — bundle as
 * res/font/*.ttf or use Downloadable Fonts; exact R.font.* names below assume
 * the former.
 */
val PlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold)
)

val MonoData = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium)
)

val FarcastTypography = Typography(
    headlineMedium = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelSmall = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp)
)

/** Not part of Material3's Typography slots — data strings get this directly,
 *  e.g. Text("51.5074, -0.1278", style = FarcastDataStyle). */
val FarcastDataStyle = TextStyle(fontFamily = MonoData, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
val FarcastDataStyleSmall = TextStyle(fontFamily = MonoData, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 15.sp)
