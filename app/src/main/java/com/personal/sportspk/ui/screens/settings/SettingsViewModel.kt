package com.personal.sportspk.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sportspk.data.local.ThemeMode
import com.personal.sportspk.data.local.UserPreferencesStore
import com.personal.sportspk.data.local.UserSettings
import com.personal.sportspk.data.repository.BackupRepository
import com.personal.sportspk.domain.model.FavoriteTeam
import com.personal.sportspk.domain.model.League
import com.personal.sportspk.domain.model.Team
import com.personal.sportspk.domain.repository.FavoritesRepository
import com.personal.sportspk.domain.repository.ScoreboardRepository
import com.personal.sportspk.ui.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val favorites: List<FavoriteTeam> = emptyList(),
    val pickerLeague: League? = null,
    val pickerTeams: List<Team> = emptyList(),
    val isLoadingPicker: Boolean = false,
    val statusMessage: String? = null,
)

/** Settings, the My Teams editor, and backup export/import. */
class SettingsViewModel(
    private val preferencesStore: UserPreferencesStore,
    private val favoritesRepository: FavoritesRepository,
    private val scoreboardRepository: ScoreboardRepository,
    private val backupRepository: BackupRepository,
) : ViewModel() {

    private val pickerLeague = MutableStateFlow<League?>(null)
    private val pickerTeams = MutableStateFlow<List<Team>>(emptyList())
    private val loadingPicker = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesStore.settings,
        favoritesRepository.observeFavorites(),
        pickerLeague,
        pickerTeams,
        combine(loadingPicker, statusMessage) { loading, status -> loading to status },
    ) { settings, favorites, league, teams, (loading, status) ->
        SettingsUiState(
            settings = settings,
            favorites = favorites,
            pickerLeague = league,
            pickerTeams = teams,
            isLoadingPicker = loading,
            statusMessage = status,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { preferencesStore.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { preferencesStore.setDynamicColor(enabled) }

    fun setNotifyGameStart(enabled: Boolean) = viewModelScope.launch { preferencesStore.setNotifyGameStart(enabled) }

    fun setNotifyFinalScore(enabled: Boolean) = viewModelScope.launch { preferencesStore.setNotifyFinalScore(enabled) }

    fun setBackgroundRefresh(enabled: Boolean) = viewModelScope.launch { preferencesStore.setBackgroundRefresh(enabled) }

    fun setStartDestination(value: String) = viewModelScope.launch { preferencesStore.setStartDestination(value) }

    fun removeFavorite(favorite: FavoriteTeam) = viewModelScope.launch {
        favoritesRepository.remove(favorite.league, favorite.teamId)
    }

    fun moveFavorite(from: Int, to: Int) = viewModelScope.launch {
        val current = favoritesRepository.favorites().map { it.key }.toMutableList()
        if (from !in current.indices || to !in current.indices) return@launch
        current.add(to, current.removeAt(from))
        favoritesRepository.setOrder(current)
    }

    /** Opens the team picker for a league, loading its roster of teams from the provider. */
    fun openPicker(league: League) {
        pickerLeague.value = league
        pickerTeams.value = emptyList()
        loadingPicker.value = true
        viewModelScope.launch {
            val teams = scoreboardRepository.teams(league)
            pickerTeams.value = teams
            loadingPicker.value = false
            if (teams.isEmpty()) {
                statusMessage.value = "Could not load ${league.displayName} teams. Check your connection."
            }
        }
    }

    fun closePicker() {
        pickerLeague.value = null
        pickerTeams.value = emptyList()
    }

    fun addFavorite(team: Team) = viewModelScope.launch { favoritesRepository.add(team) }

    suspend fun exportBackup(): String = backupRepository.export()

    fun importBackup(contents: String) = viewModelScope.launch {
        backupRepository.import(contents).fold(
            onSuccess = { summary ->
                statusMessage.value = buildString {
                    append("Restored ${summary.favoritesRestored} teams")
                    if (summary.favoritesSkipped > 0) append(", skipped ${summary.favoritesSkipped}")
                    append(".")
                }
            },
            onFailure = { statusMessage.value = "That file could not be read as a Sportspk backup." },
        )
    }

    fun setStatusMessage(message: String?) {
        statusMessage.value = message
    }

    companion object {
        fun factory(
            preferencesStore: UserPreferencesStore,
            favoritesRepository: FavoritesRepository,
            scoreboardRepository: ScoreboardRepository,
            backupRepository: BackupRepository,
        ) = viewModelFactory {
            SettingsViewModel(preferencesStore, favoritesRepository, scoreboardRepository, backupRepository)
        }
    }
}
