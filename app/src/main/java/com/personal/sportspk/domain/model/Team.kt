package com.personal.sportspk.domain.model

/**
 * A team, in our own shape. ESPN's team JSON is mapped into this in the data layer; no
 * ESPN-specific field ever reaches the domain or UI layers.
 */
data class Team(
    val id: String,
    val league: League,
    val displayName: String,
    val shortDisplayName: String,
    val abbreviation: String,
    val location: String,
    val logoUrl: String?,
    val primaryColor: String?,
    val alternateColor: String?,
) {
    /** Stable key across leagues - ESPN team ids are only unique *within* a league. */
    val key: String get() = "${league.id}:$id"

    companion object {
        fun keyOf(league: League, teamId: String) = "${league.id}:$teamId"
    }
}
