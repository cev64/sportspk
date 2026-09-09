package com.personal.sportspk.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A cached game.
 *
 * This table is a CACHE, not user data: it is safe to clear and is rebuilt from the network.
 * It is stored in Room anyway so screens and the widget can render instantly from disk and so
 * background refreshes have somewhere to land.
 */
@Entity(
    tableName = "games",
    indices = [Index("leagueId"), Index("startTimeMillis"), Index("homeTeamKey"), Index("awayTeamKey")],
)
data class GameEntity(
    @PrimaryKey val id: String,
    val leagueId: String,
    val startTimeMillis: Long,
    val state: String,
    val statusDetail: String,
    val period: Int,
    val displayClock: String?,
    val venue: String?,
    val broadcast: String?,
    val note: String?,
    val fetchedAtMillis: Long,

    val homeTeamKey: String,
    val homeTeamId: String,
    val homeName: String,
    val homeShortName: String,
    val homeAbbreviation: String,
    val homeLocation: String,
    val homeLogoUrl: String?,
    val homeColor: String?,
    val homeAltColor: String?,
    val homeScore: Int?,
    val homeWinner: Boolean?,
    val homeRecord: String?,
    val homeRank: Int?,

    val awayTeamKey: String,
    val awayTeamId: String,
    val awayName: String,
    val awayShortName: String,
    val awayAbbreviation: String,
    val awayLocation: String,
    val awayLogoUrl: String?,
    val awayColor: String?,
    val awayAltColor: String?,
    val awayScore: Int?,
    val awayWinner: Boolean?,
    val awayRecord: String?,
    val awayRank: Int?,
)

/**
 * The My Teams list. USER DATA - never cleared by a refresh, a migration, or an app update.
 */
@Entity(tableName = "favorite_teams")
data class FavoriteTeamEntity(
    @PrimaryKey val key: String,
    val leagueId: String,
    val teamId: String,
    val displayName: String,
    val abbreviation: String,
    val logoUrl: String?,
    val sortOrder: Int,
)

/** Cached standings, stored as one row per league with a serialized payload. */
@Entity(tableName = "standings")
data class StandingsEntity(
    @PrimaryKey val leagueId: String,
    val payloadJson: String,
    val fetchedAtMillis: Long,
)

/** When each cacheable resource was last successfully refreshed, for TTL decisions. */
@Entity(tableName = "refresh_state")
data class RefreshStateEntity(
    @PrimaryKey val key: String,
    val lastRefreshMillis: Long,
)

/**
 * Which favorite games we have already notified about, so a game start or final is announced
 * once and not on every background poll.
 */
@Entity(tableName = "notified_events")
data class NotifiedEventEntity(
    @PrimaryKey val key: String,
    val notifiedAtMillis: Long,
)
