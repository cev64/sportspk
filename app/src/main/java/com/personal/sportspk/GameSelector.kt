package com.personal.sportspk

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Decides which games the widget shows. */
object GameSelector {
    /** Every game in this window is shown. */
    private const val UPCOMING_DAYS = 7L

    /** A team with nothing this week still gets its next game, if it's within this window. */
    private const val NEXT_GAME_DAYS = 30L

    fun select(
        games: List<Game>,
        favoriteIds: Set<String>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Game> {
        val today: LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        fun dateOf(g: Game) = Instant.ofEpochMilli(g.startMillis).atZone(zone).toLocalDate()

        // The same game can come from two teams' schedules (Thunder vs Hornets).
        val unique = games.groupBy { it.id }.values.map { dupes ->
            dupes.reduce { a, b ->
                a.copy(
                    away = a.away.copy(isFavorite = a.away.isFavorite || b.away.isFavorite),
                    home = a.home.copy(isFavorite = a.home.isFavorite || b.home.isFavorite),
                )
            }
        }

        val shown = unique.filter { g ->
            val d = dateOf(g)
            g.state == GameState.LIVE ||
                d == today ||
                (g.state == GameState.PRE && d.isAfter(today) && !d.isAfter(today.plusDays(UPCOMING_DAYS)))
        }.toMutableSet()

        for (teamId in favoriteIds) {
            if (shown.any { it.involves(teamId) }) continue
            unique.filter {
                it.state == GameState.PRE && it.involves(teamId) && dateOf(it).isAfter(today) &&
                    !dateOf(it).isAfter(today.plusDays(NEXT_GAME_DAYS))
            }.minByOrNull { it.startMillis }?.let { shown += it }
        }

        return shown.sortedBy { it.startMillis }
    }

    private fun Game.involves(teamId: String) =
        (away.isFavorite && away.teamId == teamId) || (home.isFavorite && home.teamId == teamId)
}
