package com.personal.sportspk.util

import com.personal.sportspk.domain.model.Game
import com.personal.sportspk.domain.model.GameState
import com.personal.sportspk.domain.model.PeriodLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Presentation helpers for games.
 *
 * ESPN's own `shortDetail` is used verbatim wherever it is good enough (it already says "Bot 8th"
 * or "Final/OT"), because re-deriving that from period numbers per sport would be strictly worse.
 * These helpers cover the cases where we need our own phrasing.
 */
object GameFormatting {

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val dayFormatter = DateTimeFormatter.ofPattern("EEE M/d", Locale.US)

    /** Local start time, e.g. "8:20 PM". */
    fun startTime(game: Game, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(game.startTimeMillis).atZone(zoneId).format(timeFormatter)

    /** Local start day, e.g. "Wed 9/10". */
    fun startDay(game: Game, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(game.startTimeMillis).atZone(zoneId).format(dayFormatter)

    /**
     * The one-line status shown on a row: ESPN's short detail when it has one, otherwise a
     * status derived from state and period.
     */
    fun statusLine(game: Game, zoneId: ZoneId = ZoneId.systemDefault()): String {
        game.statusDetail.takeIf { it.isNotBlank() }?.let { return it }
        return when (game.state) {
            GameState.PRE -> startTime(game, zoneId)
            GameState.LIVE -> periodLabel(game)
            GameState.POST -> "Final"
        }
    }

    /** Sport-appropriate name for the current chunk of game time. */
    fun periodLabel(game: Game): String {
        if (game.period <= 0) return "In progress"
        val ordinal = ordinal(game.period)
        return when (game.league.periodLabel) {
            PeriodLabel.QUARTER -> if (game.period > 4) "OT" else "$ordinal Quarter"
            PeriodLabel.PERIOD -> if (game.period > 3) "OT" else "$ordinal Period"
            PeriodLabel.INNING -> "$ordinal Inning"
            PeriodLabel.HALF -> if (game.period > 2) "OT" else "$ordinal Half"
        }
    }

    fun ordinal(value: Int): String {
        val suffix = when {
            value % 100 in 11..13 -> "th"
            value % 10 == 1 -> "st"
            value % 10 == 2 -> "nd"
            value % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$value$suffix"
    }

    /** "Updated 2 min ago" style staleness label, or null when we have never refreshed. */
    fun relativeUpdated(lastUpdatedMillis: Long?, nowMillis: Long): String? {
        if (lastUpdatedMillis == null || lastUpdatedMillis <= 0L) return null
        val deltaSeconds = ((nowMillis - lastUpdatedMillis) / 1000).coerceAtLeast(0)
        return when {
            deltaSeconds < 30 -> "Updated just now"
            deltaSeconds < 90 -> "Updated 1 min ago"
            deltaSeconds < 3600 -> "Updated ${deltaSeconds / 60} min ago"
            deltaSeconds < 7200 -> "Updated 1 hour ago"
            else -> "Updated ${deltaSeconds / 3600} hours ago"
        }
    }
}
