package com.personal.sportspk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EspnParserTest {
    private fun fixture(name: String) =
        javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun `schedule finals read object scores and winners`() {
        val games = EspnParser.parseEvents(fixture("nfl_kc_schedule.json"), setOf("12"))
        assertEquals(4, games.size)

        val opener = games[0]
        assertEquals("401872931", opener.id)
        assertEquals(GameState.FINAL, opener.state)
        assertEquals("Final", opener.detail)
        assertEquals("KC", opener.home.abbr)
        assertEquals("31", opener.home.score)
        assertTrue(opener.home.winner)
        assertTrue(opener.home.isFavorite)
        assertEquals("DEN", opener.away.abbr)
        assertEquals("10", opener.away.score)
        assertFalse(opener.away.isFavorite)
        assertEquals("https://a.espncdn.com/i/teamlogos/nfl/500/kc.png", opener.home.logoUrl)
        assertTrue(opener.link!!.startsWith("https://www.espn.com/nfl/game/"))
    }

    @Test
    fun `schedule upcoming games have no score and a parsed start time`() {
        val game = EspnParser.parseEvents(fixture("nfl_kc_schedule.json"), setOf("12"))[2]
        assertEquals(GameState.PRE, game.state)
        assertNull(game.away.score)
        assertEquals(EspnParser.parseDate("2026-10-26T00:20Z"), game.startMillis)
        assertEquals("KC", game.away.abbr)
        assertTrue(game.away.isFavorite)
    }

    @Test
    fun `soccer fixtures parse and expose their competition slug`() {
        val json = fixture("soccer_spurs_fixtures.json")
        val games = EspnParser.parseEvents(json, setOf("367"))
        assertEquals(2, games.size)
        assertEquals("TOT", games[0].away.abbr)
        assertTrue(games[0].away.isFavorite)
        assertEquals("eng.1", EspnParser.soccerLeagueOf(json)[games[0].id])
    }

    @Test
    fun `scoreboard string scores and scoreboard logos are normalised`() {
        val games = EspnParser.parseEvents(fixture("nba_scoreboard.json"), emptySet())
        assertEquals(2, games.size)
        val g = games[0]
        assertEquals(GameState.PRE, g.state)
        assertNull(g.home.score) // scoreboards report "0" before tip-off
        assertFalse(g.home.logoUrl!!.contains("/scoreboard/"))
    }

    @Test
    fun `dates without seconds parse`() {
        assertEquals(1_792_546_200_000L, EspnParser.parseDate("2026-10-21T01:30Z"))
    }
}
