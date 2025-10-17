package com.github.damontecres.wholphin.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [JellyfinServer::class, JellyfinUser::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): JellyfinServerDao
}

object Migrations {
    val Migrate2to3 =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users RENAME TO users_old")
                db.execSQL(
                    """
                    CREATE TABLE "users" (
                      rowId integer PRIMARY KEY AUTOINCREMENT NOT NULL,
                      id text NOT NULL,
                      name text,
                      serverId text NOT NULL,
                      accessToken text,
                      FOREIGN KEY (serverId) REFERENCES "servers" (id) ON DELETE CASCADE ON UPDATE NO ACTION
                    )
                    """.trimIndent(),
                )
                db.execSQL("UPDATE servers SET id = REPLACE(id, '-', '')")
                db.execSQL(
                    """
                    INSERT INTO users (id, name, serverId, accessToken)
                        SELECT REPLACE(id, '-', ''), name, REPLACE(serverId, '-', ''), accessToken
                        FROM users_old
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE users_old")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_users_serverId ON users (serverId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_users_id ON users (id)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_id_serverId ON users (id, serverId)")
            }
        }
}
