package com.personal.sportspk.ui.screens.standings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sportspk.LocalAppContainer
import com.personal.sportspk.domain.model.League
import com.personal.sportspk.ui.components.EmptyState
import com.personal.sportspk.ui.components.TeamBadge

/**
 * Standings alongside a game, shown ONLY in the third pane on a display wide enough for it -
 * in practice the Fold's inner display.
 *
 * This is the "use the extra space for more information, not bigger buttons" rule in practice:
 * on the cover screen you get the game; unfolded you get the game plus where both teams sit in
 * the table, without navigating away.
 */
@Composable
fun StandingsContextPane(league: League?, modifier: Modifier = Modifier) {
    if (league == null) {
        EmptyState(
            title = "Standings",
            body = "Select a game to see where its teams sit in the table.",
            modifier = modifier,
        )
        return
    }

    val container = LocalAppContainer.current
    val viewModel: StandingsViewModel = viewModel(
        key = "standings-context",
        factory = StandingsViewModel.factory(container.scoreboardRepository, container.favoritesRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(league) { viewModel.selectLeague(league) }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text(
            text = "${league.shortName} standings",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )

        val standings = uiState.standings
        if (standings == null || standings.groups.isEmpty()) {
            EmptyState(
                title = "No standings",
                body = "Standings for the ${league.displayName} have not loaded yet.",
            )
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            standings.groups.forEach { group ->
                item(key = "ctx-header-${group.name}") {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    HorizontalDivider()
                }
                items(group.entries, key = { "ctx-${group.name}-${it.team.id}" }) { entry ->
                    val isFavorite = entry.team.key in uiState.favoriteKeys
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isFavorite) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                MaterialTheme.shapes.small,
                            )
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TeamBadge(team = entry.team, size = 22.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = entry.team.shortDisplayName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isFavorite) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = entry.summary
                                ?: listOfNotNull(entry.wins, entry.losses).joinToString("-"),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(64.dp),
                        )
                    }
                }
            }
        }
    }
}
