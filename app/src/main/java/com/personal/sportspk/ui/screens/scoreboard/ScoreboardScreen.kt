package com.personal.sportspk.ui.screens.scoreboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.sportspk.R
import com.personal.sportspk.domain.model.League
import com.personal.sportspk.ui.rememberScoreboardPaneDirective
import com.personal.sportspk.ui.components.EmptyState
import com.personal.sportspk.ui.components.GameRow
import com.personal.sportspk.ui.components.MessageBanner
import com.personal.sportspk.ui.screens.detail.GameDetailPane
import com.personal.sportspk.ui.screens.standings.StandingsContextPane
import com.personal.sportspk.util.GameFormatting
import kotlinx.coroutines.launch

/**
 * A scoreboard, adaptively laid out.
 *
 * COMPACT (Fold cover screen): a single column - the feed, or the selected game's detail on top
 * of it. Large touch targets, one thing at a time.
 *
 * EXPANDED (Fold inner display): list and detail side by side, plus an extra pane carrying
 * standings context for the selected game's league, so moving between games never loses the list.
 *
 * TRANSITION (folding/unfolding): [ListDetailPaneScaffold]'s navigator state is remembered
 * across configuration changes, and the selection lives in the ViewModel's SavedStateHandle.
 * Unfolding therefore keeps the same game selected and simply reveals the detail pane beside the
 * list instead of stacked over it; folding collapses back to whichever pane was in front.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScoreboardScreen(
    viewModel: ScoreboardViewModel,
    showLeagueSelector: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    /** Set by a deep link so the app can open straight at one game. */
    pendingGameId: String? = null,
    onPendingGameConsumed: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // A widened directive so the standings-context pane is actually reachable; the stock
    // one caps at two partitions and would leave the extra pane dead code.
    val navigator = rememberListDetailPaneScaffoldNavigator<String>(
        scaffoldDirective = rememberScoreboardPaneDirective(),
    )
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Poll only while this screen is actually in front of the user.
    LifecycleResumeEffect(viewModel) {
        viewModel.startAutoRefresh()
        onPauseOrDispose { viewModel.stopAutoRefresh() }
    }

    LaunchedEffect(pendingGameId) {
        val gameId = pendingGameId ?: return@LaunchedEffect
        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, gameId)
        onPendingGameConsumed()
    }

    // On compact widths the detail pane is a full screen, so Back must return to the list
    // rather than leaving the tab.
    BackHandler(enabled = navigator.canNavigateBack()) {
        scope.launch { navigator.navigateBack() }
    }

    val selectedGameId = navigator.currentDestination
        ?.takeIf { it.pane == ListDetailPaneScaffoldRole.Detail }
        ?.content

    ListDetailPaneScaffold(
        modifier = modifier,
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                ScoreboardListPane(
                    uiState = uiState,
                    title = title,
                    showLeagueSelector = showLeagueSelector,
                    selectedGameId = selectedGameId,
                    listState = listState,
                    onSelectFeed = viewModel::selectFeed,
                    onRefresh = { viewModel.refresh(force = true) },
                    onDismissMessage = viewModel::dismissMessage,
                    onGameClick = { gameId ->
                        scope.launch {
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, gameId)
                        }
                    },
                    onOpenAbout = onOpenAbout,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val game = uiState.games.firstOrNull { it.id == selectedGameId }
                if (game == null) {
                    EmptyState(
                        title = "No game selected",
                        body = "Pick a game from the list to see the score, box score, and play-by-play.",
                    )
                } else {
                    GameDetailPane(
                        game = game,
                        onBack = { scope.launch { navigator.navigateBack() } },
                        showBackButton = navigator.canNavigateBack(),
                    )
                }
            }
        },
        extraPane = {
            // Only composed when the window is wide enough for a third readable pane - in
            // practice the Fold's inner display. See rememberScoreboardPaneDirective().
            AnimatedPane {
                val league = uiState.games.firstOrNull { it.id == selectedGameId }?.league
                    ?: (uiState.feed as? Feed.OfLeague)?.league
                StandingsContextPane(league = league)
            }
        },
    )
}

@Composable
private fun ScoreboardListPane(
    uiState: ScoreboardUiState,
    title: String,
    showLeagueSelector: Boolean,
    selectedGameId: String?,
    listState: LazyListState,
    onSelectFeed: (Feed) -> Unit,
    onRefresh: () -> Unit,
    onDismissMessage: () -> Unit,
    onGameClick: (String) -> Unit,
    onOpenAbout: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScoreboardHeader(
            title = title,
            uiState = uiState,
            onRefresh = onRefresh,
            onOpenAbout = onOpenAbout,
        )

        if (showLeagueSelector) {
            LeagueSelector(
                selected = uiState.feed,
                onSelect = onSelectFeed,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        uiState.message?.let { message ->
            MessageBanner(
                message = message,
                onDismiss = onDismissMessage,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        when {
            uiState.isEmpty && !uiState.hasLoadedOnce -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.isEmpty -> {
                EmptyState(
                    title = emptyTitleFor(uiState.feed),
                    body = emptyBodyFor(uiState.feed),
                    actionLabel = "Refresh",
                    onAction = onRefresh,
                )
            }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.games, key = { it.id }) { game ->
                        GameRow(
                            game = game,
                            isFavorite = game.involves(uiState.favoriteKeys),
                            isSelected = game.id == selectedGameId,
                            showLeagueLabel = uiState.feed !is Feed.OfLeague,
                            onClick = { onGameClick(game.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreboardHeader(
    title: String,
    uiState: ScoreboardUiState,
    onRefresh: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            val subtitle = buildString {
                if (uiState.liveCount > 0) append("${uiState.liveCount} live")
                val updated = GameFormatting
                    .relativeUpdated(uiState.lastUpdatedMillis, System.currentTimeMillis())
                if (updated != null) {
                    if (isNotEmpty()) append(" · ")
                    append(updated)
                }
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        IconButton(onClick = onRefresh, enabled = !uiState.isRefreshing) {
            if (uiState.isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(4.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.cd_refresh),
                )
            }
        }
        IconButton(onClick = onOpenAbout) {
            Icon(imageVector = Icons.Rounded.Info, contentDescription = "About")
        }
    }
}

/**
 * League picker for the Scores tab.
 *
 * Present on compact too, not just the inner display: the spec is explicit that full league
 * boards must be one tap away on the cover screen, not inner-display-only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeagueSelector(
    selected: Feed,
    onSelect: (Feed) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected is Feed.All,
                onClick = { onSelect(Feed.All) },
                label = { Text("All") },
            )
        }
        items(League.entries) { league ->
            FilterChip(
                selected = (selected as? Feed.OfLeague)?.league == league,
                onClick = { onSelect(Feed.OfLeague(league)) },
                label = { Text(league.shortName) },
            )
        }
    }
}

private fun emptyTitleFor(feed: Feed): String = when (feed) {
    Feed.MyTeams -> "Nothing on for your teams"
    Feed.All -> "No games today"
    is Feed.OfLeague -> "No ${feed.league.shortName} games today"
}

private fun emptyBodyFor(feed: Feed): String = when (feed) {
    Feed.MyTeams -> "None of your teams are playing right now. Add or edit teams in Settings."
    Feed.All -> "Nothing is scheduled across any league right now."
    is Feed.OfLeague -> "The ${feed.league.displayName} has nothing scheduled right now - it may be the off-season."
}
