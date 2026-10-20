package com.personal.sportspk

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val previous = GameStore.flow(applicationContext).value?.games.orEmpty()
        val games = runCatching { GameRepository.fetch(applicationContext, previous) }.getOrNull()
        if (games != null) {
            GameStore.save(applicationContext, Snapshot(games, System.currentTimeMillis()))
        }
        ScoresWidget().updateAll(applicationContext)

        // Periodic work can't run more often than every 15 minutes, so while a game is on
        // (or about to start) keep a short one-off refresh queued.
        val now = System.currentTimeMillis()
        val busy = GameStore.flow(applicationContext).value?.games.orEmpty().any {
            it.state == GameState.LIVE ||
                (it.state == GameState.PRE && it.startMillis - now in -60 * 60_000L..15 * 60_000L)
        }
        if (busy) scheduleSoon(applicationContext, delayMinutes = 2)

        return if (games != null) Result.success() else Result.retry()
    }

    companion object {
        private const val PERIODIC = "scores-periodic"
        private const val SOON = "scores-soon"

        private val network = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun scheduleSoon(context: Context, delayMinutes: Long = 0) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(SOON, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).run {
                cancelUniqueWork(PERIODIC)
                cancelUniqueWork(SOON)
            }
        }
    }
}
