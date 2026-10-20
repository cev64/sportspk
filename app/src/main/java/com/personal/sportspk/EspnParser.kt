package com.personal.sportspk

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Turns ESPN site-API schedule/scoreboard JSON into [Game]s. Pure, so it's unit tested. */
object EspnParser {

    /** Parses `events[]` from a team schedule or a league scoreboard. */
    fun parseEvents(json: String, favoriteIds: Set<String>): List<Game> {
        val events = JSONObject(json).optJSONArray("events") ?: return emptyList()
        return (0 until events.length()).mapNotNull { i ->
            runCatching { parseEvent(events.getJSONObject(i), favoriteIds) }.getOrNull()
        }
    }

    /** The league slug of a soccer event (e.g. "eng.1", "uefa.champions"), read from its app link. */
    fun soccerLeagueOf(json: String): Map<String, String> {
        val events = JSONObject(json).optJSONArray("events") ?: return emptyMap()
        val out = mutableMapOf<String, String>()
        for (i in 0 until events.length()) {
            val e = events.getJSONObject(i)
            val links = e.optJSONArray("links") ?: continue
            for (j in 0 until links.length()) {
                val href = links.getJSONObject(j).optString("href")
                val slug = Regex("leagueAbbrev=([^&]+)").find(href)?.groupValues?.get(1)
                if (slug != null) {
                    out[e.getString("id")] = slug
                    break
                }
            }
        }
        return out
    }

    private fun parseEvent(e: JSONObject, favoriteIds: Set<String>): Game? {
        val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: return null
        val status = (comp.optJSONObject("status") ?: e.optJSONObject("status"))
            ?.optJSONObject("type")
        val state = when (status?.optString("state")) {
            "in" -> GameState.LIVE
            "post" -> GameState.FINAL
            else -> GameState.PRE
        }
        val competitors = comp.optJSONArray("competitors") ?: return null
        var home: Side? = null
        var away: Side? = null
        for (i in 0 until competitors.length()) {
            val c = competitors.getJSONObject(i)
            val side = parseSide(c, state, favoriteIds)
            if (c.optString("homeAway") == "home") home = side else away = side
        }
        if (home == null || away == null) return null

        return Game(
            id = e.getString("id"),
            startMillis = parseDate(e.getString("date")),
            state = state,
            detail = status?.optString("shortDetail").orEmpty(),
            away = away,
            home = home,
            link = webLink(e.optJSONArray("links")),
            timeTbd = !e.optBoolean("timeValid", true) || comp.optBoolean("timeValid", true).not(),
        )
    }

    private fun parseSide(c: JSONObject, state: GameState, favoriteIds: Set<String>): Side {
        val team = c.optJSONObject("team") ?: JSONObject()
        val id = team.optString("id", c.optString("id"))
        return Side(
            teamId = id,
            abbr = team.optString("abbreviation").ifEmpty { team.optString("shortDisplayName") },
            logoUrl = logo(team),
            score = if (state == GameState.PRE) null else score(c.opt("score")),
            winner = c.optBoolean("winner"),
            isFavorite = id in favoriteIds,
        )
    }

    /** Schedule scores are `{value, displayValue}`; scoreboard scores are plain strings. */
    private fun score(raw: Any?): String? = when (raw) {
        is JSONObject -> raw.optString("displayValue").ifEmpty { null }
        is String -> raw.ifEmpty { null }
        is Number -> raw.toInt().toString()
        else -> null
    }

    private fun logo(team: JSONObject): String? {
        val logos = team.optJSONArray("logos")
        if (logos != null && logos.length() > 0) {
            return logos.getJSONObject(0).optString("href").ifEmpty { null }
        }
        // Scoreboard payloads use a single "logo" pointing at a /scoreboard/ variant; the
        // regular 500px logo is the same art and keeps the logo cache keyed consistently.
        return team.optString("logo").ifEmpty { null }?.replace("/500/scoreboard/", "/500/")
    }

    private fun webLink(links: JSONArray?): String? {
        if (links == null) return null
        for (i in 0 until links.length()) {
            val href = links.getJSONObject(i).optString("href")
            if (href.startsWith("https://www.espn.com/")) return href
        }
        return null
    }

    /** ESPN dates look like "2026-10-21T01:30Z" (no seconds), which ISO_INSTANT rejects. */
    internal fun parseDate(s: String): Long = runCatching { Instant.parse(s).toEpochMilli() }
        .recoverCatching {
            OffsetDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmX"))
                .toInstant().toEpochMilli()
        }
        .getOrThrow()
}
