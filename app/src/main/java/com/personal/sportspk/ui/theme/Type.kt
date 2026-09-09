package com.personal.sportspk.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Scoreboard typography: tight, dense, with a strong hierarchy between a score and its label.
 * Scores use tabular-ish sizing and heavy weight so a column of them lines up when scanned.
 */
internal val SportspkTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
    )
}

/** The score itself. Kept separate so it does not drift when the type scale is tuned. */
internal val ScoreTextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    textAlign = TextAlign.End,
)
