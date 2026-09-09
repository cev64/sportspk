package com.personal.sportspk.domain.model

/** One labelled statistic, e.g. "1st Downs" -> "15". */
data class TeamStat(val label: String, val displayValue: String)

/**
 * A named block of team stats. Some sports (MLB) group them ("Batting", "Pitching");
 * others (NFL) return one flat list, which is mapped to a single unnamed group.
 */
data class StatGroup(val name: String?, val stats: List<TeamStat>)

/** Box score for one team. */
data class TeamBoxScore(
    val team: Team,
    val isHome: Boolean,
    val groups: List<StatGroup>,
)

/** One play-by-play entry, where the league's endpoint provides plays. */
data class PlayEntry(
    val id: String,
    val text: String,
    val periodText: String?,
    val clock: String?,
    val scoreText: String?,
)

/**
 * Everything the detail screen shows. [boxScore] and [plays] are empty rather than null when
 * ESPN does not publish them for that league - a missing box score is a normal state, not an
 * error.
 */
data class GameDetail(
    val game: Game,
    val boxScore: List<TeamBoxScore>,
    val plays: List<PlayEntry>,
    val lastUpdatedMillis: Long,
)
