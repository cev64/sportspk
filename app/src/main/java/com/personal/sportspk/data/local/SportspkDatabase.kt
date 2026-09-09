package com.personal.sportspk.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's Room database.
 *
 * MIGRATION POLICY (see AGENT_INSTRUCTIONS.md "Room Database Migration Policy"):
 *   * Destructive migration is NEVER enabled. `favorite_teams` is real user data.
 *   * Every schema change bumps [VERSION] and adds a Migration to [MIGRATIONS]. Migrations are
 *     kept forever so a device on any older version can reach the current one.
 *   * The exported schema JSON under app/schemas/ is committed and must not be deleted - it is
 *     what migration tests validate against.
 */
@Database(
    entities = [
        GameEntity::class,
        FavoriteTeamEntity::class,
        StandingsEntity::class,
        RefreshStateEntity::class,
        NotifiedEventEntity::class,
    ],
    version = SportspkDatabase.VERSION,
    exportSchema = true,
)
abstract class SportspkDatabase : RoomDatabase() {

    abstract fun gameDao(): GameDao
    abstract fun favoriteTeamDao(): FavoriteTeamDao
    abstract fun standingsDao(): StandingsDao
    abstract fun refreshStateDao(): RefreshStateDao
    abstract fun notifiedEventDao(): NotifiedEventDao

    companion object {
        const val VERSION = 1
        const val NAME = "sportspk.db"

        /**
         * Migrations from every previously shipped version. Append, never edit or remove.
         * Empty at v1 because there is no earlier shipped schema yet.
         */
        val MIGRATIONS: Array<androidx.room.migration.Migration> = emptyArray()

        fun build(context: Context): SportspkDatabase =
            Room.databaseBuilder(context.applicationContext, SportspkDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                // Deliberately NOT calling fallbackToDestructiveMigration(): a missing migration
                // must fail loudly in testing rather than silently delete the user's My Teams list.
                .build()
    }
}
