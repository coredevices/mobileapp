package coredevices.ring.database.room

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import co.touchlab.kermit.Logger
import coredevices.indexai.agent.ToolDeclarationRecord
import coredevices.indexai.data.entity.AgentIterationEntity
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.ConversationMessageEntity
import coredevices.indexai.data.entity.MessageTag
import coredevices.indexai.data.entity.TolerantMessageTagListSerializer
import coredevices.indexai.data.entity.TolerantSemanticResultSerializer
import coredevices.indexai.data.entity.ItemDocument
import coredevices.indexai.data.entity.LocalRecording
import coredevices.indexai.data.entity.RecordingDocument
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.data.entity.RingTransferInfo
import coredevices.indexai.data.entity.ToolCall
import coredevices.indexai.data.entity.mcp.DisabledBuiltinMcp
import coredevices.indexai.data.entity.mcp.HttpMcpServerEntity
import coredevices.indexai.database.dao.AgentIterationDao
import coredevices.indexai.database.dao.ConversationMessageDao
import coredevices.indexai.database.dao.DisabledBuiltinMcpDao
import coredevices.indexai.database.dao.HttpMcpServerDao
import coredevices.indexai.database.dao.LocalRecordingDao
import coredevices.indexai.database.dao.RecordingEntryDao
import coredevices.indexai.database.dao.RecordingFeedItem
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.data.SemanticResult
import coredevices.ring.agent.pebble_plugin.PluginGrant
import coredevices.ring.data.entity.room.CachedRecordingMetadata
import coredevices.ring.data.entity.room.PluginGrantDenial
import coredevices.ring.data.entity.room.RecordingProcessingTaskEntity
import coredevices.ring.data.entity.room.RingDebugTransfer
import coredevices.ring.data.entity.room.RingRssiReading
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.indexfeed.CachedList
import coredevices.libindex.database.entity.RingTransfer
import coredevices.ring.data.entity.room.TraceEntryEntity
import coredevices.ring.data.entity.room.TraceSessionEntity
import coredevices.ring.data.entity.room.reminders.LocalReminderData
import coredevices.ring.database.room.dao.CachedItemDao
import coredevices.ring.database.room.dao.CachedListDao
import coredevices.ring.database.room.dao.CachedRecordingMetadataDao
import coredevices.ring.database.room.dao.LocalReminderDao
import coredevices.ring.database.room.dao.PluginGrantDao
import coredevices.ring.database.room.dao.RecordingProcessingTaskDao
import coredevices.ring.database.room.dao.RingDebugTransferDao
import coredevices.ring.database.room.dao.RingRssiReadingDao
import coredevices.libindex.database.dao.RingTransferDao
import coredevices.libindex.database.dao.RingTransferFeedItem
import coredevices.ring.database.room.dao.TraceEntryDao
import coredevices.ring.database.room.dao.TraceSessionDao
import io.rebble.libpebblecommon.plugin.WatchSettingsPlugin
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Database(
    exportSchema = true,
    entities = [
        LocalReminderData::class,
        CachedRecordingMetadata::class,
        RingDebugTransfer::class,
        LocalRecording::class,
        ConversationMessageEntity::class,
        RecordingEntryEntity::class,
        RingTransfer::class,
        HttpMcpServerEntity::class,
        DisabledBuiltinMcp::class,
        RecordingProcessingTaskEntity::class,
        TraceSessionEntity::class,
        TraceEntryEntity::class,
        CachedItem::class,
        CachedList::class,
        PluginGrantDenial::class,
        AgentIterationEntity::class,
        RingRssiReading::class,
    ],
    views = [
        RecordingFeedItem::class,
        RingTransferFeedItem::class
    ],
    version = 43,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6, Migrate5To6::class),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10, Migrate9To10::class),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17, Migrate16To17::class),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21),
        AutoMigration(from = 21, to = 22),
        AutoMigration(from = 22, to = 23),
        AutoMigration(from = 23, to = 24),
        AutoMigration(from = 24, to = 25),
        AutoMigration(from = 25, to = 26),
        AutoMigration(from = 26, to = 27),
        AutoMigration(from = 27, to = 28, Migrate27To28::class),
        AutoMigration(from = 28, to = 29, Migrate27To28::class),
        AutoMigration(from = 29, to = 30),
        // 30→31: adds CachedItem.locked / CachedList.locked (encrypted-without-key rows).
        AutoMigration(from = 30, to = 31),
        // 31→32: adds LocalReminderData.notifyBeforeMillis (early heads-up notification lead time).
        AutoMigration(from = 31, to = 32),
        // 32→33: adds RecordingEntryEntity.errorType (classification of `error`).
        AutoMigration(from = 32, to = 33),
        // 33→34 is Migrate33To34, added to the builder in experimentalModule.
        // 34→35: adds PluginGrantDenial.
        AutoMigration(from = 34, to = 35),
        // 35→36: adds ConversationMessageEntity.message_tags.
        AutoMigration(from = 35, to = 36),
        // 36→37 is Migrate36To37, added to the builder in experimentalModule.
        // 37→38: adds AgentIterationEntity.
        AutoMigration(from = 37, to = 38),
        // 38→39: adds RecordingEntryEntity.transcribedViaLocalFallback.
        AutoMigration(from = 38, to = 39),
        // 39→40: RecordingProcessingTaskEntity.transferId FK SET NULL → CASCADE.
        AutoMigration(from = 39, to = 40, Migrate39To40::class),
        // 40→41: adds RingRssiReading.
        AutoMigration(from = 40, to = 41),
        // 41→42: adds HttpMcpServerEntity.description.
        AutoMigration(from = 41, to = 42),
        // 42→43: denies the Watch Settings plugin to the Index agent until the user allows it.
        AutoMigration(from = 42, to = 43, Migrate42To43::class),
    ]
)
@TypeConverters(Converters::class)
@ConstructedBy(RingDatabaseConstructor::class)
abstract class RingDatabase: RoomDatabase() {
    abstract fun localReminderDao(): LocalReminderDao
    abstract fun cachedRecordingMetadataDao(): CachedRecordingMetadataDao
    abstract fun ringDebugTransferDao(): RingDebugTransferDao
    abstract fun localRecordingDao(): LocalRecordingDao
    abstract fun conversationMessageDao(): ConversationMessageDao
    abstract fun recordingEntryDao(): RecordingEntryDao
    abstract fun ringTransferDao(): RingTransferDao
    abstract fun httpMcpServerDao(): HttpMcpServerDao
    abstract fun disabledBuiltinMcpDao(): DisabledBuiltinMcpDao
    abstract fun recordingProcessingTaskDao(): RecordingProcessingTaskDao
    abstract fun traceSessionDao(): TraceSessionDao
    abstract fun traceEntryDao(): TraceEntryDao
    abstract fun cachedItemDao(): CachedItemDao
    abstract fun cachedListDao(): CachedListDao
    abstract fun pluginGrantDao(): PluginGrantDao
    abstract fun agentIterationDao(): AgentIterationDao
    abstract fun ringRssiReadingDao(): RingRssiReadingDao
}

@DeleteColumn("LocalReminderData", "platformId")
class Migrate5To6: AutoMigrationSpec

@DeleteColumn("LocalRecording", "recording")
class Migrate9To10: AutoMigrationSpec

@DeleteColumn("LocalRecording", "notified")
@DeleteColumn("LocalRecording", "discarded")
@DeleteColumn("LocalRecording", "ringRxIndex")
class Migrate16To17: AutoMigrationSpec

/**
 * Backfill for the new `lastPushedUpdated` column. Existing rows with a
 * `firestoreId` were already pushed by the old observer, so mark them
 * already-synced (`lastPushedUpdated = updated`). Without this, every such
 * row would look dirty on first launch after upgrade and the push observer
 * would re-upload the entire table at once. Rows without a `firestoreId`
 * were never uploaded — leave their watermark NULL so they still push.
 */
class Migrate27To28 : AutoMigrationSpec {
    override fun onPostMigrate(connection: SQLiteConnection) {
        connection.execSQL(
            "UPDATE LocalRecording SET lastPushedUpdated = updated WHERE firestoreId IS NOT NULL"
        )
    }
}

/**
 * Gives existing installs the builtin MCP associations a fresh install is seeded with. Startup no
 * longer backfills them, so a builtin added later needs its own migration to reach existing groups.
 */
class Migrate33To34(isAndroid: Boolean) : Migration(33, 34) {
    private val builtinMcpNames = buildList {
        add("builtin_note")
        add("builtin_reminder")
        add("builtin_calendar")
        if (isAndroid) {
            add("builtin_clock")
            add("builtin_messaging")
        }
    }

    override fun migrate(connection: SQLiteConnection) {
        val statement = connection.prepare(
            "INSERT OR IGNORE INTO BuiltinMcpGroupAssociation (groupId, builtinMcpName) " +
                "SELECT id, ? FROM McpSandboxGroupEntity ORDER BY id LIMIT 1"
        )
        try {
            builtinMcpNames.forEach { name ->
                statement.bindText(1, name)
                statement.step()
                statement.reset()
            }
        } finally {
            statement.close()
        }
    }
}

/**
 * Replaces MCP sandbox groups with a flat server list. A server that was in the default (first)
 * group is enabled; every other server is kept but disabled.
 */
class Migrate36To37(isAndroid: Boolean) : Migration(36, 37) {
    private val builtinMcpNames = buildList {
        add("builtin_note")
        add("builtin_reminder")
        add("builtin_calendar")
        if (isAndroid) {
            add("builtin_clock")
            add("builtin_messaging")
        }
    }

    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `DisabledBuiltinMcp` " +
                "(`builtinMcpName` TEXT NOT NULL, PRIMARY KEY(`builtinMcpName`))"
        )
        connection.execSQL(
            "ALTER TABLE `HttpMcpServerEntity` ADD COLUMN `enabled` INTEGER NOT NULL DEFAULT 1"
        )
        val defaultGroup = "(SELECT id FROM McpSandboxGroupEntity ORDER BY id LIMIT 1)"
        connection.execSQL(
            "UPDATE HttpMcpServerEntity SET enabled = 0 WHERE id NOT IN " +
                "(SELECT httpMcpId FROM HttpMcpGroupAssociation WHERE groupId = $defaultGroup)"
        )
        val statement = connection.prepare(
            "INSERT OR IGNORE INTO DisabledBuiltinMcp (builtinMcpName) SELECT ? WHERE ? NOT IN " +
                "(SELECT builtinMcpName FROM BuiltinMcpGroupAssociation WHERE groupId = $defaultGroup)"
        )
        try {
            builtinMcpNames.forEach { name ->
                statement.bindText(1, name)
                statement.bindText(2, name)
                statement.step()
                statement.reset()
            }
        } finally {
            statement.close()
        }
        connection.execSQL("DROP TABLE `BuiltinMcpGroupAssociation`")
        connection.execSQL("DROP TABLE `HttpMcpGroupAssociation`")
        connection.execSQL("DROP TABLE `McpSandboxGroupEntity`")
    }
}

/**
 * Drops audio tasks whose transfer was already deleted under the old SET NULL FK; they can't be
 * processed and fail to load.
 */
class Migrate39To40 : AutoMigrationSpec {
    override fun onPostMigrate(connection: SQLiteConnection) {
        connection.execSQL(
            "DELETE FROM RecordingProcessingTaskEntity WHERE type = 'AudioRecording' AND transferId IS NULL"
        )
    }
}

/** Plugins the Index agent may not use until the user turns them on. */
private val PLUGINS_DENIED_BY_DEFAULT = listOf(WatchSettingsPlugin.BUILT_IN_WATCH_SETTINGS_UUID)

private fun SQLiteConnection.denyDefaultPluginGrants() {
    val statement = prepare(
        "INSERT OR IGNORE INTO PluginGrantDenial (pluginUuid, `grant`, name) VALUES (?, ?, '')"
    )
    try {
        PLUGINS_DENIED_BY_DEFAULT.forEach { pluginUuid ->
            PluginGrant.entries.forEach { grant ->
                statement.bindText(1, pluginUuid.toString())
                statement.bindText(2, grant.name)
                statement.step()
                statement.reset()
            }
        }
    } finally {
        statement.close()
    }
}

class Migrate42To43 : AutoMigrationSpec {
    override fun onPostMigrate(connection: SQLiteConnection) = connection.denyDefaultPluginGrants()
}

/** Fresh databases skip migrations, so the default denials are seeded here instead. */
object SeedDefaultPluginGrants : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) = connection.denyDefaultPluginGrants()
    override fun onDestructiveMigration(connection: SQLiteConnection) = connection.denyDefaultPluginGrants()
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object RingDatabaseConstructor : RoomDatabaseConstructor<RingDatabase> {
    override fun initialize(): RingDatabase
}

class Converters {
    @TypeConverter
    fun StringToUuid(string: String?): Uuid? = string?.let { Uuid.parse(it) }

    @TypeConverter
    fun UuidToString(uuid: Uuid?): String? = uuid?.toString()

    @TypeConverter
    fun LongToInstant(long: Long?): Instant? = long?.let { Instant.fromEpochMilliseconds(it) }

    @TypeConverter
    fun InstantToLong(instant: Instant?): Long? = instant?.toEpochMilliseconds()

    @TypeConverter
    fun RecordingToString(recording: RecordingDocument?) = recording?.let { JsonSnake.encodeToString(it) }

    @TypeConverter
    fun StringToRecording(string: String?) = string?.let {
        try {
            JsonSnake.decodeFromString<RecordingDocument>(it)
        } catch (e: SerializationException) {
            Logger.w { "Failed to deserialize Recording from database, returning empty recording: $e\n$string" }
            RecordingDocument(
                timestamp = Instant.DISTANT_PAST,
                updated = Instant.DISTANT_PAST.toEpochMilliseconds()
            )
        }
    }

    @TypeConverter
    fun ToolCallListToString(toolCalls: List<ToolCall>?) = toolCalls?.let { JsonSnake.encodeToString(it) }

    @TypeConverter
    fun StringToToolCallList(string: String?) = string?.let {
        JsonSnake.decodeFromString<List<ToolCall>>(it)
    }

    @TypeConverter
    fun StringToRingTransferInfo(string: String?) = string?.let {
        return@let try {
            JsonSnake.decodeFromString<RingTransferInfo>(it)
        } catch (e: SerializationException) {
            // Handle legacy data format
            // TODO: Remove this block after a while
            try {
                val ob = JsonSnake.parseToJsonElement(it).jsonObject
                if (ob.containsKey("collection_index")) {
                    return RingTransferInfo(
                        collectionStartIndex = ob["collection_index"]!!.jsonPrimitive.int,
                        collectionEndIndex = ob["collection_index"]!!.jsonPrimitive.int,
                        buttonPressed = ob["button_pressed"]?.jsonPrimitive?.long,
                        buttonReleased = ob["button_released"]?.jsonPrimitive?.long,
                        advertisementReceived = ob["advertisement_received"]!!.jsonPrimitive.long,
                        transferCompleted = ob["transfer_completed"]!!.jsonPrimitive.long,
                        buttonReleaseAdvertisementLatencyMs = ob["button_release_advertisement_latency_ms"]?.jsonPrimitive?.long,
                    )
                } else {
                    Logger.w(e) { "Failed to deserialize RingTransferInfo from database, returning null: ${e.message}\n$string" }
                    return null
                }
            } catch (e: Exception) {
                Logger.w(e) { "Failed to deserialize legacy RingTransferInfo from database, returning null: ${e.message}\n$string" }
                return null
            }
        }
    }

    @TypeConverter
    fun RingTransferInfoToString(info: RingTransferInfo?) = info?.let { JsonSnake.encodeToString(it) }

    @TypeConverter
    fun SemanticResultToString(result: SemanticResult?) =
        result?.let { JsonSnake.encodeToString(it) }

    @TypeConverter
    fun StringToSemanticResult(string: String?) = string?.let {
        JsonSnake.decodeFromString(TolerantSemanticResultSerializer, it)
    }

    @TypeConverter
    fun MessageTagListToString(tags: List<MessageTag>?) = tags?.let { JsonSnake.encodeToString(it) }

    @TypeConverter
    fun StringToMessageTagList(string: String?) = string?.let {
        JsonSnake.decodeFromString(TolerantMessageTagListSerializer, it)
    }

    @TypeConverter
    fun ToolDeclarationListToString(list: List<ToolDeclarationRecord>?) = list?.let { JsonSnake.encodeToString(it) }

    @TypeConverter
    fun StringToToolDeclarationList(string: String?) = string?.let {
        JsonSnake.decodeFromString<List<ToolDeclarationRecord>>(it)
    }

    @TypeConverter
    fun ConversationToString(messages: List<ConversationMessageDocument>?) = messages?.let { JsonSnake.encodeToString(it) }

    @TypeConverter
    fun StringToConversation(string: String?) = string?.let {
        JsonSnake.decodeFromString<List<ConversationMessageDocument>>(it)
    }

    @TypeConverter
    fun StringListToString(list: List<String>?) = list?.let { JsonSnake.encodeToString(it) }

    @TypeConverter
    fun StringToStringList(string: String?) = string?.let {
        JsonSnake.decodeFromString<List<String>>(it)
    }

    @TypeConverter
    fun ItemMetadataToString(metadata: ItemDocument.ItemMetadata?) =
        metadata?.let { JsonSnake.encodeToString(ItemDocument.ItemMetadata.serializer(), it) }

    @TypeConverter
    fun StringToItemMetadata(string: String?) = string?.let {
        JsonSnake.decodeFromString(ItemDocument.ItemMetadata.serializer(), it)
    }
}