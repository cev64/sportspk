package com.personal.sportspk.domain.usecase

import com.personal.sportspk.data.local.DefaultFavorites
import com.personal.sportspk.domain.model.League
import com.personal.sportspk.domain.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LeagueTest {

    /**
     * League ids are persisted in Room, DataStore, backups, and deep links. Renaming one would
     * orphan every stored favorite pointing at it, so the exact set is pinned here: if this test
     * fails, a migration is required, not a fix to the assertion.
     */
    @Test
    fun `league ids are a stable persisted contract`() {
        assertEquals(
            listOf("nfl", "nba", "mlb", "nhl", "epl", "ncaaf", "ncaab"),
            League.entries.map { it.id },
        )
    }

    @Test
    fun `every league maps to an ESPN sport and league path`() {
        League.entries.forEach { league ->
            assertEquals(
                "${league.name} sport path must not be blank",
                true,
                league.sportPath.isNotBlank(),
            )
            assertEquals(
                "${league.name} league path must not be blank",
                true,
                league.leaguePath.isNotBlank(),
            )
        }
        // The two paths together must be unique, or two leagues would share a cache key.
        val paths = League.entries.map { "${it.sportPath}/${it.leaguePath}" }
        assertEquals(paths.size, paths.distinct().size)
    }

    @Test
    fun `fromId round-trips and rejects anything unknown`() {
        League.entries.forEach { assertEquals(it, League.fromId(it.id)) }
        assertNull(League.fromId("quidditch"))
        assertNull(League.fromId(null))
    }

    @Test
    fun `seed favorites cover every requested team and point at real leagues`() {
        val seeds = DefaultFavorites.SEEDS

        assertEquals(10, seeds.size)
        assertEquals(
            listOf(
                "Kansas City Chiefs", "Carolina Panthers", "St. Louis Blues",
                "St. Louis Cardinals", "Oklahoma City Thunder", "Charlotte Hornets",
                "Tottenham Hotspur", "Michigan Wolverines", "Notre Dame Fighting Irish",
                "Creighton Bluejays",
            ),
            seeds.map { it.displayName },
        )
        // Two NFL, two NBA, two NCAAF, one each elsewhere.
        assertEquals(2, seeds.count { it.league == League.NFL })
        assertEquals(2, seeds.count { it.league == League.NBA })
        assertEquals(2, seeds.count { it.league == League.NCAAF })
        assertEquals(1, seeds.count { it.league == League.EPL })
    }

    @Test
    fun `seed team keys are unique`() {
        val keys = DefaultFavorites.SEEDS.map { Team.keyOf(it.league, it.teamId) }
        assertEquals(keys.size, keys.distinct().size)
        assertNotNull(keys.firstOrNull { it == "nfl:12" })
    }
}
