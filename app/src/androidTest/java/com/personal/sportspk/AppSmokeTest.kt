package com.personal.sportspk

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the app actually launches and composes on a device - the "it runs" half of the initial
 * milestone. Deeper UI assertions belong in per-screen tests; this one exists so a crash on
 * startup can never reach an APK build.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesAndShowsNavigation() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTextExists("My Teams")
    }
}

private fun androidx.compose.ui.test.junit4.ComposeTestRule.onNodeWithTextExists(text: String) {
    onAllNodes(androidx.compose.ui.test.hasText(text, substring = true))
        .fetchSemanticsNodes()
        .also { check(it.isNotEmpty()) { "expected a node containing \"$text\"" } }
}
