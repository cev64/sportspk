package com.personal.sportspk.di

import android.content.Context
import com.personal.sportspk.data.local.SportspkDatabase
import com.personal.sportspk.data.local.UserPreferencesStore
import com.personal.sportspk.data.remote.EspnApi
import com.personal.sportspk.data.repository.BackupRepository
import com.personal.sportspk.data.repository.EspnScoreboardRepository
import com.personal.sportspk.data.repository.RoomFavoritesRepository
import com.personal.sportspk.domain.repository.FavoritesRepository
import com.personal.sportspk.domain.repository.ScoreboardRepository
import kotlinx.coroutines.Dispatchers

/**
 * Manual dependency container.
 *
 * A service locator rather than Hilt: the graph is small and entirely singleton-scoped, and
 * per the dependency rule a full DI framework plus its annotation processor was not worth the
 * build cost for this. Swapping in Hilt later would not change any call site's shape.
 *
 * Everything here is lazy, so app startup does not touch disk before it needs to.
 */
interface AppContainer {
    val scoreboardRepository: ScoreboardRepository
    val favoritesRepository: FavoritesRepository
    val backupRepository: BackupRepository
    val preferencesStore: UserPreferencesStore
    val database: SportspkDatabase
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val database: SportspkDatabase by lazy { SportspkDatabase.build(context) }

    override val preferencesStore: UserPreferencesStore by lazy { UserPreferencesStore(context) }

    private val api by lazy { EspnApi(ioDispatcher = Dispatchers.IO) }

    override val scoreboardRepository: ScoreboardRepository by lazy {
        EspnScoreboardRepository(
            api = api,
            gameDao = database.gameDao(),
            standingsDao = database.standingsDao(),
            refreshStateDao = database.refreshStateDao(),
        )
    }

    override val favoritesRepository: FavoritesRepository by lazy {
        RoomFavoritesRepository(
            dao = database.favoriteTeamDao(),
            refreshStateDao = database.refreshStateDao(),
        )
    }

    override val backupRepository: BackupRepository by lazy {
        BackupRepository(favoritesRepository, preferencesStore)
    }
}
