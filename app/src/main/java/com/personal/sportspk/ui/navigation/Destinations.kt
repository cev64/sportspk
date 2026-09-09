package com.personal.sportspk.ui.navigation

import android.net.Uri
import com.personal.sportspk.domain.model.League

/** Top-level destinations, shown as bottom nav on compact and a rail on expanded. */
enum class TopLevelDestination(val route: String) {
    MyTeams("my_teams"),
    Scores("scores"),
    Standings("standings"),
    Settings("settings"),
    ;

    companion object {
        fun fromRoute(route: String?): TopLevelDestination? =
            entries.firstOrNull { it.route == route }
    }
}

/** Non-top-level routes. */
object Routes {
    const val ABOUT = "about"
}

/**
 * A game addressed from outside the app - a notification tap or a widget tap.
 *
 * Deep link form: `sportspk://game/{leagueId}/{gameId}`
 */
data class GameDeepLink(val league: League, val gameId: String) {
    companion object {
        const val SCHEME = "sportspk"
        const val HOST = "game"

        fun parse(uri: Uri?): GameDeepLink? {
            if (uri == null) return null
            if (uri.scheme != SCHEME || uri.host != HOST) return null
            val segments = uri.pathSegments
            if (segments.size < 2) return null
            val league = League.fromId(segments[0]) ?: return null
            val gameId = segments[1].takeIf { it.isNotBlank() } ?: return null
            return GameDeepLink(league, gameId)
        }

        fun build(league: League, gameId: String): Uri =
            Uri.parse("$SCHEME://$HOST/${league.id}/$gameId")
    }
}
