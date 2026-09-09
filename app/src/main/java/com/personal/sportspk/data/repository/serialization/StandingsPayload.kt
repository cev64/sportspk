package com.personal.sportspk.data.repository.serialization

import com.personal.sportspk.domain.model.League
import com.personal.sportspk.domain.model.StandingEntry
import com.personal.sportspk.domain.model.Standings
import com.personal.sportspk.domain.model.StandingsGroup
import com.personal.sportspk.domain.model.Team
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Storage format for cached standings.
 *
 * Standings are a variable-depth table, so they are stored as one JSON blob per league rather
 * than shredded across relational tables. This is a CACHE - it is never migrated, and a payload
 * an older build cannot decode is simply treated as absent and refetched.
 *
 * Note this is OUR format, not ESPN's: it is built from domain models, so a provider change
 * does not invalidate stored data.
 */
internal object StandingsPayload {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class PayloadDto(val groups: List<GroupDto> = emptyList())

    @Serializable
    private data class GroupDto(val name: String, val entries: List<EntryDto> = emptyList())

    @Serializable
    private data class EntryDto(
        val teamId: String,
        val displayName: String,
        val shortDisplayName: String,
        val abbreviation: String,
        val location: String = "",
        val logoUrl: String? = null,
        val primaryColor: String? = null,
        val wins: Int? = null,
        val losses: Int? = null,
        val ties: Int? = null,
        val winPercent: String? = null,
        val gamesBehind: String? = null,
        val points: String? = null,
        val summary: String? = null,
        val rank: Int? = null,
    )

    fun encode(standings: Standings): String = json.encodeToString(
        PayloadDto.serializer(),
        PayloadDto(
            groups = standings.groups.map { group ->
                GroupDto(
                    name = group.name,
                    entries = group.entries.map { entry ->
                        EntryDto(
                            teamId = entry.team.id,
                            displayName = entry.team.displayName,
                            shortDisplayName = entry.team.shortDisplayName,
                            abbreviation = entry.team.abbreviation,
                            location = entry.team.location,
                            logoUrl = entry.team.logoUrl,
                            primaryColor = entry.team.primaryColor,
                            wins = entry.wins,
                            losses = entry.losses,
                            ties = entry.ties,
                            winPercent = entry.winPercent,
                            gamesBehind = entry.gamesBehind,
                            points = entry.points,
                            summary = entry.summary,
                            rank = entry.rank,
                        )
                    },
                )
            },
        )
    )

    fun decode(league: League, payload: String, fetchedAtMillis: Long): Standings? {
        val dto = runCatching { json.decodeFromString(PayloadDto.serializer(), payload) }.getOrNull() ?: return null
        return Standings(
            league = league,
            lastUpdatedMillis = fetchedAtMillis,
            groups = dto.groups.map { group ->
                StandingsGroup(
                    name = group.name,
                    entries = group.entries.map { entry ->
                        StandingEntry(
                            team = Team(
                                id = entry.teamId,
                                league = league,
                                displayName = entry.displayName,
                                shortDisplayName = entry.shortDisplayName,
                                abbreviation = entry.abbreviation,
                                location = entry.location,
                                logoUrl = entry.logoUrl,
                                primaryColor = entry.primaryColor,
                                alternateColor = null,
                            ),
                            wins = entry.wins,
                            losses = entry.losses,
                            ties = entry.ties,
                            winPercent = entry.winPercent,
                            gamesBehind = entry.gamesBehind,
                            points = entry.points,
                            summary = entry.summary,
                            rank = entry.rank,
                        )
                    },
                )
            },
        )
    }
}
