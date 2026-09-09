package com.personal.sportspk.domain.model

/** Where a game is in its lifecycle. Derived from ESPN's status.type.state. */
enum class GameState {
    /** Scheduled, not yet started. */
    PRE,

    /** In progress (including halftime and delays). */
    LIVE,

    /** Final, postponed, or canceled - anything ESPN reports as finished. */
    POST,
    ;

    val isLive: Boolean get() = this == LIVE
}

/** One side of a game. */
data class Competitor(
    val team: Team,
    val score: Int?,
    val isHome: Boolean,
    val isWinner: Boolean?,
    val record: String?,
    val rank: Int?,
)

/**
 * A single game. This is the core domain model the whole app renders from - the league
 * scoreboards, My Teams, the widget, and notifications all read this same type.
 */
data class Game(
    val id: String,
    val league: League,
    val startTimeMillis: Long,
    val state: GameState,
    /** e.g. "Bot 8th", "Final", "9/9 - 8:20 PM EDT". ESPN's own short status text. */
    val statusDetail: String,
    val period: Int,
    val displayClock: String?,
    val home: Competitor,
    val away: Competitor,
    val venue: String?,
    val broadcast: String?,
    val note: String?,
) {
    val name: String get() = "${away.team.shortDisplayName} @ ${home.team.shortDisplayName}"

    fun involves(teamKeys: Set<String>): Boolean =
        home.team.key in teamKeys || away.team.key in teamKeys
}
