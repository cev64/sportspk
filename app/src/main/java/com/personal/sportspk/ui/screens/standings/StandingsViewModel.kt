package com.personal.sportspk.ui.screens.standings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sportspk.domain.model.League
import com.personal.sportspk.domain.model.Standings
import com.personal.sportspk.domain.repository.FavoritesRepository
import com.personal.sportspk.domain.repository.RefreshResult
import com.personal.sportspk.domain.repository.ScoreboardRepository
import com.personal.sportspk.ui.savedStateViewModelFactory
import com.personal.sportspk.util.ErrorText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StandingsUiState(
    val league: League = League.NFL,
    val standings: Standings? = null,
    val favoriteKeys: Set<String> = emptySet(),
    val isRefreshing: Boolean = false,
    val message: String? = null,
)

/**
 * Standings for one league at a time, with favorites highlighted.
 *
 * The selected league lives in SavedStateHandle so it survives folding, rotation, and process
 * death - the same continuity rule the scoreboards follow.
 */
class StandingsViewModel(
    private val scoreboardRepository: ScoreboardRepository,
    favoritesRepository: FavoritesRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val selectedLeague = MutableStateFlow(
        League.fromId(savedStateHandle[KEY_LEAGUE]) ?: League.NFL
    )
    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    @Suppress("OPT_IN_USAGE")
    val uiState: StateFlow<StandingsUiState> = combine(
        selectedLeague,
        selectedLeague.flatMapLatest { scoreboardRepository.observeStandings(it) },
        favoritesRepository.observeFavoriteKeys(),
        refreshing,
        message,
    ) { league, standings, favorites, isRefreshing, msg ->
        StandingsUiState(
            league = league,
            standings = standings,
            favoriteKeys = favorites,
            isRefreshing = isRefreshing,
            message = msg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StandingsUiState(league = selectedLeague.value),
    )

    init {
        refresh(force = false)
    }

    fun selectLeague(league: League) {
        if (league == selectedLeague.value) return
        selectedLeague.value = league
        savedStateHandle[KEY_LEAGUE] = league.id
        refresh(force = false)
    }

    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            refreshing.value = true
            message.value = null
            when (val result = scoreboardRepository.refreshStandings(selectedLeague.value, force)) {
                is RefreshResult.Failed -> message.value = ErrorText.of(result.error)
                else -> Unit
            }
            refreshing.value = false
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    companion object {
        private const val KEY_LEAGUE = "standings_league"

        fun factory(
            scoreboardRepository: ScoreboardRepository,
            favoritesRepository: FavoritesRepository,
        ) = savedStateViewModelFactory {
            StandingsViewModel(scoreboardRepository, favoritesRepository, it)
        }
    }
}
