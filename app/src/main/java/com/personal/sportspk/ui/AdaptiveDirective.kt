package com.personal.sportspk.ui

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowSize
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pane directive for the scoreboard's list / detail / standings-context scaffold.
 *
 * Material 3 Adaptive's stock [calculatePaneScaffoldDirective] never allows more than TWO
 * horizontal partitions, so a `ListDetailPaneScaffold`'s extra pane is never shown with it. That
 * would make the third pane dead code, so this widens the directive to three partitions when the
 * window is genuinely wide enough for three readable panes.
 *
 * The threshold is a width, not a device check: it is met by the Fold 8 Ultra's inner display
 * (particularly in landscape) and by a large tablet or desktop window, and it is not met on the
 * cover screen, in portrait phone layouts, or in a narrow split-screen pane - all of which
 * correctly fall back to the stock one- or two-pane behaviour.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberScoreboardPaneDirective(): PaneScaffoldDirective {
    val base = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
    val windowSize = currentWindowSize()
    val density = LocalDensity.current
    val widthDp = with(density) { windowSize.width.toDp() }

    return remember(base, widthDp) { widenForThreePanes(base, widthDp) }
}

/**
 * Pure part of [rememberScoreboardPaneDirective], separated so the width threshold is unit
 * testable without a composition or a device.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun widenForThreePanes(base: PaneScaffoldDirective, widthDp: Dp): PaneScaffoldDirective =
    if (widthDp >= THREE_PANE_MIN_WIDTH && base.maxHorizontalPartitions >= 2) {
        // Only widen a directive that was already showing two panes. A compact window stays
        // single-pane no matter how the width is reported.
        base.copy(maxHorizontalPartitions = 3)
    } else {
        base
    }

/**
 * Three panes at roughly 300dp each plus spacers. Below this the third pane would squeeze the
 * scoreboard list narrower than a game row reads comfortably, so two panes is the better layout.
 */
internal val THREE_PANE_MIN_WIDTH: Dp = 960.dp
