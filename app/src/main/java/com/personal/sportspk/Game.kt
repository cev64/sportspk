package com.personal.sportspk

import org.json.JSONArray
import org.json.JSONObject

enum class GameState { PRE, LIVE, FINAL }

data class Side(
    val teamId: String,
    val abbr: String,
    val logoUrl: String?,
    val score: String?,
    val winner: Boolean,
    val isFavorite: Boolean,
)

data class Game(
    val id: String,
    val startMillis: Long,
    val state: GameState,
    /** ESPN's short status, e.g. "Q3 5:12", "Final", "Bot 7th". */
    val detail: String,
    val away: Side,
    val home: Side,
    val link: String?,
    /** True when ESPN only gave a date (time TBD). */
    val timeTbd: Boolean = false,
)

/** Plain JSON round-trip so the widget can render the last result after a process restart. */
object GameJson {
    fun encode(games: List<Game>, updatedMillis: Long): String = JSONObject()
        .put("updated", updatedMillis)
        .put("games", JSONArray(games.map { g ->
            JSONObject()
                .put("id", g.id)
                .put("start", g.startMillis)
                .put("state", g.state.name)
                .put("detail", g.detail)
                .put("away", side(g.away))
                .put("home", side(g.home))
                .put("link", g.link)
                .put("tbd", g.timeTbd)
        }))
        .toString()

    fun decode(json: String): Pair<List<Game>, Long> {
        val root = JSONObject(json)
        val arr = root.getJSONArray("games")
        val games = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Game(
                id = o.getString("id"),
                startMillis = o.getLong("start"),
                state = GameState.valueOf(o.getString("state")),
                detail = o.optString("detail"),
                away = side(o.getJSONObject("away")),
                home = side(o.getJSONObject("home")),
                link = o.optString("link").ifEmpty { null },
                timeTbd = o.optBoolean("tbd"),
            )
        }
        return games to root.optLong("updated")
    }

    private fun side(s: Side) = JSONObject()
        .put("teamId", s.teamId)
        .put("abbr", s.abbr)
        .put("logo", s.logoUrl)
        .put("score", s.score)
        .put("winner", s.winner)
        .put("fav", s.isFavorite)

    private fun side(o: JSONObject) = Side(
        teamId = o.optString("teamId"),
        abbr = o.optString("abbr"),
        logoUrl = o.optString("logo").ifEmpty { null },
        score = o.optString("score").ifEmpty { null },
        winner = o.optBoolean("winner"),
        isFavorite = o.optBoolean("fav"),
    )
}
