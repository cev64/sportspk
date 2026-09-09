package com.personal.sportspk.data.repository

import com.personal.sportspk.data.local.UserPreferencesStore
import com.personal.sportspk.data.local.UserSettings
import com.personal.sportspk.domain.model.League
import com.personal.sportspk.domain.model.Team
import com.personal.sportspk.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Explicit JSON backup of everything the user actually owns.
 *
 * Deliberately covers only user data - the My Teams list and settings. The cached scoreboards
 * are excluded because they are reconstructible from the network and would make the backup
 * large and instantly stale.
 *
 * Backups are a SAFETY NET, not a substitute for upgrade-in-place: a normal APK update
 * preserves this data automatically without anyone exporting anything.
 *
 * [BackupPayload.version] exists so a future format change can be migrated on import rather
 * than rejected.
 */
class BackupRepository(
    private val favoritesRepository: FavoritesRepository,
    private val preferencesStore: UserPreferencesStore,
) {

    @Serializable
    data class BackupPayload(
        val version: Int = CURRENT_VERSION,
        val exportedAtMillis: Long,
        val favorites: List<FavoriteBackup> = emptyList(),
        val settings: SettingsBackup? = null,
    )

    @Serializable
    data class FavoriteBackup(
        val leagueId: String,
        val teamId: String,
        val displayName: String,
        val abbreviation: String,
        val logoUrl: String? = null,
        val sortOrder: Int = 0,
    )

    @Serializable
    data class SettingsBackup(
        val themeMode: String,
        val dynamicColor: Boolean,
        val notifyGameStart: Boolean,
        val notifyFinalScore: Boolean,
        val backgroundRefresh: Boolean,
        val startDestination: String,
    )

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(nowMillis: Long = System.currentTimeMillis()): String {
        val settings = preferencesStore.settings.first()
        val payload = BackupPayload(
            exportedAtMillis = nowMillis,
            favorites = favoritesRepository.favorites().map {
                FavoriteBackup(
                    leagueId = it.league.id,
                    teamId = it.teamId,
                    displayName = it.displayName,
                    abbreviation = it.abbreviation,
                    logoUrl = it.logoUrl,
                    sortOrder = it.sortOrder,
                )
            },
            settings = SettingsBackup(
                themeMode = settings.themeMode.name,
                dynamicColor = settings.dynamicColor,
                notifyGameStart = settings.notifyGameStart,
                notifyFinalScore = settings.notifyFinalScore,
                backgroundRefresh = settings.backgroundRefresh,
                startDestination = settings.startDestination,
            ),
        )
        return json.encodeToString(BackupPayload.serializer(), payload)
    }

    /**
     * Restores a backup. Favorites are MERGED into the existing list, never replaced wholesale -
     * importing an old backup must not delete teams the user added since it was taken.
     */
    suspend fun import(contents: String): Result<ImportSummary> = runCatching {
        val payload = json.decodeFromString(BackupPayload.serializer(), contents)
        require(payload.version <= CURRENT_VERSION) {
            "This backup was written by a newer version of the app."
        }

        var restored = 0
        var skipped = 0
        payload.favorites.forEach { backup ->
            val league = League.fromId(backup.leagueId)
            if (league == null) {
                skipped++
                return@forEach
            }
            favoritesRepository.add(
                Team(
                    id = backup.teamId,
                    league = league,
                    displayName = backup.displayName,
                    shortDisplayName = backup.displayName,
                    abbreviation = backup.abbreviation,
                    location = "",
                    logoUrl = backup.logoUrl,
                    primaryColor = null,
                    alternateColor = null,
                )
            )
            restored++
        }

        payload.settings?.let { restoreSettings(it) }
        ImportSummary(favoritesRestored = restored, favoritesSkipped = skipped)
    }

    private suspend fun restoreSettings(backup: SettingsBackup) {
        com.personal.sportspk.data.local.ThemeMode.entries
            .firstOrNull { it.name == backup.themeMode }
            ?.let { preferencesStore.setThemeMode(it) }
        preferencesStore.setDynamicColor(backup.dynamicColor)
        preferencesStore.setNotifyGameStart(backup.notifyGameStart)
        preferencesStore.setNotifyFinalScore(backup.notifyFinalScore)
        preferencesStore.setBackgroundRefresh(backup.backgroundRefresh)
        if (backup.startDestination in setOf(UserSettings.START_MY_TEAMS, UserSettings.START_SCORES)) {
            preferencesStore.setStartDestination(backup.startDestination)
        }
    }

    data class ImportSummary(val favoritesRestored: Int, val favoritesSkipped: Int)

    companion object {
        const val CURRENT_VERSION = 1
        const val MIME_TYPE = "application/json"
        fun suggestedFileName(nowMillis: Long = System.currentTimeMillis()): String {
            val stamp = java.time.Instant.ofEpochMilli(nowMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
            return "sportspk-backup-$stamp.json"
        }
    }
}
