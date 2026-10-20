package com.personal.sportspk

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class Snapshot(val games: List<Game>, val updatedMillis: Long)

/** Last fetched games, kept in memory for the widget and on disk across process deaths. */
object GameStore {
    private val state = MutableStateFlow<Snapshot?>(null)
    private var loaded = false

    private fun file(context: Context) = File(context.filesDir, "games.json")

    @Synchronized
    fun flow(context: Context): StateFlow<Snapshot?> {
        if (!loaded) {
            loaded = true
            state.value = runCatching {
                val (games, updated) = GameJson.decode(file(context).readText())
                Snapshot(games, updated)
            }.getOrNull()
        }
        return state
    }

    @Synchronized
    fun save(context: Context, snapshot: Snapshot) {
        loaded = true
        state.value = snapshot
        runCatching {
            val tmp = File(context.filesDir, "games.json.tmp")
            tmp.writeText(GameJson.encode(snapshot.games, snapshot.updatedMillis))
            tmp.renameTo(file(context))
        }
    }
}
