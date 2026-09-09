package com.personal.sportspk.domain.model

/** One team's row in a standings table. */
data class StandingEntry(
    val team: Team,
    val wins: Int?,
    val losses: Int?,
    val ties: Int?,
    val winPercent: String?,
    val gamesBehind: String?,
    val points: String?,
    val summary: String?,
    val rank: Int?,
)

/** One standings table - a division, conference, or the whole table for a flat league. */
data class StandingsGroup(
    val name: String,
    val entries: List<StandingEntry>,
)

data class Standings(
    val league: League,
    val groups: List<StandingsGroup>,
    val lastUpdatedMillis: Long,
)
