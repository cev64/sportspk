package com.personal.sportspk.data.local

import com.personal.sportspk.domain.model.League

/**
 * Seed values for the My Teams list, written once on first run.
 *
 * These are DATA, not logic. Nothing in the app branches on them: they are inserted into the
 * `favorite_teams` table at first launch and are then fully editable in Settings. Removing an
 * entry here does not remove it from an existing install, and editing the list on-device is
 * never overwritten by a later app update.
 *
 * ESPN team ids, verified against the live /teams endpoint for each league.
 */
internal object DefaultFavorites {

    data class Seed(
        val league: League,
        val teamId: String,
        val displayName: String,
        val abbreviation: String,
    )

    val SEEDS: List<Seed> = listOf(
        Seed(League.NFL, "12", "Kansas City Chiefs", "KC"),
        Seed(League.NFL, "29", "Carolina Panthers", "CAR"),
        Seed(League.NHL, "19", "St. Louis Blues", "STL"),
        Seed(League.MLB, "24", "St. Louis Cardinals", "STL"),
        Seed(League.NBA, "25", "Oklahoma City Thunder", "OKC"),
        Seed(League.NBA, "30", "Charlotte Hornets", "CHA"),
        Seed(League.EPL, "367", "Tottenham Hotspur", "TOT"),
        Seed(League.NCAAF, "130", "Michigan Wolverines", "MICH"),
        Seed(League.NCAAF, "87", "Notre Dame Fighting Irish", "ND"),
        Seed(League.NCAAB, "156", "Creighton Bluejays", "CREI"),
    )
}
