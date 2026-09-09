package com.personal.sportspk.ui.screens.standings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sportspk.LocalAppContainer
import com.personal.sportspk.domain.model.League
import com.personal.sportspk.domain.model.StandingEntry
import com.personal.sportspk.ui.components.EmptyState
import com.personal.sportspk.ui.components.MessageBanner
import com.personal.sportspk.ui.components.TeamBadge

/**
 * Full standings screen.
 *
 * COMPACT: league chips above a single scrolling table.
 * EXPANDED: the same content, but the extra width lets the record columns breathe and the
 * scaffold shows it beside the navigation rail instead of under a bottom bar.
 * TRANSITION: selected league and scroll position are retained (SavedStateHandle + LazyColumn
 * state), so folding does not send you back to the top of the NFL table.
 */
@Composable
fun StandingsScreen(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val viewModel: StandingsViewModel = viewModel(
        factory = StandingsViewModel.factory(container.scoreboardRepository, container.favoritesRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Standings",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(League.entries) { league ->
                FilterChip(
                    selected = uiState.league == league,
                    onClick = { viewModel.selectLeague(league) },
                    label = { Text(league.shortName) },
                )
            }
        }

        uiState.message?.let { message ->
            MessageBanner(
                message = message,
                onDismiss = viewModel::dismissMessage,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        val standings = uiState.standings
        if (standings == null || standings.groups.isEmpty()) {
            EmptyState(
                title = "No standings yet",
                body = "Standings for the ${uiState.league.displayName} have not loaded. " +
                    "They may not be published outside the season.",
                actionLabel = "Refresh",
                onAction = { viewModel.refresh(force = true) },
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            standings.groups.forEach { group ->
                item(key = "header-${group.name}") {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    StandingsColumnHeader(uiState.league)
                    HorizontalDivider()
                }
                items(group.entries, key = { "${group.name}-${it.team.id}" }) { entry ->
                    StandingsRow(
                        entry = entry,
                        league = uiState.league,
                        isFavorite = entry.team.key in uiState.favoriteKeys,
                    )
                }
            }
        }
    }
}

@Composable
private fun StandingsColumnHeader(league: League) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = "Team",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        headerColumns(league).forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(48.dp),
            )
        }
    }
}

@Composable
private fun StandingsRow(entry: StandingEntry, league: League, isFavorite: Boolean) {
    // A favorite is tinted rather than badged: the point is to find your team while scanning.
    val background = if (isFavorite) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, MaterialTheme.shapes.small)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamBadge(team = entry.team, size = 26.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = entry.team.shortDisplayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isFavorite) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        valueColumns(entry, league).forEach { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.width(48.dp),
            )
        }
    }
}

/**
 * Different sports keep different columns. Soccer tables are points-driven; North American
 * leagues are W-L(-T) with games behind.
 */
private fun headerColumns(league: League): List<String> = when (league) {
    League.EPL -> listOf("W", "D", "L", "PTS")
    League.NHL -> listOf("W", "L", "OTL", "PTS")
    else -> listOf("W", "L", "PCT", "GB")
}

private fun valueColumns(entry: StandingEntry, league: League): List<String> = when (league) {
    League.EPL -> listOf(
        entry.wins?.toString() ?: "-",
        entry.ties?.toString() ?: "-",
        entry.losses?.toString() ?: "-",
        entry.points ?: "-",
    )

    League.NHL -> listOf(
        entry.wins?.toString() ?: "-",
        entry.losses?.toString() ?: "-",
        entry.ties?.toString() ?: "-",
        entry.points ?: "-",
    )

    else -> listOf(
        entry.wins?.toString() ?: "-",
        entry.losses?.toString() ?: "-",
        entry.winPercent ?: "-",
        entry.gamesBehind ?: "-",
    )
}
