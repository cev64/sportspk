package com.personal.sportspk.data.mapper

import com.personal.sportspk.data.remote.dto.EspnBoxScoreTeamDto
import com.personal.sportspk.data.remote.dto.EspnCompetitionDto
import com.personal.sportspk.data.remote.dto.EspnCompetitorDto
import com.personal.sportspk.data.remote.dto.EspnEventDto
import com.personal.sportspk.data.remote.dto.EspnPlayDto
import com.personal.sportspk.data.remote.dto.EspnScoreboardDto
import com.personal.sportspk.data.remote.dto.EspnStandingsDto
import com.personal.sportspk.data.remote.dto.EspnStandingsEntryDto
import com.personal.sportspk.data.remote.dto.EspnStandingsGroupDto
import com.personal.sportspk.data.remote.dto.EspnStatNodeDto
import com.personal.sportspk.data.remote.dto.EspnSummaryDto
import com.personal.sportspk.data.remote.dto.EspnTeamDto
import com.personal.sportspk.data.remote.dto.EspnTeamsDto
import com.personal.sportspk.domain.model.Competitor
import com.personal.sportspk.domain.model.Game
import com.personal.sportspk.domain.model.GameDetail
import com.personal.sportspk.domain.model.GameState
import com.personal.sportspk.domain.model.League
import com.personal.sportspk.domain.model.PlayEntry
import com.personal.sportspk.domain.model.StandingEntry
import com.personal.sportspk.domain.model.Standings
import com.personal.sportspk.domain.model.StandingsGroup
import com.personal.sportspk.domain.model.StatGroup
import com.personal.sportspk.domain.model.Team
import com.personal.sportspk.domain.model.TeamBoxScore
import com.personal.sportspk.domain.model.TeamStat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Translates ESPN's wire types into our domain models.
 *
 * This is the ONLY place that understands ESPN's shape. It is deliberately total: any event it
 * cannot make sense of is dropped rather than propagated as a half-populated [Game], so one
 * malformed row can never take down a whole scoreboard.
 */
internal object EspnMapper {

    fun toGames(league: League, dto: EspnScoreboardDto): List<Game> =
        dto.events.mapNotNull { runCatching { toGame(league, it) }.getOrNull() }.filterNotNull()

    fun toGame(league: League, event: EspnEventDto): Game? {
        val id = event.id ?: return null
        val competition = event.competitions.firstOrNull()
        val status = competition?.status ?: event.status
        val competitors = competition?.competitors ?: return null

        val home = competitors.firstOrNull { it.homeAway == "home" } ?: competitors.getOrNull(0)
        val away = competitors.firstOrNull { it.homeAway == "away" } ?: competitors.getOrNull(1)
        if (home == null || away == null) return null

        val homeCompetitor = toCompetitor(league, home, isHome = true) ?: return null
        val awayCompetitor = toCompetitor(league, away, isHome = false) ?: return null

        return Game(
            id = id,
            league = league,
            startTimeMillis = parseIsoMillis(competition.date ?: event.date) ?: return null,
            state = toState(status?.type?.state),
            statusDetail = status?.type?.shortDetail
                ?: status?.type?.detail
                ?: status?.type?.description
                .orEmpty(),
            period = status?.period ?: 0,
            displayClock = status?.displayClock?.takeIf { it.isNotBlank() },
            home = homeCompetitor,
            away = awayCompetitor,
            venue = competition.venue?.fullName,
            broadcast = toBroadcast(competition),
            note = competition.notes.firstOrNull()?.headline?.takeIf { it.isNotBlank() },
        )
    }

    private fun toCompetitor(
        league: League,
        dto: EspnCompetitorDto,
        isHome: Boolean,
    ): Competitor? {
        val team = toTeam(league, dto.team) ?: return null
        return Competitor(
            team = team,
            score = dto.score?.trim()?.toIntOrNull(),
            isHome = isHome,
            isWinner = dto.winner,
            record = dto.records
                .firstOrNull { it.type == "total" || it.name == "overall" }
                ?.summary
                ?.takeIf { it.isNotBlank() },
            rank = dto.curatedRank?.current?.takeIf { it in 1..25 },
        )
    }

    fun toTeam(league: League, dto: EspnTeamDto?): Team? {
        val id = dto?.id ?: return null
        val displayName = dto.displayName
            ?: listOfNotNull(dto.location, dto.name).joinToString(" ").takeIf { it.isNotBlank() }
            ?: dto.abbreviation
            ?: return null
        return Team(
            id = id,
            league = league,
            displayName = displayName,
            shortDisplayName = dto.shortDisplayName ?: dto.name ?: displayName,
            abbreviation = dto.abbreviation ?: displayName.take(3).uppercase(),
            location = dto.location.orEmpty(),
            logoUrl = dto.logo ?: dto.logos.firstOrNull()?.href,
            primaryColor = dto.color?.takeIf { it.isNotBlank() },
            alternateColor = dto.alternateColor?.takeIf { it.isNotBlank() },
        )
    }

    fun toTeams(league: League, dto: EspnTeamsDto): List<Team> =
        dto.sports
            .flatMap { it.leagues }
            .flatMap { it.teams }
            .mapNotNull { toTeam(league, it.team) }
            .distinctBy { it.id }
            .sortedBy { it.displayName }

    fun toState(state: String?): GameState = when (state?.lowercase()) {
        "in" -> GameState.LIVE
        "post" -> GameState.POST
        else -> GameState.PRE
    }

    private fun toBroadcast(competition: EspnCompetitionDto): String? {
        val national = competition.broadcasts.firstOrNull { it.marketName == "national" }
        val chosen = national ?: competition.broadcasts.firstOrNull()
        return chosen?.names?.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    // --- detail ------------------------------------------------------------

    fun toGameDetail(
        league: League,
        game: Game,
        dto: EspnSummaryDto,
        nowMillis: Long,
    ): GameDetail = GameDetail(
        game = game,
        boxScore = dto.boxscore?.teams.orEmpty().mapNotNull { toTeamBoxScore(league, it) },
        plays = dto.plays.mapNotNull { toPlay(it) }.takeLast(MAX_PLAYS).asReversed(),
        lastUpdatedMillis = nowMillis,
    )

    private fun toTeamBoxScore(league: League, dto: EspnBoxScoreTeamDto): TeamBoxScore? {
        val team = toTeam(league, dto.team) ?: return null
        return TeamBoxScore(
            team = team,
            isHome = dto.homeAway == "home",
            groups = toStatGroups(dto.statistics),
        )
    }

    /**
     * Handles both shapes ESPN uses for team statistics: a flat list of leaves (NFL) becomes a
     * single unnamed group, while nested nodes (MLB's "Batting"/"Pitching") keep their names.
     */
    private fun toStatGroups(nodes: List<EspnStatNodeDto>): List<StatGroup> {
        if (nodes.isEmpty()) return emptyList()
        val grouped = nodes.filter { it.stats.isNotEmpty() }
        if (grouped.isNotEmpty()) {
            return grouped.mapNotNull { group ->
                val stats = group.stats.mapNotNull { toStat(it) }
                if (stats.isEmpty()) null
                else StatGroup(group.displayName ?: group.name?.replaceFirstChar { it.uppercase() }, stats)
            }
        }
        val flat = nodes.mapNotNull { toStat(it) }
        return if (flat.isEmpty()) emptyList() else listOf(StatGroup(null, flat))
    }

    private fun toStat(node: EspnStatNodeDto): TeamStat? {
        val value = node.displayValue?.takeIf { it.isNotBlank() } ?: return null
        val label = node.label
            ?: node.displayName
            ?: node.shortDisplayName
            ?: node.abbreviation
            ?: node.name
            ?: return null
        return TeamStat(label = label, displayValue = value)
    }

    private fun toPlay(dto: EspnPlayDto): PlayEntry? {
        val text = dto.text?.takeIf { it.isNotBlank() } ?: return null
        val awayScore = dto.awayScore
        val homeScore = dto.homeScore
        return PlayEntry(
            id = dto.id ?: text.hashCode().toString(),
            text = text,
            periodText = dto.period?.displayValue
                ?: dto.period?.number?.let { "Period $it" },
            clock = dto.clock?.displayValue?.takeIf { it.isNotBlank() },
            scoreText = if (awayScore != null && homeScore != null) "$awayScore-$homeScore" else null,
        )
    }

    // --- standings ---------------------------------------------------------

    fun toStandings(league: League, dto: EspnStandingsDto, nowMillis: Long): Standings {
        val groups = buildList {
            dto.standings?.entries?.let { entries ->
                if (entries.isNotEmpty()) {
                    add(StandingsGroup(dto.name ?: league.displayName, entries.toEntries(league)))
                }
            }
            dto.children.forEach { collectGroups(league, it, this) }
        }
        return Standings(league = league, groups = groups, lastUpdatedMillis = nowMillis)
    }

    /**
     * ESPN nests standings groups arbitrarily deep (league -> conference -> division for the
     * NFL, a single flat table for the EPL). We keep only nodes that actually carry entries.
     */
    private fun collectGroups(
        league: League,
        node: EspnStandingsGroupDto,
        into: MutableList<StandingsGroup>,
    ) {
        val entries = node.standings?.entries.orEmpty()
        if (entries.isNotEmpty()) {
            val name = node.name ?: node.standings?.displayName ?: node.abbreviation
            into += StandingsGroup(name ?: league.displayName, entries.toEntries(league))
        }
        node.children.forEach { collectGroups(league, it, into) }
    }

    private fun List<EspnStandingsEntryDto>.toEntries(league: League): List<StandingEntry> =
        mapNotNull { entry ->
            val team = toTeam(league, entry.team) ?: return@mapNotNull null
            fun stat(name: String) = entry.stats.firstOrNull { it.name == name }
            StandingEntry(
                team = team,
                wins = stat("wins")?.displayValue?.toIntOrNull(),
                losses = stat("losses")?.displayValue?.toIntOrNull(),
                ties = stat("ties")?.displayValue?.toIntOrNull(),
                winPercent = stat("winPercent")?.displayValue,
                gamesBehind = stat("gamesBehind")?.displayValue,
                points = stat("points")?.displayValue,
                summary = stat("overall")?.displayValue,
                rank = (stat("rank") ?: stat("playoffSeed"))?.displayValue?.toIntOrNull(),
            )
        }

    // --- helpers -----------------------------------------------------------

    /**
     * ESPN timestamps look like "2026-09-10T00:20Z" - ISO-8601 with the seconds omitted, which
     * [java.time.Instant.parse] rejects. ISO_OFFSET_DATE_TIME accepts optional seconds.
     */
    fun parseIsoMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private const val MAX_PLAYS = 40
}
