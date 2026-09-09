package com.personal.sportspk.domain.repository

import com.personal.sportspk.domain.model.Game
import com.personal.sportspk.domain.model.GameDetail
import com.personal.sportspk.domain.model.League
import com.personal.sportspk.domain.model.Standings
import com.personal.sportspk.domain.model.Team
import kotlinx.coroutines.flow.Flow

/**
 * The app's only door to score data.
 *
 * ESPN is the current provider, but nothing above the data layer knows that: the UI and
 * domain layers talk to this interface in terms of our own models. A second provider can be
 * added by writing another implementation, without touching a single screen.
 *
 * Reads ([observeScoreboard], [observeGame], ...) are served from the Room cache and never
 * block on the network. Refreshes are explicit and return a [RefreshResult] so callers can
 * surface staleness without the read path ever failing.
 */
interface ScoreboardRepository {

    /** Cached games for one league, today. Emits again whenever the cache is refreshed. */
    fun observeScoreboard(league: League): Flow<List<Game>>

    /** Cached games across every league, today. */
    fun observeAllScoreboards(): Flow<List<Game>>

    /** Cached games involving any of the given team keys ([Team.key]). */
    fun observeGamesForTeams(teamKeys: Set<String>): Flow<List<Game>>

    /** One cached game, or null if it is not in the cache. */
    fun observeGame(gameId: String): Flow<Game?>

    /** Cached standings for a league. */
    fun observeStandings(league: League): Flow<Standings?>

    /** Every team we know about in a league, for the favorites picker. */
    suspend fun teams(league: League): List<Team>

    /** Fetches the league's scoreboard and writes it to the cache. */
    suspend fun refreshScoreboard(league: League, force: Boolean = false): RefreshResult

    /** Fetches standings and writes them to the cache. */
    suspend fun refreshStandings(league: League, force: Boolean = false): RefreshResult

    /**
     * Fetches box score / play-by-play for one game. Detail is comparatively expensive and is
     * not cached across sessions, so this returns the value directly.
     */
    suspend fun gameDetail(league: League, gameId: String): Result<GameDetail>

    /** When the given league's scoreboard was last successfully written, or null. */
    suspend fun lastRefreshMillis(league: League): Long?
}

/** Outcome of a refresh. The cache is authoritative for reads either way. */
sealed interface RefreshResult {
    /** Network fetch succeeded and the cache was updated. */
    data class Updated(val gameCount: Int) : RefreshResult

    /** Cached data was still inside its TTL, so no request was made. */
    data object StillFresh : RefreshResult

    /** Fetch failed. Cached data (possibly stale) remains readable. */
    data class Failed(val error: Throwable) : RefreshResult
}
