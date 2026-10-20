package com.personal.sportspk

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Fetches every favorite team's schedule from ESPN, overlays live scores, picks what to show. */
object GameRepository {
    private const val BASE = "https://site.api.espn.com/apis/site/v2/sports"
    private val ESPN_ZONE: ZoneId = ZoneId.of("America/New_York")
    internal const val USER_AGENT = "Mozilla/5.0 (Linux; Android) Sportspk/2.0"
    private val YMD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** At most this many ESPN requests in flight; firing all of them at once makes some time out. */
    private val requests = Semaphore(4)

    suspend fun fetch(context: Context, previous: List<Game>): List<Game> = withContext(Dispatchers.IO) {
        val games = fetchGames(previous)
        games.flatMap { listOf(it.away.logoUrl, it.home.logoUrl) }.filterNotNull().distinct()
            .map { async { requests.withPermit { LogoCache.ensure(context, it) } } }.awaitAll()
        games
    }

    /**
     * Everything except logos; no Android dependencies, so it can run on the JVM. A team whose
     * schedule can't be fetched keeps its games from [previous] instead of vanishing.
     */
    internal suspend fun fetchGames(previous: List<Game> = emptyList()): List<Game> = withContext(Dispatchers.IO) {
        val results = FAVORITE_TEAMS.map { team -> async { runCatching { teamGames(team) } } }.awaitAll()
        if (results.all { it.isFailure }) throw results.first().exceptionOrNull() ?: IOException()

        val fetched = results.mapNotNull { it.getOrNull() }.flatten()
        val failedIds = FAVORITE_TEAMS.filterIndexed { i, _ -> results[i].isFailure }.map { it.id }.toSet()
        val fetchedIds = fetched.map { it.game.id }.toSet()
        val kept = previous.filter { g ->
            g.id !in fetchedIds && listOf(g.away, g.home).any { it.isFavorite && it.teamId in failedIds }
        }

        val favoriteIds = FAVORITE_TEAMS.map { it.id }.toSet()
        val selected = GameSelector.select(fetched.map { it.game } + kept, favoriteIds)
        withLiveScores(selected, fetched.associate { it.game.id to it.scoreboardPath })
    }

    private class Fetched(val game: Game, val scoreboardPath: String)

    private suspend fun teamGames(team: FavoriteTeam): List<Fetched> {
        val favorites = FAVORITE_TEAMS.filter { it.sport == team.sport }.map { it.id }.toSet()

        if (team.sport == "soccer") {
            // "all" covers every competition (league, cups, Europe). Plain /schedule only lists
            // results; fixture=true lists upcoming games.
            val base = "$BASE/soccer/all/teams/${team.id}/schedule"
            return listOf(get(base), get("$base?fixture=true")).flatMap { json ->
                val leagues = EspnParser.soccerLeagueOf(json)
                EspnParser.parseEvents(json, favorites).map {
                    Fetched(it, "soccer/${leagues[it.id] ?: team.league}")
                }
            }
        }

        val base = "$BASE/${team.sport}/${team.league}/teams/${team.id}/schedule"
        var games = EspnParser.parseEvents(get(base), favorites)
        // Outside the regular season the default can be empty or stale (college hoops reports
        // "preseason" with no events), so also ask for the regular season explicitly.
        val now = System.currentTimeMillis()
        if (games.none { it.state != GameState.FINAL || it.startMillis > now - 86_400_000L }) {
            games = games + EspnParser.parseEvents(get("$base?seasontype=2"), favorites)
        }
        return games.map { Fetched(it, "${team.sport}/${team.league}") }
    }

    /** Schedules are cached harder than scoreboards, so today's games get scoreboard data. */
    private suspend fun withLiveScores(games: List<Game>, pathById: Map<String, String>): List<Game> =
        coroutineScope {
            val now = System.currentTimeMillis()
            val needed = games.filter {
                it.state == GameState.LIVE ||
                    (it.state == GameState.PRE && it.startMillis <= now + 10 * 60_000L) ||
                    (it.state == GameState.FINAL && now - it.startMillis < 12 * 3_600_000L)
            }
            val boards = needed.mapNotNull { g ->
                pathById[g.id]?.let { it to Instant.ofEpochMilli(g.startMillis).atZone(ESPN_ZONE).toLocalDate() }
            }.distinct()

            val fresh = boards.map { (path, date) ->
                async { runCatching { scoreboard(path, date) }.getOrDefault(emptyList()) }
            }.awaitAll().flatten().associateBy { it.id }

            games.map { g ->
                val f = fresh[g.id] ?: return@map g
                g.copy(
                    state = f.state,
                    detail = f.detail,
                    away = g.away.copy(score = f.away.score, winner = f.away.winner),
                    home = g.home.copy(score = f.home.score, winner = f.home.winner),
                )
            }
        }

    private suspend fun scoreboard(path: String, date: LocalDate): List<Game> {
        // College scoreboards default to ranked teams only; groups widens it to the division.
        val groups = when (path) {
            "football/college-football" -> "&groups=80"
            "basketball/mens-college-basketball" -> "&groups=50"
            else -> ""
        }
        val json = get("$BASE/$path/scoreboard?dates=${YMD.format(date)}&limit=500$groups")
        return EspnParser.parseEvents(json, emptySet())
    }

    private suspend fun get(url: String): String = requests.withPermit {
        runCatching { download(url) }.getOrElse {
            delay(1_000)
            download(url)
        }
    }

    private fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        // ESPN's edge rejects Java's default User-Agent; send our own rather than rely on Android's.
        conn.setRequestProperty("User-Agent", USER_AGENT)
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode} for $url")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
