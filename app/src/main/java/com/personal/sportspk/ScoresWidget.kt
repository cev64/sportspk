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
import androidx.glance.layout.RowScope
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
private val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

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
    // One labelled section per day. A live game that started before midnight stays under today.
    val byDay = games.groupBy {
        val day = Instant.ofEpochMilli(it.startMillis).atZone(zone).toLocalDate()
        if (it.state == GameState.LIVE || day < today) today else day
    }.toSortedMap()
    val items = buildList {
        for ((day, dayGames) in byDay) {
            add(Item.Section(dayLabel(day, today)))
            dayGames.forEach { add(Item.Match(it)) }
        }
    }

    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(items.size) { index ->
            when (val item = items[index]) {
                is Item.Section -> SectionLabel(item.title, first = index == 0)
                is Item.Match -> GameRow(item.game, logos, zone)
            }
        }
    }
}

private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "TODAY"
    today.plusDays(1) -> "TOMORROW"
    else -> DAY_LABEL.format(day).uppercase()
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
private fun GameRow(game: Game, logos: Map<String, Bitmap?>, zone: ZoneId) {
    val open = game.link?.let {
        actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
    }
    var modifier = GlanceModifier
        .fillMaxWidth()
        .background(Palette.row)
        .cornerRadius(12.dp)
        .padding(horizontal = 8.dp, vertical = 6.dp)
    if (open != null) modifier = modifier.clickable(open)

    // "My team" is the favorite; when both sides are favorites (Thunder vs Hornets) it's the home team.
    val mineIsHome = game.home.isFavorite
    val mine = if (mineIsHome) game.home else game.away
    val opponent = if (mineIsHome) game.away else game.home

    Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 3.dp)) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            // Team blocks take what they need and the middle flexes, so a narrow widget squeezes
            // the gap around the time rather than clipping a name.
            // My team: logo, then name.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo(mine, logos)
                Spacer(GlanceModifier.width(5.dp))
                TeamName(mine.abbr, bold = true)
            }
            Center(game, mine, opponent, zone)
            // Opponent: "@ SEA" / "vs SEA", then logo.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (mineIsHome) "vs" else "@",
                    maxLines = 1,
                    style = TextStyle(color = Palette.muted, fontSize = 11.sp),
                )
                Spacer(GlanceModifier.width(2.dp))
                TeamName(opponent.abbr, bold = false)
                Spacer(GlanceModifier.width(5.dp))
                Logo(opponent, logos)
            }
        }
    }
}

@Composable
private fun Logo(side: Side, logos: Map<String, Bitmap?>) {
    Box(
        modifier = GlanceModifier.size(28.dp).background(Palette.badge).cornerRadius(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = side.logoUrl?.let { logos[it] }
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = side.abbr,
                modifier = GlanceModifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun TeamName(abbr: String, bold: Boolean) {
    Text(
        abbr,
        maxLines = 1,
        style = TextStyle(
            color = Palette.text,
            fontSize = 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        ),
    )
}

/** Middle of the row: start time before the game, otherwise my score – their score and the status. */
@Composable
private fun RowScope.Center(game: Game, mine: Side, opponent: Side, zone: ZoneId) {
    val main = when (game.state) {
        GameState.PRE ->
            if (game.timeTbd) "TBD" else TIME.format(Instant.ofEpochMilli(game.startMillis).atZone(zone))
        else -> "${mine.score ?: "-"} – ${opponent.score ?: "-"}"
    }
    // The day comes from the section label, so a scheduled game only needs its start time.
    val sub = when (game.state) {
        GameState.PRE -> null
        GameState.LIVE -> game.detail.ifEmpty { "LIVE" }
        GameState.FINAL -> game.detail.ifEmpty { "Final" }
    }
    Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            main,
            maxLines = 1,
            style = TextStyle(
                color = Palette.text,
                fontSize = if (game.state == GameState.PRE) 13.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
        )
        if (sub != null) {
            Text(
                sub,
                maxLines = 1,
                style = TextStyle(
                    color = if (game.state == GameState.LIVE) Palette.live else Palette.muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}
