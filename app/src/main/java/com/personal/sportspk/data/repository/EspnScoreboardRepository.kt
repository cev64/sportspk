package com.personal.sportspk.data.repository

import com.personal.sportspk.data.local.GameDao
import com.personal.sportspk.data.local.RefreshStateDao
import com.personal.sportspk.data.local.RefreshStateEntity
import com.personal.sportspk.data.local.StandingsDao
import com.personal.sportspk.data.local.StandingsEntity
import com.personal.sportspk.data.mapper.EspnMapper
import com.personal.sportspk.data.mapper.toDomain
import com.personal.sportspk.data.mapper.toEntity
import com.personal.sportspk.data.remote.EspnApi
import com.personal.sportspk.data.repository.serialization.StandingsPayload
import com.personal.sportspk.domain.model.Game
import com.personal.sportspk.domain.model.GameDetail
import com.personal.sportspk.domain.model.League
import com.personal.sportspk.domain.model.Standings
import com.personal.sportspk.domain.model.Team
import com.personal.sportspk.domain.repository.RefreshResult
import com.personal.sportspk.domain.repository.ScoreboardRepository
import com.personal.sportspk.domain.usecase.RefreshPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ESPN-backed implementation of [ScoreboardRepository].
 *
 * Reads always come from Room, so screens render instantly from cache and never fail because
 * the network is down. Writes happen only on an explicit refresh. Per-league mutexes stop the
 * UI's refresh and the background worker's refresh from stampeding the same endpoint.
 */
internal class EspnScoreboardRepository(
    private val api: EspnApi,
    private val gameDao: GameDao,
    private val standingsDao: StandingsDao,
    private val refreshStateDao: RefreshStateDao,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ScoreboardRepository {

    private val refreshLocks = League.entries.associateWith { Mutex() }
    private val standingsLocks = League.entries.associateWith { Mutex() }
    private val teamCache = mutableMapOf<League, List<Team>>()
    private val teamCacheLock = Mutex()

    override fun observeScoreboard(league: League): Flow<List<Game>> =
        gameDao.observeByLeague(league.id).map { rows -> rows.mapNotNull { it.toDomain() }.sortedForDisplay() }

    override fun observeAllScoreboards(): Flow<List<Game>> =
        gameDao.observeAll().map { rows -> rows.mapNotNull { it.toDomain() }.sortedForDisplay() }

    override fun observeGamesForTeams(teamKeys: Set<String>): Flow<List<Game>> =
        if (teamKeys.isEmpty()) {
            flowOf(emptyList())
        } else {
            gameDao.observeForTeams(teamKeys).map { rows -> rows.mapNotNull { it.toDomain() }.sortedForDisplay() }
        }

    override fun observeGame(gameId: String): Flow<Game?> =
        gameDao.observeById(gameId).map { it?.toDomain() }

    override fun observeStandings(league: League): Flow<Standings?> =
        standingsDao.observe(league.id).map { entity ->
            entity?.let { StandingsPayload.decode(league, it.payloadJson, it.fetchedAtMillis) }
        }

    override suspend fun teams(league: League): List<Team> = teamCacheLock.withLock {
        teamCache[league]?.let { return it }
        val fetched = runCatching { EspnMapper.toTeams(league, api.teams(league)) }.getOrDefault(emptyList())
        if (fetched.isNotEmpty()) teamCache[league] = fetched
        fetched
    }

    override suspend fun refreshScoreboard(league: League, force: Boolean): RefreshResult {
        val mutex = refreshLocks.getValue(league)
        return mutex.withLock {
            val now = nowMillis()
            if (!force && isFresh(scoreboardKey(league), now, scoreboardTtlMillis(league))) {
                return@withLock RefreshResult.StillFresh
            }
            runCatching { api.scoreboard(league) }
                .mapCatching { dto ->
                    val games = EspnMapper.toGames(league, dto)
                    gameDao.replaceLeague(league.id, games.map { it.toEntity(now) })
                    refreshStateDao.upsert(RefreshStateEntity(scoreboardKey(league), now))
                    games.size
                }
                .fold(
                    onSuccess = { RefreshResult.Updated(it) },
                    onFailure = { RefreshResult.Failed(it) },
                )
        }
    }

    override suspend fun refreshStandings(league: League, force: Boolean): RefreshResult {
        val mutex = standingsLocks.getValue(league)
        return mutex.withLock {
            val now = nowMillis()
            val ttl = RefreshPolicy.STANDINGS_CACHE_TTL.inWholeMilliseconds
            if (!force && isFresh(standingsKey(league), now, ttl)) {
                return@withLock RefreshResult.StillFresh
            }
            runCatching { api.standings(league) }
                .mapCatching { dto ->
                    val standings = EspnMapper.toStandings(league, dto, now)
                    standingsDao.upsert(
                        StandingsEntity(
                            leagueId = league.id,
                            payloadJson = StandingsPayload.encode(standings),
                            fetchedAtMillis = now,
                        )
                    )
                    refreshStateDao.upsert(RefreshStateEntity(standingsKey(league), now))
                    standings.groups.sumOf { it.entries.size }
                }
                .fold(
                    onSuccess = { RefreshResult.Updated(it) },
                    onFailure = { RefreshResult.Failed(it) },
                )
        }
    }

    override suspend fun gameDetail(league: League, gameId: String): Result<GameDetail> {
        val now = nowMillis()
        val cachedGame = gameDao.byId(gameId)?.toDomain()
        return runCatching {
            val summary = api.summary(league, gameId)
            // Prefer the freshest score/status the summary carries; fall back to the cached row.
            val game = summary.header?.competitions?.firstOrNull()
                ?.let { competition ->
                    EspnMapper.toGame(
                        league,
                        com.personal.sportspk.data.remote.dto.EspnEventDto(
                            id = gameId,
                            competitions = listOf(competition),
                            status = competition.status,
                        ),
                    )
                }
                ?: cachedGame
                ?: error("Game $gameId is not available")
            EspnMapper.toGameDetail(league, game, summary, now)
        }
    }

    override suspend fun lastRefreshMillis(league: League): Long? =
        refreshStateDao.lastRefresh(scoreboardKey(league))

    private suspend fun isFresh(key: String, now: Long, ttlMillis: Long): Boolean {
        val last = refreshStateDao.lastRefresh(key) ?: return false
        return now - last < ttlMillis
    }

    /** A league with a live game gets a much shorter TTL than one with nothing going on. */
    private suspend fun scoreboardTtlMillis(league: League): Long {
        val anyLive = gameDao.countLiveInLeague(league.id) > 0
        return RefreshPolicy.cacheTtl(anyLive).inWholeMilliseconds
    }

    private fun scoreboardKey(league: League) = "scoreboard:${league.id}"

    private fun standingsKey(league: League) = "standings:${league.id}"
}

/** Live games first, then games about to start, then finals - the order you actually scan in. */
internal fun List<Game>.sortedForDisplay(): List<Game> = sortedWith(
    compareBy<Game> { game ->
        when (game.state) {
            com.personal.sportspk.domain.model.GameState.LIVE -> 0
            com.personal.sportspk.domain.model.GameState.PRE -> 1
            com.personal.sportspk.domain.model.GameState.POST -> 2
        }
    }.thenBy { it.startTimeMillis }.thenBy { it.id }
)
