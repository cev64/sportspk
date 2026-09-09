package com.personal.sportspk.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room migration harness.
 *
 * At v1 there is no earlier shipped schema to migrate from, so this verifies the setup itself:
 * the schema IS exported (migrations cannot be written or tested without it) and the database
 * opens cleanly. When [SportspkDatabase.VERSION] is next bumped, add a test here that:
 *
 *   1. creates the OLD version via [helper],
 *   2. inserts representative favorite_teams rows,
 *   3. runs the migration with `helper.runMigrationsAndValidate(...)`,
 *   4. asserts those rows are still present and correct.
 *
 * Do not replace a missing migration with a destructive one - favorite_teams is user data.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SportspkDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun schemaIsExportedSoFutureMigrationsCanBeValidated() {
        val db = helper.createDatabase(TEST_DB, SportspkDatabase.VERSION)
        db.close()
    }

    @Test
    fun migrationListCoversEveryShippedVersion() {
        // Every version above 1 needs a migration path into the current version.
        val expectedMigrations = SportspkDatabase.VERSION - 1
        assertEquals(
            "Bumping the DB version requires adding a migration to SportspkDatabase.MIGRATIONS",
            expectedMigrations,
            SportspkDatabase.MIGRATIONS.size,
        )
    }

    @Test
    fun favoritesSurviveAReopenOfTheDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = SportspkDatabase.build(context)
        val dao = db.favoriteTeamDao()

        kotlinx.coroutines.runBlocking {
            dao.upsert(
                listOf(
                    FavoriteTeamEntity(
                        key = "nfl:12",
                        leagueId = "nfl",
                        teamId = "12",
                        displayName = "Kansas City Chiefs",
                        abbreviation = "KC",
                        logoUrl = null,
                        sortOrder = 0,
                    )
                )
            )
            assertTrue(dao.all().any { it.key == "nfl:12" })
        }
        db.close()

        val reopened = SportspkDatabase.build(context)
        kotlinx.coroutines.runBlocking {
            val rows = reopened.favoriteTeamDao().all()
            assertTrue("favorites must survive closing and reopening", rows.any { it.key == "nfl:12" })
            reopened.favoriteTeamDao().delete("nfl:12")
        }
        reopened.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
