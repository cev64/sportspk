package com.personal.sportspk.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.personal.sportspk.domain.model.Game
import com.personal.sportspk.domain.usecase.RefreshPolicy
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Schedules [ScoreRefreshWorker].
 *
 * WorkManager's minimum periodic interval is 15 minutes, which is coarser than the ~25s we want
 * while a favorite team is playing. The app therefore refreshes on a fast in-app loop while a
 * scoreboard is on screen (see the scoreboard ViewModel), and WorkManager provides the reliable
 * background floor that produces "game started"/"final" notifications when the app is closed.
 *
 * Per the spec, a foreground service is NOT used. It would be the next step only if testing
 * shows WorkManager's timing is too imprecise for kickoff alerts in practice - documented in
 * PRODUCT_SPEC.md under "Background tasks".
 */
object ScoreRefreshScheduler {

    /** WorkManager will not accept anything shorter than this for periodic work. */
    private val MIN_PERIODIC: Duration = 15.minutes

    fun ensureScheduled(context: Context) {
        enqueue(context, MIN_PERIODIC, ExistingPeriodicWorkPolicy.KEEP)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(ScoreRefreshWorker.WORK_NAME)
    }

    /**
     * Re-enqueues with an interval derived from what is actually happening. Because WorkManager
     * clamps to 15 minutes, this mostly matters for backing OFF - it stops us waking at the
     * floor rate all night when nothing is live.
     */
    fun rescheduleFor(context: Context, games: List<Game>, favoriteKeys: Set<String>) {
        val desired = RefreshPolicy.intervalFor(games, favoriteKeys, System.currentTimeMillis())
        val interval = maxOf(desired, MIN_PERIODIC)
        enqueue(context, interval, ExistingPeriodicWorkPolicy.UPDATE)
    }

    private fun enqueue(context: Context, interval: Duration, policy: ExistingPeriodicWorkPolicy) {
        val request = PeriodicWorkRequestBuilder<ScoreRefreshWorker>(
            interval.inWholeMinutes,
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(ScoreRefreshWorker.WORK_NAME, policy, request)
    }
}
