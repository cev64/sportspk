package com.personal.sportspk.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.sportspk.domain.model.Competitor
import com.personal.sportspk.domain.model.Game
import com.personal.sportspk.domain.model.GameState
import com.personal.sportspk.ui.theme.LocalScoreColors
import com.personal.sportspk.ui.theme.ScoreTextStyle
import com.personal.sportspk.util.GameFormatting

/**
 * One game.
 *
 * Identical on the cover screen and the inner display - the layout that changes between them is
 * the surrounding scaffold, not the row. Keeping one row means a score reads the same wherever
 * you see it, which is the point of a scoreboard.
 */
@Composable
fun GameRow(
    game: Game,
    isFavorite: Boolean,
    isSelected: Boolean,
    showLeagueLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scoreColors = LocalScoreColors.current
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "gameRowContainer",
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = describe(game) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (game.state == GameState.LIVE) {
                    LiveDot(color = scoreColors.live)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = GameFormatting.statusLine(game),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (game.state == GameState.LIVE) {
                        scoreColors.live
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f))
                if (showLeagueLabel) {
                    LeagueChip(text = game.league.shortName)
                }
                if (isFavorite) {
                    Spacer(Modifier.width(6.dp))
                    FavoriteDot()
                }
            }

            Spacer(Modifier.height(10.dp))
            CompetitorLine(game.away, game.state)
            Spacer(Modifier.height(8.dp))
            CompetitorLine(game.home, game.state)

            val trailing = listOfNotNull(
                game.broadcast,
                game.venue.takeIf { game.state == GameState.PRE },
            )
            if (trailing.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = trailing.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CompetitorLine(competitor: Competitor, state: GameState) {
    // A loser's score is dimmed once the game is final, so a column of finals reads at a glance.
    val isLoser = state == GameState.POST && competitor.isWinner == false
    val contentColor = if (isLoser) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TeamBadge(team = competitor.team, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                competitor.rank?.let { rank ->
                    Text(
                        text = "$rank",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = competitor.team.shortDisplayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            competitor.record?.let { record ->
                Text(
                    text = record,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = competitor.score?.toString() ?: "-",
            style = ScoreTextStyle,
            color = contentColor,
            modifier = Modifier.width(44.dp),
        )
    }
}

@Composable
private fun LiveDot(color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun FavoriteDot() {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiary)
            .semantics { contentDescription = "On My Teams" }
    )
}

@Composable
private fun LeagueChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** One spoken sentence per row, so TalkBack does not read six disconnected fragments. */
private fun describe(game: Game): String {
    val away = "${game.away.team.displayName} ${game.away.score ?: 0}"
    val home = "${game.home.team.displayName} ${game.home.score ?: 0}"
    return when (game.state) {
        GameState.PRE -> "${game.league.displayName}. ${game.away.team.displayName} at " +
            "${game.home.team.displayName}, ${GameFormatting.statusLine(game)}"

        GameState.LIVE -> "${game.league.displayName}, live. $away, $home. " +
            GameFormatting.statusLine(game)

        GameState.POST -> "${game.league.displayName}, final. $away, $home"
    }
}
