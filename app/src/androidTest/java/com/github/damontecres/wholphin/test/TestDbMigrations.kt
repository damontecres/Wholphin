package com.github.damontecres.wholphin.test

import androidx.room.testing.MigrationTestHelper
import androidx.room.util.useCursor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.damontecres.wholphin.data.AppDatabase
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TestDbMigrations {
    private val testDbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
        )

    @Test
    @Throws(IOException::class)
    fun migrate2To3() {
        val serverId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        helper.createDatabase(testDbName, 2).apply {
            execSQL(
                "INSERT INTO servers VALUES (?, ?, ?)",
                arrayOf(
                    serverId.toString(),
                    "name",
                    "url",
                ),
            )
            execSQL(
                "INSERT INTO users (serverId, id, name, accessToken) VALUES (?, ?, ?, ?)",
                arrayOf(
                    serverId.toString(),
                    userId.toString(),
                    "username",
                    "token",
                ),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDbName, 3, true)

        db.query("SELECT id FROM servers").useCursor { c ->
            c.moveToFirst()
            Assert.assertEquals(serverId.toString().replace("-", ""), c.getString(0))
        }
        db.query("SELECT serverId, id FROM users").useCursor { c ->
            c.moveToFirst()
            Assert.assertEquals(serverId.toString().replace("-", ""), c.getString(0))
            Assert.assertEquals(userId.toString().replace("-", ""), c.getString(1))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3_2() {
        val serverId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        helper.createDatabase(testDbName, 2).apply {
            execSQL(
                "INSERT INTO servers VALUES (?, ?, ?)",
                arrayOf(
                    serverId.toString().replace("-", ""),
                    "name",
                    "url",
                ),
            )
            execSQL(
                "INSERT INTO users (serverId, id, name, accessToken) VALUES (?, ?, ?, ?)",
                arrayOf(
                    serverId.toString(),
                    userId.toString(),
                    "username",
                    "token",
                ),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDbName, 3, true)

        db.query("SELECT id FROM servers").useCursor { c ->
            c.moveToFirst()
            Assert.assertEquals(serverId.toString().replace("-", ""), c.getString(0))
        }
        db.query("SELECT serverId, id FROM users").useCursor { c ->
            c.moveToFirst()
            Assert.assertEquals(serverId.toString().replace("-", ""), c.getString(0))
            Assert.assertEquals(userId.toString().replace("-", ""), c.getString(1))
        }
    }
}
