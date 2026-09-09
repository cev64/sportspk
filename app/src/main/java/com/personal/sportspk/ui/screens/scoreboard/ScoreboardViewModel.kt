package com.personal.sportspk.ui.screens.scoreboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sportspk.data.local.UserPreferencesStore
import com.personal.sportspk.domain.model.Game
import com.personal.sportspk.domain.model.League
import com.personal.sportspk.domain.repository.FavoritesRepository
import com.personal.sportspk.domain.repository.RefreshResult
import com.personal.sportspk.domain.repository.ScoreboardRepository
import com.personal.sportspk.domain.usecase.RefreshPolicy
import com.personal.sportspk.ui.savedStateViewModelFactory
import com.personal.sportspk.util.ErrorText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which set of games a scoreboard is showing. */
sealed interface Feed {
    /** Only games involving a team on the My Teams list. */
    data object MyTeams : Feed

    /** Today's games across every league. */
    data object All : Feed

    data class OfLeague(val league: League) : Feed

    /** Stable string form, so the selection can round-trip through SavedStateHandle. */
    fun toKey(): String = when (this) {
        MyTeams -> "my_teams"
        All -> "all"
        is OfLeague -> "league:${league.id}"
    }

    companion object {
        fun fromKey(key: String): Feed? = when {
            key == "my_teams" -> MyTeams
            key == "all" -> All
            key.startsWith("league:") -> League.fromId(key.removePrefix("league:"))?.let(::OfLeague)
            else -> null
        }
    }
}

data class ScoreboardUiState(
    val feed: Feed = Feed.MyTeams,
    val games: List<Game> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    val isRefreshing: Boolean = false,
    val message: String? = null,
    val lastUpdatedMillis: Long? = null,
    val hasLoadedOnce: Boolean = false,
) {
    val liveCount: Int get() = games.count { it.state.isLive }
    val isEmpty: Boolean get() = games.isEmpty()
}

/**
 * Drives a scoreboard list.
 *
 * The same ViewModel backs the My Teams tab and the Scores tab; only [Feed] differs. Games are
 * always read from the Room cache, so rotating, folding, or losing the network never empties
 * the screen - a refresh failure sets [ScoreboardUiState.message] and leaves the list alone.
 *
 * Auto-refresh runs only while a screen is actually resumed ([startAutoRefresh]/[stopAutoRefresh]),
 * at the interval [RefreshPolicy] picks. Background polling while the app is closed is
 * WorkManager's job, not this class's.
 */
class ScoreboardViewModel(
    private val scoreboardRepository: ScoreboardRepository,
    private val favoritesRepository: FavoritesRepository,
    private val preferencesStore: UserPreferencesStore,
    private val savedStateHandle: SavedStateHandle,
    initialFeed: Feed,
    private val feedIsFixed: Boolean,
) : ViewModel() {

    private val feedState = MutableStateFlow(restoreFeed(initialFeed))
    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val lastUpdated = MutableStateFlow<Long?>(null)
    private val loadedOnce = MutableStateFlow(false)

    private var autoRefreshJob: Job? = null

    @Suppress("OPT_IN_USAGE")
    private val gamesFlow = combine(feedState, favoritesRepository.observeFavoriteKeys()) { feed, keys ->
        feed to keys
    }.flatMapLatest { (feed, keys) ->
        when (feed) {
            Feed.MyTeams -> scoreboardRepository.observeGamesForTeams(keys)
            Feed.All -> scoreboardRepository.observeAllScoreboards()
            is Feed.OfLeague -> scoreboardRepository.observeScoreboard(feed.league)
        }
    }

    val uiState: StateFlow<ScoreboardUiState> = combine(
        feedState,
        gamesFlow,
        favoritesRepository.observeFavoriteKeys(),
        combine(refreshing, message, lastUpdated, loadedOnce) { isRefreshing, msg, updated, loaded ->
            Status(isRefreshing, msg, updated, loaded)
        },
    ) { feed, games, favoriteKeys, status ->
        ScoreboardUiState(
            feed = feed,
            games = games,
            favoriteKeys = favoriteKeys,
            isRefreshing = status.isRefreshing,
            message = status.message,
            lastUpdatedMillis = status.lastUpdated,
            hasLoadedOnce = status.loadedOnce,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ScoreboardUiState(feed = feedState.value),
    )

    private data class Status(
        val isRefreshing: Boolean,
        val message: String?,
        val lastUpdated: Long?,
        val loadedOnce: Boolean,
    )

    fun selectFeed(feed: Feed) {
        if (feedIsFixed || feed == feedState.value) return
        feedState.value = feed
        savedStateHandle[KEY_FEED] = feed.toKey()
        viewModelScope.launch {
            preferencesStore.setLastLeagueId((feed as? Feed.OfLeague)?.league?.id)
        }
        refresh(force = false)
    }

    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            refreshing.value = true
            message.value = null
            val leagues = leaguesFor(feedState.value)
            var failure: Throwable? = null
            leagues.forEach { league ->
                when (val result = scoreboardRepository.refreshScoreboard(league, force)) {
                    is RefreshResult.Failed -> failure = failure ?: result.error
                    else -> Unit
                }
            }
            lastUpdated.value = leagues.mapNotNull { scoreboardRepository.lastRefreshMillis(it) }.maxOrNull()
            message.value = failure?.let { ErrorText.of(it) }
            loadedOnce.value = true
            refreshing.value = false
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    /** Starts the in-app polling loop. Call from a resumed screen; always pair with [stopAutoRefresh]. */
    fun startAutoRefresh() {
        if (autoRefreshJob?.isActive == true) return
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                refresh(force = false)
                val state = uiState.value
                delay(RefreshPolicy.intervalFor(state.games, state.favoriteKeys, System.currentTimeMillis()))
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    override fun onCleared() {
        stopAutoRefresh()
        super.onCleared()
    }

    private suspend fun leaguesFor(feed: Feed): List<League> = when (feed) {
        Feed.All -> League.entries
        is Feed.OfLeague -> listOf(feed.league)
        // Only the leagues the user actually follows - refreshing all seven to render My Teams
        // would be a lot of requests for rows we would immediately throw away.
        Feed.MyTeams -> favoritesRepository.favorites().map { it.league }.distinct()
    }

    private fun restoreFeed(fallback: Feed): Feed =
        savedStateHandle.get<String>(KEY_FEED)?.let { Feed.fromKey(it) } ?: fallback

    companion object {
        private const val KEY_FEED = "scoreboard_feed"
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(
            scoreboardRepository: ScoreboardRepository,
            favoritesRepository: FavoritesRepository,
            preferencesStore: UserPreferencesStore,
            initialFeed: Feed,
            feedIsFixed: Boolean,
        ) = savedStateViewModelFactory {
            ScoreboardViewModel(
                scoreboardRepository = scoreboardRepository,
                favoritesRepository = favoritesRepository,
                preferencesStore = preferencesStore,
                savedStateHandle = it,
                initialFeed = initialFeed,
                feedIsFixed = feedIsFixed,
            )
        }
    }
}
