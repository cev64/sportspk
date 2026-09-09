package com.personal.sportspk.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sportspk.LocalAppContainer
import com.personal.sportspk.data.local.UserSettings
import com.personal.sportspk.domain.model.League
import com.personal.sportspk.ui.navigation.GameDeepLink
import com.personal.sportspk.ui.navigation.TopLevelDestination
import com.personal.sportspk.ui.screens.about.AboutScreen
import com.personal.sportspk.ui.screens.scoreboard.Feed
import com.personal.sportspk.ui.screens.scoreboard.ScoreboardScreen
import com.personal.sportspk.ui.screens.scoreboard.ScoreboardViewModel
import com.personal.sportspk.ui.screens.settings.SettingsScreen
import com.personal.sportspk.ui.screens.standings.StandingsScreen

/**
 * The app shell.
 *
 * [NavigationSuiteScaffold] is what makes navigation itself adaptive: it renders a bottom bar on
 * the Fold's cover screen and a navigation rail on the inner display, from the same declaration,
 * without the app checking a device model anywhere. Per the navigation rule, we are not reusing
 * one component at every size - we are letting the adaptive API choose the right one.
 *
 * The selected tab is [rememberSaveable], so folding, rotating, or an Activity recreation lands
 * you back on the same tab rather than resetting to the start destination.
 */
@Composable
fun SportspkApp(
    settings: UserSettings,
    deepLink: GameDeepLink?,
    onDeepLinkConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current

    var selectedDestination by rememberSaveable(stateSaver = TopLevelDestinationSaver) {
        mutableStateOf(
            if (settings.startDestination == UserSettings.START_SCORES) {
                TopLevelDestination.Scores
            } else {
                TopLevelDestination.MyTeams
            }
        )
    }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var pendingGameId by rememberSaveable { mutableStateOf<String?>(null) }

    // A notification or widget tap opens the Scores tab at that game's league, with the game
    // already selected - not the feed with the user left to find it.
    val myTeamsViewModel: ScoreboardViewModel = viewModel(
        key = "feed-my-teams",
        factory = ScoreboardViewModel.factory(
            scoreboardRepository = container.scoreboardRepository,
            favoritesRepository = container.favoritesRepository,
            preferencesStore = container.preferencesStore,
            initialFeed = Feed.MyTeams,
            feedIsFixed = true,
        ),
    )
    val scoresViewModel: ScoreboardViewModel = viewModel(
        key = "feed-scores",
        factory = ScoreboardViewModel.factory(
            scoreboardRepository = container.scoreboardRepository,
            favoritesRepository = container.favoritesRepository,
            preferencesStore = container.preferencesStore,
            initialFeed = League.fromId(settings.lastLeagueId)?.let(Feed::OfLeague) ?: Feed.All,
            feedIsFixed = false,
        ),
    )

    LaunchedEffect(deepLink) {
        val link = deepLink ?: return@LaunchedEffect
        selectedDestination = TopLevelDestination.Scores
        showAbout = false
        scoresViewModel.selectFeed(Feed.OfLeague(link.league))
        pendingGameId = link.gameId
        onDeepLinkConsumed()
    }

    if (showAbout) {
        AboutScreen(onBack = { showAbout = false }, modifier = modifier.fillMaxSize())
        return
    }

    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = destination == selectedDestination,
                    onClick = { selectedDestination = destination },
                    icon = {
                        Icon(
                            imageVector = destination.icon(),
                            contentDescription = null,
                        )
                    },
                    label = { Text(destination.label()) },
                )
            }
        },
    ) {
        when (selectedDestination) {
            TopLevelDestination.MyTeams -> ScoreboardScreen(
                viewModel = myTeamsViewModel,
                showLeagueSelector = false,
                title = "My Teams",
                onOpenAbout = { showAbout = true },
                modifier = Modifier.fillMaxSize(),
            )

            TopLevelDestination.Scores -> ScoreboardScreen(
                viewModel = scoresViewModel,
                showLeagueSelector = true,
                title = "Scores",
                pendingGameId = pendingGameId,
                onPendingGameConsumed = { pendingGameId = null },
                onOpenAbout = { showAbout = true },
                modifier = Modifier.fillMaxSize(),
            )

            TopLevelDestination.Standings -> StandingsScreen(modifier = Modifier.fillMaxSize())

            TopLevelDestination.Settings -> SettingsScreen(
                onOpenAbout = { showAbout = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun TopLevelDestination.label(): String = when (this) {
    TopLevelDestination.MyTeams -> "My Teams"
    TopLevelDestination.Scores -> "Scores"
    TopLevelDestination.Standings -> "Standings"
    TopLevelDestination.Settings -> "Settings"
}

private fun TopLevelDestination.icon() = when (this) {
    TopLevelDestination.MyTeams -> Icons.Rounded.Star
    TopLevelDestination.Scores -> Icons.Rounded.DateRange
    TopLevelDestination.Standings -> Icons.AutoMirrored.Rounded.List
    TopLevelDestination.Settings -> Icons.Rounded.Settings
}

/** Saves the selected tab by route so it survives process death, not just recomposition. */
private val TopLevelDestinationSaver = androidx.compose.runtime.saveable.Saver<TopLevelDestination, String>(
    save = { it.route },
    restore = { TopLevelDestination.fromRoute(it) },
)
