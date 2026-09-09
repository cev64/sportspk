package com.personal.sportspk.data.mapper

import com.personal.sportspk.data.local.FavoriteTeamEntity
import com.personal.sportspk.data.local.GameEntity
import com.personal.sportspk.domain.model.Competitor
import com.personal.sportspk.domain.model.FavoriteTeam
import com.personal.sportspk.domain.model.Game
import com.personal.sportspk.domain.model.GameState
import com.personal.sportspk.domain.model.League
import com.personal.sportspk.domain.model.Team

/** Room row -> domain. Returns null for rows whose league id no longer exists. */
internal fun GameEntity.toDomain(): Game? {
    val league = League.fromId(leagueId) ?: return null
    return Game(
        id = id,
        league = league,
        startTimeMillis = startTimeMillis,
        state = runCatching { GameState.valueOf(state) }.getOrDefault(GameState.PRE),
        statusDetail = statusDetail,
        period = period,
        displayClock = displayClock,
        home = Competitor(
            team = Team(
                id = homeTeamId,
                league = league,
                displayName = homeName,
                shortDisplayName = homeShortName,
                abbreviation = homeAbbreviation,
                location = homeLocation,
                logoUrl = homeLogoUrl,
                primaryColor = homeColor,
                alternateColor = homeAltColor,
            ),
            score = homeScore,
            isHome = true,
            isWinner = homeWinner,
            record = homeRecord,
            rank = homeRank,
        ),
        away = Competitor(
            team = Team(
                id = awayTeamId,
                league = league,
                displayName = awayName,
                shortDisplayName = awayShortName,
                abbreviation = awayAbbreviation,
                location = awayLocation,
                logoUrl = awayLogoUrl,
                primaryColor = awayColor,
                alternateColor = awayAltColor,
            ),
            score = awayScore,
            isHome = false,
            isWinner = awayWinner,
            record = awayRecord,
            rank = awayRank,
        ),
        venue = venue,
        broadcast = broadcast,
        note = note,
    )
}

internal fun Game.toEntity(fetchedAtMillis: Long): GameEntity = GameEntity(
    id = id,
    leagueId = league.id,
    startTimeMillis = startTimeMillis,
    state = state.name,
    statusDetail = statusDetail,
    period = period,
    displayClock = displayClock,
    venue = venue,
    broadcast = broadcast,
    note = note,
    fetchedAtMillis = fetchedAtMillis,
    homeTeamKey = home.team.key,
    homeTeamId = home.team.id,
    homeName = home.team.displayName,
    homeShortName = home.team.shortDisplayName,
    homeAbbreviation = home.team.abbreviation,
    homeLocation = home.team.location,
    homeLogoUrl = home.team.logoUrl,
    homeColor = home.team.primaryColor,
    homeAltColor = home.team.alternateColor,
    homeScore = home.score,
    homeWinner = home.isWinner,
    homeRecord = home.record,
    homeRank = home.rank,
    awayTeamKey = away.team.key,
    awayTeamId = away.team.id,
    awayName = away.team.displayName,
    awayShortName = away.team.shortDisplayName,
    awayAbbreviation = away.team.abbreviation,
    awayLocation = away.team.location,
    awayLogoUrl = away.team.logoUrl,
    awayColor = away.team.primaryColor,
    awayAltColor = away.team.alternateColor,
    awayScore = away.score,
    awayWinner = away.isWinner,
    awayRecord = away.record,
    awayRank = away.rank,
)

internal fun FavoriteTeamEntity.toDomain(): FavoriteTeam? {
    val league = League.fromId(leagueId) ?: return null
    return FavoriteTeam(
        league = league,
        teamId = teamId,
        displayName = displayName,
        abbreviation = abbreviation,
        logoUrl = logoUrl,
        sortOrder = sortOrder,
    )
}

internal fun FavoriteTeam.toEntity(): FavoriteTeamEntity = FavoriteTeamEntity(
    key = key,
    leagueId = league.id,
    teamId = teamId,
    displayName = displayName,
    abbreviation = abbreviation,
    logoUrl = logoUrl,
    sortOrder = sortOrder,
)
