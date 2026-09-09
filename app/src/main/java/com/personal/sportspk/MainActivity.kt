package com.personal.sportspk

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.sportspk.di.AppContainer
import com.personal.sportspk.data.local.UserSettings
import com.personal.sportspk.ui.SportspkApp
import com.personal.sportspk.ui.navigation.GameDeepLink
import com.personal.sportspk.ui.theme.SportspkTheme

/** Makes the dependency container reachable from composables without threading it through. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided")
}

/**
 * The app's single Activity.
 *
 * Note the manifest declares `configChanges` for size/layout/density changes, so folding and
 * unfolding the device does NOT recreate this Activity - Compose simply re-lays-out against the
 * new window size. State is additionally held in ViewModels and SavedStateHandle, so even a real
 * recreation (Android reclaiming the process, a theme change) preserves navigation and selection.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be installed before super.onCreate() to replace the launch theme.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as SportspkApplication).container

        setContent {
            var deepLink by remember { mutableStateOf(GameDeepLink.parse(intent?.data)) }

            DisposableEffect(Unit) {
                val listener = androidx.core.util.Consumer<Intent> { newIntent ->
                    GameDeepLink.parse(newIntent.data)?.let { deepLink = it }
                }
                addOnNewIntentListener(listener)
                onDispose { removeOnNewIntentListener(listener) }
            }

            val settings by container.preferencesStore.settings
                .collectAsStateWithLifecycle(initialValue = UserSettings())

            CompositionLocalProvider(LocalAppContainer provides container) {
                SportspkTheme(
                    themeMode = settings.themeMode,
                    dynamicColor = settings.dynamicColor,
                ) {
                    SportspkApp(
                        settings = settings,
                        deepLink = deepLink,
                        onDeepLinkConsumed = { deepLink = null },
                    )
                }
            }
        }
    }
}
