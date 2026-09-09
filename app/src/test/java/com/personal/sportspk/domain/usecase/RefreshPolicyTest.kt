package com.personal.sportspk.domain.usecase

import com.personal.sportspk.domain.model.Competitor
import com.personal.sportspk.domain.model.Game
import com.personal.sportspk.domain.model.GameState
import com.personal.sportspk.domain.model.League
import com.personal.sportspk.domain.model.Team
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/**
 * The polling policy is the main lever on battery and on request volume against an endpoint with
 * no published rate limit, so it is tested directly rather than only through the worker.
 */
class RefreshPolicyTest {

    private val now = 1_757_000_000_000L

    @Test
    fun `a live favorite game gets the fast interval`() {
        val games = listOf(
            game("1", League.NFL, GameState.LIVE, homeId = "12"),
            game("2", League.NBA, GameState.PRE, startOffset = 6.hoursMillis),
        )

        val interval = RefreshPolicy.intervalFor(games, favorites("nfl:12"), now)

        assertEquals(RefreshPolicy.LIVE_FAVORITE, interval)
    }

    @Test
    fun `a live game we do not follow only gets the slow live interval`() {
        val games = listOf(game("1", League.NFL, GameState.LIVE, homeId = "99"))

        val interval = RefreshPolicy.intervalFor(games, favorites("nfl:12"), now)

        assertEquals(RefreshPolicy.LIVE_OTHER, interval)
    }

    @Test
    fun `a favorite starting soon tightens the interval so kickoff alerts land on time`() {
        val games = listOf(
            game("1", League.NFL, GameState.PRE, homeId = "12", startOffset = 5.minutes.inWholeMilliseconds)
        )

        val interval = RefreshPolicy.intervalFor(games, favorites("nfl:12"), now)

        assertEquals(RefreshPolicy.FAVORITE_STARTING_SOON, interval)
    }

    @Test
    fun `a favorite starting tomorrow does not tighten the interval`() {
        val games = listOf(
            game("1", League.NFL, GameState.PRE, homeId = "12", startOffset = 20.hoursMillis)
        )

        val interval = RefreshPolicy.intervalFor(games, favorites("nfl:12"), now)

        assertEquals(RefreshPolicy.IDLE, interval)
    }

    @Test
    fun `nothing happening backs all the way off`() {
        val games = listOf(game("1", League.NFL, GameState.POST, homeId = "12"))

        assertEquals(RefreshPolicy.IDLE, RefreshPolicy.intervalFor(games, favorites("nfl:12"), now))
        assertEquals(RefreshPolicy.IDLE, RefreshPolicy.intervalFor(emptyList(), emptySet(), now))
    }

    @Test
    fun `a live favorite wins over a live non-favorite regardless of list order`() {
        val games = listOf(
            game("1", League.NBA, GameState.LIVE, homeId = "99"),
            game("2", League.NFL, GameState.LIVE, homeId = "12"),
        )

        assertEquals(
            RefreshPolicy.LIVE_FAVORITE,
            RefreshPolicy.intervalFor(games, favorites("nfl:12"), now),
        )
    }

    @Test
    fun `a favorite playing away is matched too, not just at home`() {
        val games = listOf(game("1", League.NFL, GameState.LIVE, homeId = "99", awayId = "12"))

        assertEquals(
            RefreshPolicy.LIVE_FAVORITE,
            RefreshPolicy.intervalFor(games, favorites("nfl:12"), now),
        )
    }

    @Test
    fun `cache TTL is shorter while something is live`() {
        assertEquals(RefreshPolicy.LIVE_CACHE_TTL, RefreshPolicy.cacheTtl(anyLive = true))
        assertEquals(RefreshPolicy.IDLE_CACHE_TTL, RefreshPolicy.cacheTtl(anyLive = false))
    }

    private val Int.hoursMillis: Long get() = this * 60L * 60L * 1000L

    private fun favorites(vararg keys: String) = keys.toSet()

    private fun game(
        id: String,
        league: League,
        state: GameState,
        homeId: String = "1",
        awayId: String = "2",
        startOffset: Long = 0L,
    ) = Game(
        id = id,
        league = league,
        startTimeMillis = now + startOffset,
        state = state,
        statusDetail = state.name,
        period = 1,
        displayClock = null,
        home = competitor(league, homeId, isHome = true),
        away = competitor(league, awayId, isHome = false),
        venue = null,
        broadcast = null,
        note = null,
    )

    private fun competitor(league: League, teamId: String, isHome: Boolean) = Competitor(
        team = Team(
            id = teamId,
            league = league,
            displayName = "Team $teamId",
            shortDisplayName = "T$teamId",
            abbreviation = "T$teamId",
            location = "",
            logoUrl = null,
            primaryColor = null,
            alternateColor = null,
        ),
        score = 0,
        isHome = isHome,
        isWinner = null,
        record = null,
        rank = null,
    )
}
