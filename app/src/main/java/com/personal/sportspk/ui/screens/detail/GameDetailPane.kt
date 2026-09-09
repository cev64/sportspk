package com.personal.sportspk.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sportspk.LocalAppContainer
import com.personal.sportspk.R
import com.personal.sportspk.domain.model.Competitor
import com.personal.sportspk.domain.model.Game
import com.personal.sportspk.domain.model.GameState
import com.personal.sportspk.domain.model.StatGroup
import com.personal.sportspk.domain.model.TeamBoxScore
import com.personal.sportspk.ui.components.MessageBanner
import com.personal.sportspk.ui.components.SectionHeader
import com.personal.sportspk.ui.components.TeamBadge
import com.personal.sportspk.ui.theme.LocalScoreColors
import com.personal.sportspk.util.GameFormatting

/**
 * Game detail: score header, box score, and play-by-play where the league publishes it.
 *
 * The SAME pane is used from a full league board and from My Teams, and on both the cover screen
 * (as a full-width pane) and the inner display (beside the list). It scrolls in one column
 * either way; the extra width on the inner display is spent by the surrounding scaffold showing
 * more panes, not by inflating this one.
 */
@Composable
fun GameDetailPane(
    game: Game,
    showBackButton: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: GameDetailViewModel = viewModel(
        key = "game-detail",
        factory = GameDetailViewModel.factory(container.scoreboardRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(game.id, game.state, game.home.score, game.away.score) {
        viewModel.bind(game)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBackButton) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                    )
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = game.league.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (uiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        uiState.message?.let { message ->
            MessageBanner(
                message = message,
                onDismiss = viewModel::dismissMessage,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item { ScoreHeader(game) }

            item {
                val meta = listOfNotNull(
                    GameFormatting.startDay(game) + " " + GameFormatting.startTime(game),
                    game.venue,
                    game.broadcast,
                ).joinToString(" · ")
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                game.note?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            val detail = uiState.detail
            if (detail != null && detail.boxScore.isNotEmpty()) {
                item { SectionHeader("Box score") }
                item { BoxScoreTable(detail.boxScore) }
            } else if (detail != null && !uiState.isLoading) {
                item {
                    Text(
                        text = if (game.state == GameState.PRE) {
                            "The box score appears once the game starts."
                        } else {
                            "No box score is published for this game."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }

            if (detail != null && detail.plays.isNotEmpty()) {
                item { SectionHeader("Play by play") }
                items(detail.plays, key = { it.id }) { play ->
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        val head = listOfNotNull(play.periodText, play.clock, play.scoreText)
                            .joinToString(" · ")
                        if (head.isNotEmpty()) {
                            Text(
                                text = head,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(text = play.text, style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ScoreHeader(game: Game) {
    val scoreColors = LocalScoreColors.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = GameFormatting.statusLine(game),
                style = MaterialTheme.typography.labelLarge,
                color = if (game.state.isLive) scoreColors.live else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompetitorColumn(game.away, Modifier.weight(1f))
                Text(
                    text = "at",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                CompetitorColumn(game.home, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CompetitorColumn(competitor: Competitor, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TeamBadge(team = competitor.team, size = 44.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = competitor.team.shortDisplayName,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        competitor.record?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = competitor.score?.toString() ?: "-",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Team stats side by side.
 *
 * Rows are the union of both teams' labels, so a stat one side reports and the other omits still
 * lines up instead of shifting the whole column.
 */
@Composable
private fun BoxScoreTable(boxScore: List<TeamBoxScore>) {
    val away = boxScore.firstOrNull { !it.isHome } ?: boxScore.getOrNull(0)
    val home = boxScore.firstOrNull { it.isHome } ?: boxScore.getOrNull(1)
    if (away == null || home == null) return

    val groupNames = (away.groups.map { it.name } + home.groups.map { it.name }).distinct()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Spacer(Modifier.weight(1.4f))
            Text(
                text = away.team.abbreviation,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.6f),
            )
            Text(
                text = home.team.abbreviation,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.6f),
            )
        }
        HorizontalDivider()

        groupNames.forEach { groupName ->
            val awayGroup = away.groups.firstOrNull { it.name == groupName }
            val homeGroup = home.groups.firstOrNull { it.name == groupName }
            if (groupName != null) {
                Text(
                    text = groupName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            statLabels(awayGroup, homeGroup).forEach { label ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        modifier = Modifier.weight(1.4f),
                    )
                    Text(
                        text = awayGroup?.stats?.firstOrNull { it.label == label }?.displayValue ?: "-",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.6f),
                    )
                    Text(
                        text = homeGroup?.stats?.firstOrNull { it.label == label }?.displayValue ?: "-",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.6f),
                    )
                }
            }
        }
    }
}

private fun statLabels(away: StatGroup?, home: StatGroup?): List<String> =
    (away?.stats.orEmpty().map { it.label } + home?.stats.orEmpty().map { it.label }).distinct()
