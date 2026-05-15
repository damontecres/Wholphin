package com.github.damontecres.wholphin.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.ItemTrackModification
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.data.model.LibraryDisplayInfo
import com.github.damontecres.wholphin.data.model.NavDrawerPinnedItem
import com.github.damontecres.wholphin.data.model.PlaybackEffect
import com.github.damontecres.wholphin.data.model.PlaybackLanguageChoice
import com.github.damontecres.wholphin.data.model.SeerrServer
import com.github.damontecres.wholphin.data.model.SeerrUser
import com.github.damontecres.wholphin.ui.components.ViewOptions
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.serializer.toUUID
import timber.log.Timber
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Database(
    entities = [
        JellyfinServer::class,
        JellyfinUser::class,
        ItemPlayback::class,
        NavDrawerPinnedItem::class,
        LibraryDisplayInfo::class,
        PlaybackEffect::class,
        PlaybackLanguageChoice::class,
        ItemTrackModification::class,
        SeerrServer::class,
        SeerrUser::class,

    ],
    version = 32,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(3, 4),
        AutoMigration(4, 5),
        AutoMigration(5, 6),
        AutoMigration(6, 7),
        AutoMigration(7, 8),
        AutoMigration(8, 9),
        AutoMigration(9, 10),
        AutoMigration(10, 11),
        AutoMigration(11, 12),
        AutoMigration(12, 20),
        AutoMigration(20, 30),
        AutoMigration(30, 31),
        AutoMigration(31, 32),
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): JellyfinServerDao

    abstract fun itemPlaybackDao(): ItemPlaybackDao

    abstract fun serverPreferencesDao(): ServerPreferencesDao

    abstract fun libraryDisplayInfoDao(): LibraryDisplayInfoDao

    abstract fun playbackLanguageChoiceDao(): PlaybackLanguageChoiceDao

    abstract fun seerrServerDao(): SeerrServerDao

    abstract fun playbackEffectDao(): PlaybackEffectDao
}

class Converters {
    @TypeConverter
    fun convertToString(id: UUID): String = id.toString().replace("-", "")

    @TypeConverter
    fun convertToUUID(str: String): UUID = str.toUUID()

    @TypeConverter
    fun convertItemSortBy(sort: ItemSortBy): String = sort.serialName

    @TypeConverter
    fun convertItemSortBy(sort: String): ItemSortBy? = ItemSortBy.fromNameOrNull(sort)

    @TypeConverter
    fun convertSortOrder(sort: SortOrder): String = sort.serialName

    @TypeConverter
    fun convertSortOrder(sort: String): SortOrder? = SortOrder.fromNameOrNull(sort)

    @TypeConverter
    fun convertGetItemsFilter(filter: GetItemsFilter): String = Json.encodeToString(filter)

    @TypeConverter
    fun convertGetItemsFilter(filter: String): GetItemsFilter =
        try {
            Json.decodeFromString(filter)
        } catch (ex: Exception) {
            Timber.e(ex, "Error parsing filter")
            GetItemsFilter()
        }

    @TypeConverter
    fun convertViewOptions(viewOptions: ViewOptions?): String? = viewOptions?.let { Json.encodeToString(viewOptions) }

    @TypeConverter
    fun convertViewOptions(viewOptions: String?): ViewOptions? =
        try {
            viewOptions?.let { Json.decodeFromString(viewOptions) }
        } catch (ex: Exception) {
            Timber.e(ex, "Error parsing view options")
            null
        }

    @TypeConverter
    fun convertToString(dateTime: ZonedDateTime): String = DateTimeFormatter.ISO_ZONED_DATE_TIME.format(dateTime)

    @TypeConverter
    fun convertToLocalDateTime(dateTime: String): ZonedDateTime = ZonedDateTime.parse(dateTime, DateTimeFormatter.ISO_ZONED_DATE_TIME)
}

class ZonedDateTimeSerializer : KSerializer<ZonedDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(ZonedDateTime::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ZonedDateTime =
        ZonedDateTime.parse(decoder.decodeString(), DateTimeFormatter.ISO_ZONED_DATE_TIME)

    override fun serialize(
        encoder: Encoder,
        value: ZonedDateTime,
    ) {
        encoder.encodeString(DateTimeFormatter.ISO_ZONED_DATE_TIME.format(value))
    }
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
