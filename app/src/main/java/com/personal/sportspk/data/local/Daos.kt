package com.personal.sportspk.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games WHERE leagueId = :leagueId ORDER BY startTimeMillis ASC")
    fun observeByLeague(leagueId: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games ORDER BY startTimeMillis ASC")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE homeTeamKey IN (:teamKeys) OR awayTeamKey IN (:teamKeys) ORDER BY startTimeMillis ASC")
    fun observeForTeams(teamKeys: Collection<String>): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<GameEntity?>

    @Query("SELECT * FROM games WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): GameEntity?

    @Query("SELECT * FROM games")
    suspend fun all(): List<GameEntity>

    @Query("SELECT COUNT(*) FROM games WHERE leagueId = :leagueId AND state = 'LIVE'")
    suspend fun countLiveInLeague(leagueId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(games: List<GameEntity>)

    @Query("DELETE FROM games WHERE leagueId = :leagueId AND id NOT IN (:keepIds)")
    suspend fun deleteStaleInLeague(leagueId: String, keepIds: List<String>)

    @Query("DELETE FROM games WHERE leagueId = :leagueId")
    suspend fun deleteLeague(leagueId: String)

    /**
     * Replaces a league's cached scoreboard atomically, so a screen never observes a moment
     * where the old games are gone and the new ones have not landed.
     */
    @Transaction
    suspend fun replaceLeague(leagueId: String, games: List<GameEntity>) {
        if (games.isEmpty()) {
            deleteLeague(leagueId)
            return
        }
        upsert(games)
        deleteStaleInLeague(leagueId, games.map { it.id })
    }
}

@Dao
interface FavoriteTeamDao {
    @Query("SELECT * FROM favorite_teams ORDER BY sortOrder ASC, displayName ASC")
    fun observeAll(): Flow<List<FavoriteTeamEntity>>

    @Query("SELECT * FROM favorite_teams ORDER BY sortOrder ASC, displayName ASC")
    suspend fun all(): List<FavoriteTeamEntity>

    @Query("SELECT COUNT(*) FROM favorite_teams")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(teams: List<FavoriteTeamEntity>)

    @Query("DELETE FROM favorite_teams WHERE key = :key")
    suspend fun delete(key: String)

    @Query("UPDATE favorite_teams SET sortOrder = :order WHERE key = :key")
    suspend fun setOrder(key: String, order: Int)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM favorite_teams")
    suspend fun maxSortOrder(): Int
}

@Dao
interface StandingsDao {
    @Query("SELECT * FROM standings WHERE leagueId = :leagueId LIMIT 1")
    fun observe(leagueId: String): Flow<StandingsEntity?>

    @Query("SELECT * FROM standings WHERE leagueId = :leagueId LIMIT 1")
    suspend fun get(leagueId: String): StandingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StandingsEntity)
}

@Dao
interface RefreshStateDao {
    @Query("SELECT lastRefreshMillis FROM refresh_state WHERE key = :key LIMIT 1")
    suspend fun lastRefresh(key: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RefreshStateEntity)
}

@Dao
interface NotifiedEventDao {
    @Query("SELECT EXISTS(SELECT 1 FROM notified_events WHERE key = :key)")
    suspend fun wasNotified(key: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markNotified(entity: NotifiedEventEntity)

    /** Housekeeping so this table cannot grow without bound over seasons. */
    @Query("DELETE FROM notified_events WHERE notifiedAtMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}
