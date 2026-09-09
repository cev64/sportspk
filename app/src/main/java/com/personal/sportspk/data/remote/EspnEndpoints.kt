package com.personal.sportspk.data.remote

import com.personal.sportspk.domain.model.League

/**
 * URL construction for ESPN's public site API.
 *
 * These endpoints are FREE and require no key, but they are also UNOFFICIAL and UNDOCUMENTED:
 * there is no SLA, no published rate limit, and the response schema can change without notice.
 * Everything downstream is written defensively for that - see [dto] for the nullable DTOs and
 * [com.personal.sportspk.data.mapper.EspnMapper] for the mapping.
 *
 * Confined to the data layer on purpose. Nothing in domain/ or ui/ builds an ESPN URL.
 */
internal object EspnEndpoints {
    private const val SITE_V2 = "https://site.api.espn.com/apis/site/v2/sports"
    private const val API_V2 = "https://site.api.espn.com/apis/v2/sports"

    fun scoreboard(league: League): String =
        "$SITE_V2/${league.sportPath}/${league.leaguePath}/scoreboard"

    fun summary(league: League, eventId: String): String =
        "$SITE_V2/${league.sportPath}/${league.leaguePath}/summary?event=$eventId"

    fun teams(league: League): String =
        "$SITE_V2/${league.sportPath}/${league.leaguePath}/teams?limit=1000"

    fun standings(league: League): String =
        "$API_V2/${league.sportPath}/${league.leaguePath}/standings"
}
