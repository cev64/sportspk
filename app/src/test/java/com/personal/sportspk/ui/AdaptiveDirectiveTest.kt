package com.personal.sportspk.ui

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The stock Material 3 Adaptive directive caps at two horizontal partitions, which would leave the
 * scoreboard's standings-context pane permanently invisible. These tests pin the widening rule so
 * that third pane cannot silently become dead code again.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class AdaptiveDirectiveTest {

    private fun directive(maxHorizontalPartitions: Int) = PaneScaffoldDirective(
        maxHorizontalPartitions = maxHorizontalPartitions,
        horizontalPartitionSpacerSize = 0.dp,
        maxVerticalPartitions = 1,
        verticalPartitionSpacerSize = 0.dp,
        defaultPanePreferredWidth = 360.dp,
        excludedBounds = emptyList(),
    )

    @Test
    fun `a wide window gets a third pane`() {
        val widened = widenForThreePanes(directive(2), THREE_PANE_MIN_WIDTH)

        assertEquals(3, widened.maxHorizontalPartitions)
    }

    @Test
    fun `a window just below the threshold keeps two panes`() {
        val unchanged = widenForThreePanes(directive(2), THREE_PANE_MIN_WIDTH - 1.dp)

        assertEquals(2, unchanged.maxHorizontalPartitions)
    }

    @Test
    fun `a compact single-pane window is never widened, however wide it reports`() {
        // The cover screen and narrow split-screen panes must stay single-pane.
        val unchanged = widenForThreePanes(directive(1), 2000.dp)

        assertEquals(1, unchanged.maxHorizontalPartitions)
    }

    @Test
    fun `widening preserves every other property of the directive`() {
        val base = directive(2)

        val widened = widenForThreePanes(base, 1200.dp)

        assertEquals(base.horizontalPartitionSpacerSize, widened.horizontalPartitionSpacerSize)
        assertEquals(base.maxVerticalPartitions, widened.maxVerticalPartitions)
        assertEquals(base.defaultPanePreferredWidth, widened.defaultPanePreferredWidth)
        assertEquals(base.excludedBounds, widened.excludedBounds)
    }
}
