package com.personal.sportspk

/** A team to follow, addressed the way ESPN's site API addresses it: /sports/{sport}/{league}/teams/{id}. */
data class FavoriteTeam(val sport: String, val league: String, val id: String, val name: String)

val FAVORITE_TEAMS = listOf(
    FavoriteTeam("basketball", "nba", "25", "Oklahoma City Thunder"),
    FavoriteTeam("hockey", "nhl", "19", "St. Louis Blues"),
    FavoriteTeam("soccer", "eng.1", "367", "Tottenham Hotspur"),
    FavoriteTeam("baseball", "mlb", "24", "St. Louis Cardinals"),
    FavoriteTeam("football", "nfl", "12", "Kansas City Chiefs"),
    FavoriteTeam("basketball", "nba", "30", "Charlotte Hornets"),
    FavoriteTeam("football", "college-football", "87", "Notre Dame Fighting Irish"),
    FavoriteTeam("soccer", "usa.1", "21812", "St. Louis CITY SC"),
    FavoriteTeam("basketball", "mens-college-basketball", "156", "Creighton Bluejays"),
)
