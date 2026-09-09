package com.personal.sportspk.util

import com.personal.sportspk.data.remote.EspnException

/**
 * Turns a refresh failure into something worth showing.
 *
 * Never surfaces a stack trace or an ESPN URL - the user's actual question is "are these scores
 * current, and is it worth retrying?".
 */
object ErrorText {
    fun of(error: Throwable): String = when (error) {
        is EspnException.Offline -> "Offline - showing the last scores we saved."
        is EspnException.Timeout -> "The scores service is slow to respond. Showing saved scores."
        is EspnException.RateLimited -> "Too many requests too quickly. Backing off for a moment."
        is EspnException.Server -> "The scores service is having trouble (HTTP ${error.code})."
        is EspnException.Malformed -> "The scores service returned data we could not read."
        else -> "Could not refresh scores. Showing saved scores."
    }
}
