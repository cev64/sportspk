package com.personal.sportspk.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Everything the user can configure. New fields must have a default - see [from]. */
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val notifyGameStart: Boolean = true,
    val notifyFinalScore: Boolean = true,
    val backgroundRefresh: Boolean = true,
    /** Tab the app opens on. My Teams is the fast cover-screen check-in. */
    val startDestination: String = START_MY_TEAMS,
    /** League selected on the Scores tab, restored across launches. */
    val lastLeagueId: String? = null,
) {
    companion object {
        const val START_MY_TEAMS = "my_teams"
        const val START_SCORES = "scores"
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Lightweight settings, in DataStore.
 *
 * COMPATIBILITY RULE: keys here are permanent. Adding a setting means adding a key with a
 * default; it never means clearing the store. A key that is missing (because the value was
 * written by an older build) falls back to its default in [from], so preferences survive
 * upgrades untouched.
 */
class UserPreferencesStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val NOTIFY_GAME_START = booleanPreferencesKey("notify_game_start")
        val NOTIFY_FINAL_SCORE = booleanPreferencesKey("notify_final_score")
        val BACKGROUND_REFRESH = booleanPreferencesKey("background_refresh")
        val START_DESTINATION = stringPreferencesKey("start_destination")
        val LAST_LEAGUE_ID = stringPreferencesKey("last_league_id")
    }

    val settings: Flow<UserSettings> = context.dataStore.data
        .catch { throwable ->
            // A corrupt preferences file must not take the app down; fall back to defaults.
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { it.toSettings() }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = enabled }

    suspend fun setNotifyGameStart(enabled: Boolean) = edit { it[Keys.NOTIFY_GAME_START] = enabled }

    suspend fun setNotifyFinalScore(enabled: Boolean) = edit { it[Keys.NOTIFY_FINAL_SCORE] = enabled }

    suspend fun setBackgroundRefresh(enabled: Boolean) = edit { it[Keys.BACKGROUND_REFRESH] = enabled }

    suspend fun setStartDestination(value: String) = edit { it[Keys.START_DESTINATION] = value }

    suspend fun setLastLeagueId(leagueId: String?) = edit { prefs ->
        if (leagueId == null) prefs.remove(Keys.LAST_LEAGUE_ID) else prefs[Keys.LAST_LEAGUE_ID] = leagueId
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private fun Preferences.toSettings(): UserSettings {
        val defaults = UserSettings()
        return UserSettings(
            themeMode = this[Keys.THEME_MODE]
                ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: defaults.themeMode,
            dynamicColor = this[Keys.DYNAMIC_COLOR] ?: defaults.dynamicColor,
            notifyGameStart = this[Keys.NOTIFY_GAME_START] ?: defaults.notifyGameStart,
            notifyFinalScore = this[Keys.NOTIFY_FINAL_SCORE] ?: defaults.notifyFinalScore,
            backgroundRefresh = this[Keys.BACKGROUND_REFRESH] ?: defaults.backgroundRefresh,
            startDestination = this[Keys.START_DESTINATION] ?: defaults.startDestination,
            lastLeagueId = this[Keys.LAST_LEAGUE_ID] ?: defaults.lastLeagueId,
        )
    }
}
