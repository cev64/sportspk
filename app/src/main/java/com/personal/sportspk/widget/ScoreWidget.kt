package com.personal.sportspk.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.personal.sportspk.MainActivity
import com.personal.sportspk.SportspkApplication
import com.personal.sportspk.domain.model.Game
import com.personal.sportspk.domain.model.GameState
import com.personal.sportspk.ui.navigation.GameDeepLink
import com.personal.sportspk.util.GameFormatting
import kotlinx.coroutines.flow.first

/**
 * Home-screen widget for favorite-team scores.
 *
 * Reads the SAME Room cache the app reads - it does not keep its own copy of anything, and it
 * does not hit the network itself. It is refreshed by [com.personal.sportspk.workers.ScoreRefreshWorker]
 * after each background poll, so the widget and the app can never disagree about a score.
 *
 * Sizes:
 *   small  - the single most relevant game (live first, else next up, else most recent final)
 *   larger - the full favorite-teams feed
 */
class ScoreWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val games = loadGames(context)

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                val compact = size.height < COMPACT_HEIGHT_THRESHOLD

                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.widgetBackground)
                        .padding(10.dp)
                        .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                ) {
                    if (games.isEmpty()) {
                        Text(
                            text = "No games for your teams",
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                        )
                    } else if (compact) {
                        GameLine(context, games.first(), showLeague = true)
                    } else {
                        Text(
                            text = "My Teams",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                        Spacer(GlanceModifier.height(6.dp))
                        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                            items(games, itemId = { it.id.hashCode().toLong() }) { game ->
                                GameLine(context, game, showLeague = true)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Favorite games, most relevant first: live, then upcoming, then finals.
     * Reuses the app's own ordering so the widget's "most relevant" and the app's top row agree.
     */
    private suspend fun loadGames(context: Context): List<Game> {
        val app = context.applicationContext as? SportspkApplication ?: return emptyList()
        val keys = app.container.favoritesRepository.favorites().map { it.key }.toSet()
        if (keys.isEmpty()) return emptyList()
        return app.container.scoreboardRepository.observeGamesForTeams(keys).first().take(MAX_ROWS)
    }

    companion object {
        private val COMPACT_HEIGHT_THRESHOLD = 110.dp
        private const val MAX_ROWS = 12

        /** Called after every background refresh so the widget never shows a stale score. */
        suspend fun updateAll(context: Context) {
            runCatching { ScoreWidget().updateAll(context) }
        }
    }
}

@Composable
private fun GameLine(context: Context, game: Game, showLeague: Boolean) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            // Tapping one game deep-links to that game's detail rather than just opening the app.
            .clickable(actionStartActivity(gameIntent(context, game))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = "${game.away.team.abbreviation} ${game.away.score ?: ""}".trim(),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = if (game.away.isWinner == true) FontWeight.Bold else FontWeight.Normal,
                ),
                maxLines = 1,
            )
            Text(
                text = "${game.home.team.abbreviation} ${game.home.score ?: ""}".trim(),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = if (game.home.isWinner == true) FontWeight.Bold else FontWeight.Normal,
                ),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.width(6.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (showLeague) {
                Text(
                    text = game.league.shortName,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 1,
                )
            }
            Text(
                text = statusFor(game),
                style = TextStyle(
                    color = if (game.state == GameState.LIVE) {
                        GlanceTheme.colors.primary
                    } else {
                        GlanceTheme.colors.onSurfaceVariant
                    },
                ),
                maxLines = 1,
            )
        }
    }
}

/** An explicit VIEW intent at sportspk://game/{league}/{id}, handled by MainActivity. */
private fun gameIntent(context: Context, game: Game): Intent =
    Intent(Intent.ACTION_VIEW, GameDeepLink.build(game.league, game.id)).apply {
        setClass(context, MainActivity::class.java)
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
    }

private fun statusFor(game: Game): String = when (game.state) {
    GameState.PRE -> GameFormatting.startTime(game)
    GameState.LIVE -> game.displayClock?.takeIf { it.isNotBlank() }
        ?: GameFormatting.statusLine(game)

    GameState.POST -> "Final"
}

class ScoreWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScoreWidget()
}
