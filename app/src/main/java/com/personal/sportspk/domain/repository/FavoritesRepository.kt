package com.personal.sportspk.domain.repository

import com.personal.sportspk.domain.model.FavoriteTeam
import com.personal.sportspk.domain.model.League
import com.personal.sportspk.domain.model.Team
import kotlinx.coroutines.flow.Flow

/** The My Teams list. Ordinary user data - see [FavoriteTeam]. */
interface FavoritesRepository {
    fun observeFavorites(): Flow<List<FavoriteTeam>>

    /** [Team.key] values, for cheap membership checks while filtering games. */
    fun observeFavoriteKeys(): Flow<Set<String>>

    suspend fun favorites(): List<FavoriteTeam>

    suspend fun add(team: Team)

    suspend fun remove(league: League, teamId: String)

    suspend fun setOrder(orderedKeys: List<String>)

    /**
     * Writes the seed defaults, but only if the user has never had a favorites list.
     * Called once at startup; a user who has deliberately emptied their list keeps it empty.
     */
    suspend fun seedDefaultsIfNeverSeeded()
}
