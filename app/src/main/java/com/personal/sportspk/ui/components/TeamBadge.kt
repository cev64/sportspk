package com.personal.sportspk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.sportspk.domain.model.Team

/**
 * A team's abbreviation on a chip tinted with that team's own colour.
 *
 * Deliberately NOT a downloaded logo. ESPN publishes logo URLs, but rendering them would mean
 * adding an image-loading dependency and putting a network fetch behind every row of every
 * scoreboard - including offline ones. A colour-coded monogram is instantly recognisable at
 * a glance on the cover screen, renders from cache, and costs nothing.
 */
@Composable
fun TeamBadge(
    team: Team,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val background = remember(team.primaryColor) {
        parseTeamColor(team.primaryColor) ?: Color(0xFF52606D)
    }
    val content = remember(background) {
        if (background.luminance() > 0.55f) Color(0xFF10151C) else Color.White
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 28))
            .background(background)
            // The abbreviation is decorative here; the full team name is read out by the row.
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = team.abbreviation.take(4),
            color = content,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.30f).sp,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

/** ESPN gives colours as bare RRGGBB hex, sometimes blank or malformed. */
internal fun parseTeamColor(hex: String?): Color? {
    val cleaned = hex?.trim()?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
    val value = cleaned.toLongOrNull(radix = 16) ?: return null
    return Color(0xFF000000L or value)
}
