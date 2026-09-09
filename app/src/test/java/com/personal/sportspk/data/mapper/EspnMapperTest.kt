package com.personal.sportspk.data.mapper

import com.personal.sportspk.data.remote.dto.EspnScoreboardDto
import com.personal.sportspk.data.remote.dto.EspnStandingsDto
import com.personal.sportspk.data.remote.dto.EspnSummaryDto
import com.personal.sportspk.domain.model.GameState
import com.personal.sportspk.domain.model.League
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mapping tests run against REAL captured ESPN responses (src/test/resources), not hand-written
 * fixtures. That is deliberate: the endpoint is undocumented, so the thing worth testing is that
 * we survive its actual shape - including the two different box-score layouts it uses.
 */
class EspnMapperTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }

    @Test
    fun `maps a live, a final, and a scheduled game from a real scoreboard`() {
        val dto = json.decodeFromString(EspnScoreboardDto.serializer(), fixture("espn_mlb_scoreboard.json"))
        val games = EspnMapper.toGames(League.MLB, dto)

        assertEquals(3, games.size)
        assertEquals(
            setOf(GameState.LIVE, GameState.POST, GameState.PRE),
            games.map { it.state }.toSet(),
        )
        games.forEach { game ->
            assertEquals(League.MLB, game.league)
            assertTrue("start time should be parsed", game.startTimeMillis > 0)
            assertTrue("home and away must differ", game.home.team.id != game.away.team.id)
            assertTrue(game.home.isHome)
            assertFalse(game.away.isHome)
        }
    }

    @Test
    fun `a finished game carries scores and a winner`() {
        val dto = json.decodeFromString(EspnScoreboardDto.serializer(), fixture("espn_mlb_scoreboard.json"))
        val final = EspnMapper.toGames(League.MLB, dto).first { it.state == GameState.POST }

        assertNotNull(final.home.score)
        assertNotNull(final.away.score)
        assertTrue(
            "exactly one side should be flagged the winner",
            listOf(final.home.isWinner, final.away.isWinner).count { it == true } == 1,
        )
    }

    @Test
    fun `team keys are namespaced by league so ids never collide across sports`() {
        val dto = json.decodeFromString(EspnScoreboardDto.serializer(), fixture("espn_mlb_scoreboard.json"))
        val game = EspnMapper.toGames(League.MLB, dto).first()

        assertTrue(game.home.team.key.startsWith("mlb:"))
        // ESPN reuses plain numeric ids in every league; the key must disambiguate them.
        assertTrue(game.home.team.key != game.home.team.id)
    }

    @Test
    fun `flat NFL team statistics become one unnamed stat group`() {
        val dto = json.decodeFromString(EspnSummaryDto.serializer(), fixture("espn_nfl_summary.json"))
        val scoreboard = json.decodeFromString(EspnScoreboardDto.serializer(), fixture("espn_mlb_scoreboard.json"))
        val anyGame = EspnMapper.toGames(League.MLB, scoreboard).first()

        val detail = EspnMapper.toGameDetail(League.NFL, anyGame, dto, nowMillis = 1_000L)

        assertEquals(2, detail.boxScore.size)
        val groups = detail.boxScore.first().groups
        assertEquals("NFL returns a flat stat list", 1, groups.size)
        assertNull("a flat list has no group name", groups.first().name)
        assertTrue(groups.first().stats.isNotEmpty())
        // ESPN's flat nodes label stats with "label", not "displayName".
        assertTrue(groups.first().stats.any { it.label == "1st Downs" })
    }

    @Test
    fun `standings flatten nested children into named groups`() {
        val dto = json.decodeFromString(EspnStandingsDto.serializer(), fixture("espn_epl_standings.json"))
        val standings = EspnMapper.toStandings(League.EPL, dto, nowMillis = 42L)

        assertTrue(standings.groups.isNotEmpty())
        assertEquals(42L, standings.lastUpdatedMillis)
        val entries = standings.groups.first().entries
        assertTrue(entries.isNotEmpty())
        entries.forEach { entry ->
            assertEquals(League.EPL, entry.team.league)
            assertNotNull("soccer tables are points-driven", entry.points)
        }
    }

    @Test
    fun `ESPN timestamps without seconds still parse`() {
        // "2026-09-10T00:20Z" is what the scoreboard actually returns; Instant.parse rejects it.
        assertNotNull(EspnMapper.parseIsoMillis("2026-09-10T00:20Z"))
        assertNotNull(EspnMapper.parseIsoMillis("2026-09-10T00:20:30Z"))
    }

    @Test
    fun `garbage timestamps are dropped rather than throwing`() {
        assertNull(EspnMapper.parseIsoMillis(null))
        assertNull(EspnMapper.parseIsoMillis(""))
        assertNull(EspnMapper.parseIsoMillis("not a date"))
    }

    @Test
    fun `an unknown status state is treated as scheduled`() {
        assertEquals(GameState.PRE, EspnMapper.toState(null))
        assertEquals(GameState.PRE, EspnMapper.toState("something-new-espn-invented"))
        assertEquals(GameState.LIVE, EspnMapper.toState("in"))
        assertEquals(GameState.POST, EspnMapper.toState("post"))
    }

    @Test
    fun `an event missing competitors is skipped instead of failing the whole scoreboard`() {
        val payload = """
            { "events": [
                { "id": "1", "date": "2026-09-10T00:20Z", "competitions": [] },
                { "id": "2", "date": "2026-09-10T00:20Z",
                  "competitions": [ { "competitors": [
                     { "homeAway": "home", "score": "3", "team": { "id": "24", "displayName": "St. Louis Cardinals", "abbreviation": "STL" } },
                     { "homeAway": "away", "score": "1", "team": { "id": "26", "displayName": "San Francisco Giants", "abbreviation": "SF" } }
                  ], "status": { "period": 9, "type": { "state": "post", "shortDetail": "Final" } } } ] }
            ] }
        """.trimIndent()
        val dto = json.decodeFromString(EspnScoreboardDto.serializer(), payload)

        val games = EspnMapper.toGames(League.MLB, dto)

        assertEquals("the malformed event is dropped, the good one survives", 1, games.size)
        assertEquals("2", games.first().id)
        assertEquals(3, games.first().home.score)
    }

    @Test
    fun `unknown JSON fields do not break decoding`() {
        val payload = """
            { "somethingNew": true, "events": [
                { "id": "9", "date": "2026-09-10T00:20Z", "brandNewField": {"a": 1},
                  "competitions": [ { "competitors": [
                     { "homeAway": "home", "score": "0", "team": { "id": "1", "displayName": "A", "abbreviation": "A" } },
                     { "homeAway": "away", "score": "0", "team": { "id": "2", "displayName": "B", "abbreviation": "B" } }
                  ], "status": { "type": { "state": "pre" } } } ] }
            ] }
        """.trimIndent()

        val dto = json.decodeFromString(EspnScoreboardDto.serializer(), payload)

        assertEquals(1, EspnMapper.toGames(League.NFL, dto).size)
    }
    /**
     * Regression: ESPN types `broadcasts[].market` as a bare string on /scoreboard but as an
     * object on /summary. Typing it as a String made every summary response fail to decode.
     */
    @Test
    fun `broadcast market decodes whether ESPN sends a string or an object`() {
        fun payload(market: String) = """
            { "events": [
                { "id": "1", "date": "2026-09-10T00:20Z", "competitions": [ {
                  "broadcasts": [ { "market": $market, "names": ["NBC"] } ],
                  "competitors": [
                     { "homeAway": "home", "score": "0", "team": { "id": "1", "displayName": "A", "abbreviation": "A" } },
                     { "homeAway": "away", "score": "0", "team": { "id": "2", "displayName": "B", "abbreviation": "B" } }
                  ], "status": { "type": { "state": "pre" } } } ] }
            ] }
        """.trimIndent()

        listOf("\"national\"", """{ "id": "1", "type": "National" }""").forEach { market ->
            val dto = json.decodeFromString(EspnScoreboardDto.serializer(), payload(market))
            val game = EspnMapper.toGames(League.NFL, dto).single()
            assertEquals("market shape: $market", "NBC", game.broadcast)
        }
    }

    @Test
    fun `an unexpected market shape degrades to no broadcast instead of failing`() {
        val payload = """
            { "events": [
                { "id": "1", "date": "2026-09-10T00:20Z", "competitions": [ {
                  "broadcasts": [ { "market": [1, 2, 3], "names": ["NBC"] } ],
                  "competitors": [
                     { "homeAway": "home", "score": "0", "team": { "id": "1", "displayName": "A", "abbreviation": "A" } },
                     { "homeAway": "away", "score": "0", "team": { "id": "2", "displayName": "B", "abbreviation": "B" } }
                  ], "status": { "type": { "state": "pre" } } } ] }
            ] }
        """.trimIndent()

        val dto = json.decodeFromString(EspnScoreboardDto.serializer(), payload)
        val game = EspnMapper.toGames(League.NFL, dto).single()

        // We still fall back to the first broadcast rather than losing the game entirely.
        assertEquals("NBC", game.broadcast)
    }

}
