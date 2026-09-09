package com.personal.sportspk

import android.app.Application
import com.personal.sportspk.di.AppContainer
import com.personal.sportspk.di.DefaultAppContainer
import com.personal.sportspk.notifications.GameNotifier
import com.personal.sportspk.workers.ScoreRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SportspkApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)

        // Channels must exist before the first notification; creating them is idempotent.
        GameNotifier(this).ensureChannels()

        applicationScope.launch {
            // Seeds the My Teams defaults on a genuinely first run only. This is a no-op on every
            // later launch and on every app update, so it can never overwrite the user's edits.
            container.favoritesRepository.seedDefaultsIfNeverSeeded()

            if (container.preferencesStore.settings.first().backgroundRefresh) {
                ScoreRefreshScheduler.ensureScheduled(this@SportspkApplication)
            }
        }
    }
}
