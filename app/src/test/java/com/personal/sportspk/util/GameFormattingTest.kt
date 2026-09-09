package com.personal.sportspk.util

import com.personal.sportspk.domain.model.Competitor
import com.personal.sportspk.domain.model.Game
import com.personal.sportspk.domain.model.GameState
import com.personal.sportspk.domain.model.League
import com.personal.sportspk.domain.model.Team
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameFormattingTest {

    private val zone = ZoneId.of("America/Chicago")

    @Test
    fun `period labels follow each sport's own vocabulary`() {
        assertEquals("3rd Quarter", GameFormatting.periodLabel(game(League.NFL, period = 3)))
        assertEquals("2nd Period", GameFormatting.periodLabel(game(League.NHL, period = 2)))
        assertEquals("7th Inning", GameFormatting.periodLabel(game(League.MLB, period = 7)))
        assertEquals("1st Half", GameFormatting.periodLabel(game(League.EPL, period = 1)))
    }

    @Test
    fun `overtime periods collapse to OT rather than a nonsense ordinal`() {
        assertEquals("OT", GameFormatting.periodLabel(game(League.NFL, period = 5)))
        assertEquals("OT", GameFormatting.periodLabel(game(League.NHL, period = 4)))
        // Extra innings are still innings.
        assertEquals("11th Inning", GameFormatting.periodLabel(game(League.MLB, period = 11)))
    }

    @Test
    fun `ordinals handle the teens correctly`() {
        assertEquals("1st", GameFormatting.ordinal(1))
        assertEquals("2nd", GameFormatting.ordinal(2))
        assertEquals("3rd", GameFormatting.ordinal(3))
        assertEquals("4th", GameFormatting.ordinal(4))
        assertEquals("11th", GameFormatting.ordinal(11))
        assertEquals("12th", GameFormatting.ordinal(12))
        assertEquals("13th", GameFormatting.ordinal(13))
        assertEquals("21st", GameFormatting.ordinal(21))
    }

    @Test
    fun `ESPN's own status text is preferred when present`() {
        val g = game(League.MLB, period = 8, statusDetail = "Bot 8th")
        assertEquals("Bot 8th", GameFormatting.statusLine(g, zone))
    }

    @Test
    fun `a blank status falls back to something meaningful per state`() {
        assertEquals("Final", GameFormatting.statusLine(game(League.NFL, state = GameState.POST), zone))
        assertEquals(
            "2nd Quarter",
            GameFormatting.statusLine(game(League.NFL, state = GameState.LIVE, period = 2), zone),
        )
    }

    @Test
    fun `relative update text degrades sensibly`() {
        val now = 1_000_000_000L
        assertNull(GameFormatting.relativeUpdated(null, now))
        assertEquals("Updated just now", GameFormatting.relativeUpdated(now - 5_000, now))
        assertEquals("Updated 1 min ago", GameFormatting.relativeUpdated(now - 60_000, now))
        assertEquals("Updated 5 min ago", GameFormatting.relativeUpdated(now - 300_000, now))
        assertEquals("Updated 1 hour ago", GameFormatting.relativeUpdated(now - 3_700_000, now))
        // A clock that jumped backwards must not produce a negative age.
        assertEquals("Updated just now", GameFormatting.relativeUpdated(now + 10_000, now))
    }

    private fun game(
        league: League,
        state: GameState = GameState.LIVE,
        period: Int = 1,
        statusDetail: String = "",
    ) = Game(
        id = "1",
        league = league,
        startTimeMillis = 1_757_000_000_000L,
        state = state,
        statusDetail = statusDetail,
        period = period,
        displayClock = null,
        home = competitor(league, "1"),
        away = competitor(league, "2"),
        venue = null,
        broadcast = null,
        note = null,
    )

    private fun competitor(league: League, id: String) = Competitor(
        team = Team(id, league, "Team $id", "T$id", "T$id", "", null, null, null),
        score = 0,
        isHome = id == "1",
        isWinner = null,
        record = null,
        rank = null,
    )
}
