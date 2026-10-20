package com.personal.sportspk

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class GameSelectorTest {
    private val zone = ZoneId.of("America/Chicago")
    private val now = ZonedDateTime.of(2026, 10, 20, 12, 0, 0, 0, zone)

    private fun game(
        id: String,
        daysFromNow: Long,
        hour: Int,
        state: GameState = GameState.PRE,
        fav: String = "25",
        opp: String = "99",
    ) = Game(
        id = id,
        startMillis = now.plusDays(daysFromNow).withHour(hour).toInstant().toEpochMilli(),
        state = state,
        detail = "",
        away = Side(opp, "OPP", null, null, false, false),
        home = Side(fav, "FAV", null, null, false, true),
        link = null,
    )

    private fun select(vararg games: Game, favorites: Set<String> = setOf("25", "30", "12")) =
        GameSelector.select(games.toList(), favorites, now.toInstant().toEpochMilli(), zone).map { it.id }

    @Test
    fun `shows today and the coming week, sorted, but not past days`() {
        assertEquals(
            listOf("today-final", "today-late", "in-6"),
            select(
                game("in-6", 6, 19),
                game("today-late", 0, 20),
                game("today-final", 0, 9, GameState.FINAL),
                game("yesterday", -1, 19, GameState.FINAL),
            ),
        )
    }

    @Test
    fun `a team with nothing this week still shows its next game within a month`() {
        assertEquals(
            listOf("kc-next"),
            select(game("kc-next", 12, 15, fav = "12"), game("kc-later", 19, 15, fav = "12")),
        )
    }

    @Test
    fun `teams without games inside a month are left out`() {
        assertEquals(emptyList<String>(), select(game("far", 60, 15, fav = "24")))
    }

    @Test
    fun `a game between two favorites appears once with both marked`() {
        val a = game("okc-cha", 1, 19, fav = "25", opp = "30")
        val b = a.copy(
            away = a.away.copy(isFavorite = true),
            home = a.home.copy(isFavorite = false),
        )
        val result = GameSelector.select(listOf(a, b), setOf("25", "30"), now.toInstant().toEpochMilli(), zone)
        assertEquals(1, result.size)
        assertEquals(true, result[0].away.isFavorite && result[0].home.isFavorite)
    }

    @Test
    fun `a live game past midnight still shows`() {
        assertEquals(listOf("late"), select(game("late", -1, 23, GameState.LIVE)))
    }
}
