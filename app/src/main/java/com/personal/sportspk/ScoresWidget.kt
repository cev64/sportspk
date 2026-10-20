package com.personal.sportspk

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class ScoresWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScoresWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        RefreshWorker.schedule(context)
        RefreshWorker.scheduleSoon(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RefreshWorker.cancel(context)
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RefreshWorker.scheduleSoon(context)
    }
}

private object Palette {
    val background = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF14171C))
    val row = ColorProvider(day = Color(0xFFEBEEF2), night = Color(0xFF22262D))
    val text = ColorProvider(day = Color(0xFF11151A), night = Color(0xFFF2F4F7))
    val muted = ColorProvider(day = Color(0xFF66707B), night = Color(0xFF959EA9))
    val live = ColorProvider(day = Color(0xFFE5383B), night = Color(0xFFFF5A5F))
    val badge = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFFFFFFFF))
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE")
private val MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE M/d")

class ScoresWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = GameStore.flow(context)
        provideContent {
            val snapshot by store.collectAsState()
            val logos = remember(snapshot) {
                snapshot?.games.orEmpty()
                    .flatMap { listOf(it.away.logoUrl, it.home.logoUrl) }
                    .filterNotNull().distinct()
                    .associateWith { LogoCache.load(context, it) }
            }
            WidgetBody(snapshot, logos)
        }
    }
}

@Composable
private fun WidgetBody(snapshot: Snapshot?, logos: Map<String, Bitmap?>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(Palette.background)
            .cornerRadius(22.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Header(snapshot)
        Spacer(GlanceModifier.height(6.dp))

        val games = snapshot?.games
        when {
            games == null -> Message("Loading scores…")
            games.isEmpty() -> Message("No games coming up")
            else -> GameList(games, logos)
        }
    }
}

@Composable
private fun Header(snapshot: Snapshot?) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp)
            .clickable(actionRunCallback<RefreshAction>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "My Teams",
            style = TextStyle(color = Palette.text, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.defaultWeight(),
        )
        val updated = snapshot?.updatedMillis?.takeIf { it > 0 }?.let {
            TIME.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
        }
        if (updated != null) {
            Text(updated, style = TextStyle(color = Palette.muted, fontSize = 10.sp))
            Spacer(GlanceModifier.width(4.dp))
        }
        Image(
            provider = ImageProvider(R.drawable.ic_refresh),
            contentDescription = "Refresh",
            colorFilter = ColorFilter.tint(Palette.muted),
            modifier = GlanceModifier.size(16.dp),
        )
    }
}

@Composable
private fun Message(text: String) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(color = Palette.muted, fontSize = 13.sp))
    }
}

private sealed interface Item {
    data class Section(val title: String) : Item
    data class Match(val game: Game) : Item
}

@Composable
private fun GameList(games: List<Game>, logos: Map<String, Bitmap?>) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val (todays, upcoming) = games.partition {
        it.state == GameState.LIVE || Instant.ofEpochMilli(it.startMillis).atZone(zone).toLocalDate() <= today
    }
    val items = buildList {
        if (todays.isNotEmpty()) {
            add(Item.Section("TODAY"))
            todays.forEach { add(Item.Match(it)) }
        }
        if (upcoming.isNotEmpty()) {
            add(Item.Section("UPCOMING"))
            upcoming.forEach { add(Item.Match(it)) }
        }
    }

    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(items.size) { index ->
            when (val item = items[index]) {
                is Item.Section -> SectionLabel(item.title, first = index == 0)
                is Item.Match -> GameRow(item.game, logos, today, zone)
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String, first: Boolean) {
    Text(
        title,
        style = TextStyle(color = Palette.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold),
        modifier = GlanceModifier.padding(start = 6.dp, top = if (first) 0.dp else 6.dp, bottom = 3.dp),
    )
}

@Composable
private fun GameRow(game: Game, logos: Map<String, Bitmap?>, today: LocalDate, zone: ZoneId) {
    val open = game.link?.let {
        actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
    }
    var modifier = GlanceModifier
        .fillMaxWidth()
        .background(Palette.row)
        .cornerRadius(12.dp)
        .padding(horizontal = 8.dp, vertical = 4.dp)
    if (open != null) modifier = modifier.clickable(open)

    Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 3.dp)) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                TeamLine(game.away, game, logos)
                Spacer(GlanceModifier.height(2.dp))
                TeamLine(game.home, game, logos)
            }
            Spacer(GlanceModifier.width(4.dp))
            Status(game, today, zone)
        }
    }
}

@Composable
private fun TeamLine(side: Side, game: Game, logos: Map<String, Bitmap?>) {
    val showScore = game.state != GameState.PRE && side.score != null
    val emphasize = when (game.state) {
        GameState.FINAL -> side.winner
        else -> side.isFavorite
    }
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = GlanceModifier.size(18.dp).background(Palette.badge).cornerRadius(9.dp),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = side.logoUrl?.let { logos[it] }
            if (bitmap != null) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = side.abbr,
                    modifier = GlanceModifier.size(15.dp),
                )
            }
        }
        Spacer(GlanceModifier.width(6.dp))
        Text(
            side.abbr,
            maxLines = 1,
            style = TextStyle(
                color = if (game.state == GameState.FINAL && !side.winner) Palette.muted else Palette.text,
                fontSize = 13.sp,
                fontWeight = if (side.isFavorite) FontWeight.Bold else FontWeight.Normal,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        if (showScore) {
            Text(
                side.score.orEmpty(),
                style = TextStyle(
                    color = if (game.state == GameState.FINAL && !side.winner) Palette.muted else Palette.text,
                    fontSize = 14.sp,
                    fontWeight = if (emphasize || game.state == GameState.LIVE) FontWeight.Bold else FontWeight.Normal,
                ),
            )
        }
    }
}

@Composable
private fun Status(game: Game, today: LocalDate, zone: ZoneId) {
    val (top, bottom) = when (game.state) {
        GameState.LIVE -> "LIVE" to game.detail
        GameState.FINAL -> game.detail.ifEmpty { "Final" } to null
        GameState.PRE -> {
            val start = Instant.ofEpochMilli(game.startMillis).atZone(zone)
            val days = ChronoUnit.DAYS.between(today, start.toLocalDate())
            val day = when {
                days <= 0L -> "Today"
                days == 1L -> "Tomorrow"
                days < 7L -> WEEKDAY.format(start)
                else -> MONTH_DAY.format(start)
            }
            day to (if (game.timeTbd) "TBD" else TIME.format(start))
        }
    }
    Column(modifier = GlanceModifier.width(60.dp), horizontalAlignment = Alignment.End) {
        Text(
            top,
            maxLines = 1,
            style = TextStyle(
                color = if (game.state == GameState.LIVE) Palette.live else Palette.muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
            ),
        )
        if (bottom != null) {
            Text(
                bottom,
                maxLines = 1,
                style = TextStyle(
                    color = Palette.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                ),
            )
        }
    }
}
