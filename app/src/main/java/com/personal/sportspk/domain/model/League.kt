package com.personal.sportspk.domain.model

/**
 * The leagues this app covers.
 *
 * [sportPath] / [leaguePath] are the two segments ESPN's site API uses. They live here rather
 * than in the UI because they are stable identity for a league, but note that nothing outside
 * the data layer is allowed to build URLs from them - see [com.personal.sportspk.data.remote.EspnEndpoints].
 *
 * [id] is persisted in Room and DataStore. Never renumber or rename an existing entry; add new
 * ones at the end. Changing an id would orphan every stored favorite pointing at it.
 */
enum class League(
    val id: String,
    val displayName: String,
    val shortName: String,
    val sportPath: String,
    val leaguePath: String,
    val periodLabel: PeriodLabel,
) {
    NFL("nfl", "NFL", "NFL", "football", "nfl", PeriodLabel.QUARTER),
    NBA("nba", "NBA", "NBA", "basketball", "nba", PeriodLabel.QUARTER),
    MLB("mlb", "MLB", "MLB", "baseball", "mlb", PeriodLabel.INNING),
    NHL("nhl", "NHL", "NHL", "hockey", "nhl", PeriodLabel.PERIOD),
    EPL("epl", "Premier League", "EPL", "soccer", "eng.1", PeriodLabel.HALF),
    NCAAF("ncaaf", "NCAA Football", "NCAAF", "football", "college-football", PeriodLabel.QUARTER),
    NCAAB("ncaab", "NCAA Basketball", "NCAAB", "basketball", "mens-college-basketball", PeriodLabel.HALF),
    ;

    companion object {
        fun fromId(id: String?): League? = entries.firstOrNull { it.id == id }
    }
}

/** How a sport names its chunks of game time, used to render "3rd Quarter" vs "Top 4th". */
enum class PeriodLabel { QUARTER, PERIOD, INNING, HALF }
