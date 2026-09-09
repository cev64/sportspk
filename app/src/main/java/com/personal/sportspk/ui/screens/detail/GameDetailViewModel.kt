package com.personal.sportspk.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sportspk.domain.model.Game
import com.personal.sportspk.domain.model.GameDetail
import com.personal.sportspk.domain.repository.ScoreboardRepository
import com.personal.sportspk.ui.viewModelFactory
import com.personal.sportspk.util.ErrorText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

data class GameDetailUiState(
    val detail: GameDetail? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
)

/**
 * Loads box score / play-by-play for one game.
 *
 * Detail is fetched on demand rather than cached across sessions: the summary payload is large,
 * and a box score for a game you are not looking at is worth nothing. While a live game is open
 * this polls at a modest interval; a finished game is fetched once and left alone.
 */
class GameDetailViewModel(
    private val repository: ScoreboardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameDetailUiState())
    val uiState: StateFlow<GameDetailUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null
    private var currentGameId: String? = null

    /** Called whenever the selected game changes, including on the very first selection. */
    fun bind(game: Game) {
        if (currentGameId == game.id) {
            // Same game, but the list may have a fresher score - keep the header in step.
            _uiState.update { state ->
                state.copy(detail = state.detail?.copy(game = game))
            }
            return
        }
        currentGameId = game.id
        _uiState.value = GameDetailUiState(isLoading = true)
        startPolling(game)
    }

    fun refresh(game: Game) {
        viewModelScope.launch { load(game) }
    }

    private fun startPolling(game: Game) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            load(game)
            while (game.state.isLive) {
                delay(LIVE_DETAIL_INTERVAL)
                load(game)
            }
        }
    }

    private suspend fun load(game: Game) {
        _uiState.update { it.copy(isLoading = it.detail == null) }
        repository.gameDetail(game.league, game.id).fold(
            onSuccess = { detail ->
                _uiState.value = GameDetailUiState(detail = detail, isLoading = false)
            },
            onFailure = { error ->
                _uiState.update { state ->
                    state.copy(isLoading = false, message = ErrorText.of(error))
                }
            },
        )
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    companion object {
        private val LIVE_DETAIL_INTERVAL = 30.seconds

        fun factory(repository: ScoreboardRepository) = viewModelFactory {
            GameDetailViewModel(repository)
        }
    }
}
