package com.personal.sportspk.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/*
 * Wire types for ESPN's public site API.
 *
 * RULES FOR THIS FILE:
 *   1. Every field is nullable or defaulted. The endpoint is undocumented and its schema
 *      changes without notice; a missing field must degrade one row, never fail the parse.
 *   2. These types never leave the data layer. They are mapped into domain models by
 *      EspnMapper immediately after decoding.
 *
 * Shapes verified against live responses for NFL, MLB, and EPL.
 */

// --- scoreboard ------------------------------------------------------------

@Serializable
internal data class EspnScoreboardDto(
    val events: List<EspnEventDto> = emptyList(),
)

@Serializable
internal data class EspnEventDto(
    val id: String? = null,
    val date: String? = null,
    val name: String? = null,
    val shortName: String? = null,
    val competitions: List<EspnCompetitionDto> = emptyList(),
    val status: EspnStatusDto? = null,
)

@Serializable
internal data class EspnCompetitionDto(
    val id: String? = null,
    val date: String? = null,
    val competitors: List<EspnCompetitorDto> = emptyList(),
    val status: EspnStatusDto? = null,
    val venue: EspnVenueDto? = null,
    val broadcasts: List<EspnBroadcastDto> = emptyList(),
    val notes: List<EspnNoteDto> = emptyList(),
    val neutralSite: Boolean? = null,
)

@Serializable
internal data class EspnCompetitorDto(
    val id: String? = null,
    val homeAway: String? = null,
    val score: String? = null,
    val winner: Boolean? = null,
    val team: EspnTeamDto? = null,
    val records: List<EspnRecordDto> = emptyList(),
    val curatedRank: EspnRankDto? = null,
)

@Serializable
internal data class EspnRankDto(val current: Int? = null)

@Serializable
internal data class EspnRecordDto(
    val name: String? = null,
    val type: String? = null,
    val summary: String? = null,
)

@Serializable
internal data class EspnStatusDto(
    val clock: Double? = null,
    val displayClock: String? = null,
    val period: Int? = null,
    val type: EspnStatusTypeDto? = null,
)

@Serializable
internal data class EspnStatusTypeDto(
    val id: String? = null,
    val name: String? = null,
    /** "pre" | "in" | "post" */
    val state: String? = null,
    val completed: Boolean? = null,
    val description: String? = null,
    val detail: String? = null,
    val shortDetail: String? = null,
)

@Serializable
internal data class EspnVenueDto(
    val id: String? = null,
    val fullName: String? = null,
)

/**
 * `market` is NOT consistently typed by ESPN: the scoreboard returns a bare string
 * ("national"), while the summary endpoint returns an object ({"id":"1","type":"National"}).
 * Typing it as a String broke decoding of every summary response, so it is held as a raw
 * [JsonElement] and normalised by [marketName].
 */
@Serializable
internal data class EspnBroadcastDto(
    val market: JsonElement? = null,
    val names: List<String> = emptyList(),
) {
    /** Lower-cased market name from either shape, or null if ESPN sends something new again. */
    val marketName: String?
        get() = when (val element = market) {
            null -> null
            is JsonPrimitive -> element.contentOrNull?.lowercase()
            is JsonObject -> (element["type"] as? JsonPrimitive)?.contentOrNull?.lowercase()
            else -> null
        }
}

@Serializable
internal data class EspnNoteDto(
    val type: String? = null,
    val headline: String? = null,
)

// --- teams -----------------------------------------------------------------

@Serializable
internal data class EspnTeamDto(
    val id: String? = null,
    val location: String? = null,
    val name: String? = null,
    val abbreviation: String? = null,
    val displayName: String? = null,
    val shortDisplayName: String? = null,
    val color: String? = null,
    val alternateColor: String? = null,
    val logo: String? = null,
    val logos: List<EspnLogoDto> = emptyList(),
)

@Serializable
internal data class EspnLogoDto(val href: String? = null)

@Serializable
internal data class EspnTeamsDto(
    val sports: List<EspnSportDto> = emptyList(),
)

@Serializable
internal data class EspnSportDto(
    val leagues: List<EspnLeagueTeamsDto> = emptyList(),
)

@Serializable
internal data class EspnLeagueTeamsDto(
    val teams: List<EspnTeamWrapperDto> = emptyList(),
)

@Serializable
internal data class EspnTeamWrapperDto(val team: EspnTeamDto? = null)

// --- summary (box score / plays) -------------------------------------------

@Serializable
internal data class EspnSummaryDto(
    val boxscore: EspnBoxScoreDto? = null,
    val plays: List<EspnPlayDto> = emptyList(),
    val header: EspnHeaderDto? = null,
)

@Serializable
internal data class EspnHeaderDto(
    val id: String? = null,
    val competitions: List<EspnCompetitionDto> = emptyList(),
)

@Serializable
internal data class EspnBoxScoreDto(
    val teams: List<EspnBoxScoreTeamDto> = emptyList(),
)

@Serializable
internal data class EspnBoxScoreTeamDto(
    val team: EspnTeamDto? = null,
    val homeAway: String? = null,
    val statistics: List<EspnStatNodeDto> = emptyList(),
)

/**
 * ESPN returns team statistics in two different shapes depending on the sport:
 *   * flat  (NFL):  { "name": "firstDowns", "label": "1st Downs", "displayValue": "15" }
 *   * grouped (MLB): { "name": "batting", "displayName": "Batting", "stats": [ ... ] }
 * One node type covers both; [stats] being empty means it is a leaf.
 */
@Serializable
internal data class EspnStatNodeDto(
    val name: String? = null,
    val displayName: String? = null,
    val shortDisplayName: String? = null,
    val abbreviation: String? = null,
    val label: String? = null,
    val displayValue: String? = null,
    val stats: List<EspnStatNodeDto> = emptyList(),
)

@Serializable
internal data class EspnPlayDto(
    val id: String? = null,
    val text: String? = null,
    val awayScore: Int? = null,
    val homeScore: Int? = null,
    val period: EspnPlayPeriodDto? = null,
    val clock: EspnPlayClockDto? = null,
    val scoringPlay: Boolean? = null,
)

@Serializable
internal data class EspnPlayPeriodDto(
    val number: Int? = null,
    val displayValue: String? = null,
)

@Serializable
internal data class EspnPlayClockDto(val displayValue: String? = null)

// --- standings -------------------------------------------------------------

@Serializable
internal data class EspnStandingsDto(
    val name: String? = null,
    val children: List<EspnStandingsGroupDto> = emptyList(),
    val standings: EspnStandingsTableDto? = null,
)

@Serializable
internal data class EspnStandingsGroupDto(
    val id: String? = null,
    val name: String? = null,
    val abbreviation: String? = null,
    val standings: EspnStandingsTableDto? = null,
    val children: List<EspnStandingsGroupDto> = emptyList(),
)

@Serializable
internal data class EspnStandingsTableDto(
    val name: String? = null,
    val displayName: String? = null,
    val entries: List<EspnStandingsEntryDto> = emptyList(),
)

@Serializable
internal data class EspnStandingsEntryDto(
    val team: EspnTeamDto? = null,
    val note: EspnStandingsNoteDto? = null,
    val stats: List<EspnStandingsStatDto> = emptyList(),
)

@Serializable
internal data class EspnStandingsNoteDto(val description: String? = null)

@Serializable
internal data class EspnStandingsStatDto(
    val name: String? = null,
    val abbreviation: String? = null,
    val displayValue: String? = null,
    val value: Double? = null,
    @SerialName("type") val statType: String? = null,
)
