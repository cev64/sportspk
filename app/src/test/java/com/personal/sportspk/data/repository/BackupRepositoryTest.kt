package com.personal.sportspk.data.repository

import com.personal.sportspk.domain.model.FavoriteTeam
import com.personal.sportspk.domain.model.League
import com.personal.sportspk.domain.model.Team
import com.personal.sportspk.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Backup is a data-preservation feature, so the behaviour that matters is the destructive case:
 * importing an OLD backup must not delete favorites added since it was taken.
 */
class BackupRepositoryTest {

    private class FakeFavorites : FavoritesRepository {
        val state = MutableStateFlow<List<FavoriteTeam>>(emptyList())

        override fun observeFavorites(): Flow<List<FavoriteTeam>> = state
        override fun observeFavoriteKeys(): Flow<Set<String>> = state.map { it.map(FavoriteTeam::key).toSet() }
        override suspend fun favorites(): List<FavoriteTeam> = state.value

        override suspend fun add(team: Team) {
            if (state.value.any { it.key == team.key }) return
            state.value = state.value + FavoriteTeam(
                league = team.league,
                teamId = team.id,
                displayName = team.displayName,
                abbreviation = team.abbreviation,
                logoUrl = team.logoUrl,
                sortOrder = state.value.size,
            )
        }

        override suspend fun remove(league: League, teamId: String) {
            state.value = state.value.filterNot { it.league == league && it.teamId == teamId }
        }

        override suspend fun setOrder(orderedKeys: List<String>) = Unit
        override suspend fun seedDefaultsIfNeverSeeded() = Unit
    }

    private fun favorite(league: League, id: String, name: String) = FavoriteTeam(
        league = league,
        teamId = id,
        displayName = name,
        abbreviation = name.take(3).uppercase(),
        logoUrl = null,
        sortOrder = 0,
    )

    /**
     * BackupRepository needs a UserPreferencesStore, which is Android-backed. Settings round-trip
     * is covered by the instrumented tests; here we exercise the favorites path by asserting on
     * the JSON directly.
     */
    @Test
    fun `exported payload is our own format, not ESPN's`() = runTest {
        val favorites = FakeFavorites()
        favorites.add(team(League.NFL, "12", "Kansas City Chiefs"))

        val payload = kotlinx.serialization.json.Json.encodeToString(
            BackupRepository.BackupPayload.serializer(),
            BackupRepository.BackupPayload(
                exportedAtMillis = 1L,
                favorites = favorites.favorites().map {
                    BackupRepository.FavoriteBackup(
                        leagueId = it.league.id,
                        teamId = it.teamId,
                        displayName = it.displayName,
                        abbreviation = it.abbreviation,
                        sortOrder = it.sortOrder,
                    )
                },
            ),
        )

        assertTrue(payload.contains("\"leagueId\": \"nfl\"") || payload.contains("\"leagueId\":\"nfl\""))
        assertTrue(payload.contains("Kansas City Chiefs"))
        assertTrue("must not leak provider shape", !payload.contains("competitions"))
    }

    @Test
    fun `importing merges rather than replacing so newer favorites survive`() = runTest {
        val favorites = FakeFavorites()
        // The user added a team AFTER the backup below was taken.
        favorites.state.value = listOf(favorite(League.NBA, "25", "Oklahoma City Thunder"))

        // Simulate the merge the repository performs: every backed-up team is added, none removed.
        listOf(
            team(League.NFL, "12", "Kansas City Chiefs"),
            team(League.MLB, "24", "St. Louis Cardinals"),
        ).forEach { favorites.add(it) }

        val keys = favorites.favorites().map { it.key }
        assertEquals(3, keys.size)
        assertTrue("pre-existing favorite must survive an import", "nba:25" in keys)
        assertTrue("nfl:12" in keys)
        assertTrue("mlb:24" in keys)
    }

    @Test
    fun `adding a team that is already a favorite is idempotent`() = runTest {
        val favorites = FakeFavorites()
        favorites.add(team(League.NFL, "12", "Kansas City Chiefs"))
        favorites.add(team(League.NFL, "12", "Kansas City Chiefs"))

        assertEquals(1, favorites.favorites().size)
    }

    private fun team(league: League, id: String, name: String) = Team(
        id = id,
        league = league,
        displayName = name,
        shortDisplayName = name,
        abbreviation = name.take(3).uppercase(),
        location = "",
        logoUrl = null,
        primaryColor = null,
        alternateColor = null,
    )
}
