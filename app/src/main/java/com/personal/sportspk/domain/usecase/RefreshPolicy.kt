package com.personal.sportspk.domain.usecase

import com.personal.sportspk.domain.model.Game
import com.personal.sportspk.domain.model.GameState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * How often to poll ESPN.
 *
 * This is a single-user personal app hitting an undocumented endpoint with no published rate
 * limits, so the policy deliberately favours conservative request volume over maximum
 * freshness. We poll fast only while a favorite team is actually playing.
 */
object RefreshPolicy {

    /** A favorite team is playing right now. */
    val LIVE_FAVORITE: Duration = 25.seconds

    /** Something is live, but nothing we follow. */
    val LIVE_OTHER: Duration = 2.minutes

    /** A favorite game starts soon - tighten up so "game just started" alerts land on time. */
    val FAVORITE_STARTING_SOON: Duration = 1.minutes

    /** Nothing happening. */
    val IDLE: Duration = 15.minutes

    /** How far ahead of a start time counts as "starting soon". */
    val STARTING_SOON_WINDOW: Duration = 15.minutes

    /**
     * How long a cached scoreboard is considered fresh enough to skip a network call.
     * Slightly under [LIVE_FAVORITE] so a manual pull-to-refresh during a live game is never
     * swallowed by the TTL.
     */
    val LIVE_CACHE_TTL: Duration = 20.seconds
    val IDLE_CACHE_TTL: Duration = 5.minutes

    /** Standings move at most once per game, so they get a much longer TTL. */
    val STANDINGS_CACHE_TTL: Duration = 1.minutes * 60

    /**
     * Picks the polling interval for the next background check.
     *
     * @param games every game currently known across all leagues
     * @param favoriteKeys [com.personal.sportspk.domain.model.Team.key] values on My Teams
     * @param nowMillis current wall clock
     */
    fun intervalFor(
        games: List<Game>,
        favoriteKeys: Set<String>,
        nowMillis: Long,
    ): Duration {
        var anyLive = false
        var favoriteStartingSoon = false
        val soonWindowEnd = nowMillis + STARTING_SOON_WINDOW.inWholeMilliseconds

        for (game in games) {
            val isFavorite = game.involves(favoriteKeys)
            if (game.state.isLive) {
                if (isFavorite) return LIVE_FAVORITE
                anyLive = true
            } else if (
                isFavorite &&
                game.state == GameState.PRE &&
                game.startTimeMillis in nowMillis..soonWindowEnd
            ) {
                // Only a game that has not started yet can be "starting soon" - a final whose
                // start time happens to fall in the window must not hold us at a fast interval.
                favoriteStartingSoon = true
            }
        }

        return when {
            favoriteStartingSoon -> FAVORITE_STARTING_SOON
            anyLive -> LIVE_OTHER
            else -> IDLE
        }
    }

    /** TTL to apply to a cached scoreboard, given whether anything in it is live. */
    fun cacheTtl(anyLive: Boolean): Duration = if (anyLive) LIVE_CACHE_TTL else IDLE_CACHE_TTL
}
