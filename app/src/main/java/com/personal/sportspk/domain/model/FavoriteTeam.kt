package com.personal.sportspk.domain.model

/**
 * A team on the My Teams list.
 *
 * This is ordinary user data, not application logic: the defaults in
 * [com.personal.sportspk.data.local.DefaultFavorites] are seeded once on first run and are
 * fully editable afterwards. Edits persist in Room and survive APK updates.
 */
data class FavoriteTeam(
    val league: League,
    val teamId: String,
    val displayName: String,
    val abbreviation: String,
    val logoUrl: String?,
    val sortOrder: Int,
) {
    val key: String get() = Team.keyOf(league, teamId)
}
