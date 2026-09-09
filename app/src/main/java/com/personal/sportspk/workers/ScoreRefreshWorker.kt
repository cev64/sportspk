package com.personal.sportspk.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personal.sportspk.SportspkApplication
import com.personal.sportspk.data.local.NotifiedEventEntity
import com.personal.sportspk.domain.model.Game
import com.personal.sportspk.domain.model.GameState
import com.personal.sportspk.domain.model.League
import com.personal.sportspk.domain.repository.RefreshResult
import com.personal.sportspk.notifications.GameNotifier
import com.personal.sportspk.widget.ScoreWidget
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.days

/**
 * Periodic background refresh.
 *
 * Refreshes only the leagues that have a favorite team in them, then raises kickoff/final
 * notifications for favorite games whose state changed since the last run. Leagues the user
 * follows nobody in are left to on-demand refresh when they open that scoreboard - there is no
 * reason to spend battery and requests keeping them warm.
 *
 * Rescheduling is handled by [ScoreRefreshScheduler]: after each run we ask [RefreshPolicy]
 * (via the scheduler) what the next interval should be, so polling tightens during a favorite's
 * game and backs off when nothing is happening.
 */
class ScoreRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? SportspkApplication ?: return Result.success()
        val container = app.container
        val settings = container.preferencesStore.settings.first()
        if (!settings.backgroundRefresh) return Result.success()

        val favorites = container.favoritesRepository.favorites()
        val favoriteKeys = favorites.map { it.key }.toSet()
        val leagues = favorites.map { it.league }.distinct()
        if (leagues.isEmpty()) return Result.success()

        val gamesBefore = container.scoreboardRepository
            .observeGamesForTeams(favoriteKeys).first()
            .associateBy { it.id }

        var anyFailure = false
        leagues.forEach { league ->
            when (container.scoreboardRepository.refreshScoreboard(league)) {
                is RefreshResult.Failed -> anyFailure = true
                else -> Unit
            }
        }

        val gamesAfter = container.scoreboardRepository
            .observeGamesForTeams(favoriteKeys).first()

        notifyStateChanges(app, gamesBefore, gamesAfter, settings.notifyGameStart, settings.notifyFinalScore)

        ScoreWidget.updateAll(applicationContext)
        ScoreRefreshScheduler.rescheduleFor(applicationContext, gamesAfter, favoriteKeys)

        // A failed poll is normal (offline, ESPN hiccup) and is NOT retried: an immediate retry
        // would hammer an endpoint with no published rate limit, and the cached scores stay
        // readable regardless. The next scheduled run picks it up.
        if (anyFailure) Log.i(TAG, "One or more leagues failed to refresh; will retry on the next run")
        return Result.success()
    }

    private suspend fun notifyStateChanges(
        app: SportspkApplication,
        before: Map<String, Game>,
        after: List<Game>,
        notifyStart: Boolean,
        notifyFinal: Boolean,
    ) {
        val notifier = GameNotifier(applicationContext)
        if (!notifier.canPostNotifications()) return
        val notifiedDao = app.container.database.notifiedEventDao()
        val now = System.currentTimeMillis()

        notifiedDao.deleteOlderThan(now - RETENTION.inWholeMilliseconds)

        after.forEach { game ->
            val previousState = before[game.id]?.state
            when {
                notifyStart && game.state == GameState.LIVE && previousState != GameState.LIVE -> {
                    val key = "start:${game.id}"
                    if (!notifiedDao.wasNotified(key)) {
                        notifier.notifyGameStarted(game)
                        notifiedDao.markNotified(NotifiedEventEntity(key, now))
                    }
                }

                notifyFinal && game.state == GameState.POST && previousState != GameState.POST -> {
                    val key = "final:${game.id}"
                    if (!notifiedDao.wasNotified(key)) {
                        notifier.notifyFinalScore(game)
                        notifiedDao.markNotified(NotifiedEventEntity(key, now))
                    }
                }
            }
        }
    }

    companion object {
        const val WORK_NAME = "score-refresh"
        private const val TAG = "ScoreRefreshWorker"
        private val RETENTION = 14.days

        /** Leagues with at least one favorite. Exposed for tests. */
        fun leaguesToRefresh(favoriteLeagues: List<League>): List<League> = favoriteLeagues.distinct()
    }
}
