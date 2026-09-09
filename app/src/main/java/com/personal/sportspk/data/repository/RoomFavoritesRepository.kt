package com.personal.sportspk.data.repository

import com.personal.sportspk.data.local.DefaultFavorites
import com.personal.sportspk.data.local.FavoriteTeamDao
import com.personal.sportspk.data.local.FavoriteTeamEntity
import com.personal.sportspk.data.local.RefreshStateDao
import com.personal.sportspk.data.local.RefreshStateEntity
import com.personal.sportspk.data.mapper.toDomain
import com.personal.sportspk.domain.model.FavoriteTeam
import com.personal.sportspk.domain.model.League
import com.personal.sportspk.domain.model.Team
import com.personal.sportspk.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The My Teams list, in Room.
 *
 * The seed defaults are written exactly once, guarded by a marker row rather than by "is the
 * table empty?". That distinction matters: a user who deliberately removes every favorite must
 * stay at zero favorites instead of having the defaults silently reappear on next launch, and
 * an APK update must never re-seed over their edits.
 */
internal class RoomFavoritesRepository(
    private val dao: FavoriteTeamDao,
    private val refreshStateDao: RefreshStateDao,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : FavoritesRepository {

    override fun observeFavorites(): Flow<List<FavoriteTeam>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toDomain() } }

    override fun observeFavoriteKeys(): Flow<Set<String>> =
        dao.observeAll().map { rows -> rows.map { it.key }.toSet() }

    override suspend fun favorites(): List<FavoriteTeam> =
        dao.all().mapNotNull { it.toDomain() }

    override suspend fun add(team: Team) {
        val nextOrder = dao.maxSortOrder() + 1
        dao.upsert(
            listOf(
                FavoriteTeamEntity(
                    key = team.key,
                    leagueId = team.league.id,
                    teamId = team.id,
                    displayName = team.displayName,
                    abbreviation = team.abbreviation,
                    logoUrl = team.logoUrl,
                    sortOrder = nextOrder,
                )
            )
        )
    }

    override suspend fun remove(league: League, teamId: String) {
        dao.delete(Team.keyOf(league, teamId))
    }

    override suspend fun setOrder(orderedKeys: List<String>) {
        orderedKeys.forEachIndexed { index, key -> dao.setOrder(key, index) }
    }

    override suspend fun seedDefaultsIfNeverSeeded() {
        if (refreshStateDao.lastRefresh(SEED_MARKER_KEY) != null) return
        // Record the marker even if rows already exist, so this can never run twice.
        if (dao.count() == 0) {
            dao.upsert(
                DefaultFavorites.SEEDS.mapIndexed { index, seed ->
                    FavoriteTeamEntity(
                        key = Team.keyOf(seed.league, seed.teamId),
                        leagueId = seed.league.id,
                        teamId = seed.teamId,
                        displayName = seed.displayName,
                        abbreviation = seed.abbreviation,
                        logoUrl = null,
                        sortOrder = index,
                    )
                }
            )
        }
        refreshStateDao.upsert(RefreshStateEntity(SEED_MARKER_KEY, nowMillis()))
    }

    private companion object {
        const val SEED_MARKER_KEY = "favorites:seeded"
    }
}
