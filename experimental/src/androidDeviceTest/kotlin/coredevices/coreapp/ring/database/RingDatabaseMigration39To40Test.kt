package coredevices.coreapp.ring.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import coredevices.ring.database.room.RingDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RingDatabaseMigration39To40Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = instrumentation.targetContext.getDatabasePath("migration-test.db"),
        driver = BundledSQLiteDriver(),
        databaseClass = RingDatabase::class,
    )

    @Test
    fun dropsOrphanedAudioTasksAndKeepsTheRest() {
        helper.createDatabase(39).use { conn ->
            conn.insertTransfer(id = 1)
            conn.insertTask(id = 1, type = "AudioRecording", transferId = 1)
            conn.insertTask(id = 2, type = "AudioRecording", transferId = null)
            conn.insertTask(id = 3, type = "LocalAudioRecording", fileId = "file")
            conn.insertTask(id = 4, type = "TextRecording", transcription = "hello")
        }

        helper.runMigrationsAndValidate(40).use { conn ->
            assertEquals(listOf(1L, 3L, 4L), conn.taskIds())
        }
    }

    @Test
    fun deletingTransferDeletesItsTasks() {
        helper.createDatabase(39).use { conn ->
            conn.insertTransfer(id = 1)
            conn.insertTransfer(id = 2)
            conn.insertTask(id = 1, type = "AudioRecording", transferId = 1)
            conn.insertTask(id = 2, type = "AudioRecording", transferId = 2)
        }

        helper.runMigrationsAndValidate(40).use { conn ->
            conn.execSQL("PRAGMA foreign_keys = ON")
            conn.execSQL("DELETE FROM RingTransfer WHERE id = 1")
            assertEquals(listOf(2L), conn.taskIds())
        }
    }

    private fun SQLiteConnection.insertTransfer(id: Long) {
        execSQL(
            "INSERT INTO RingTransfer (id, isCurrentIndexIteration, status, createdAt) " +
                "VALUES ($id, 1, 'Completed', 0)"
        )
    }

    private fun SQLiteConnection.insertTask(
        id: Long,
        type: String,
        transferId: Long? = null,
        fileId: String? = null,
        transcription: String? = null,
    ) {
        prepare(
            "INSERT INTO RecordingProcessingTaskEntity " +
                "(id, created, attempts, status, type, transferId, fileId, transcription) " +
                "VALUES (?, 0, 0, 'Pending', ?, ?, ?, ?)"
        ).use { stmt ->
            stmt.bindLong(1, id)
            stmt.bindText(2, type)
            transferId?.let { stmt.bindLong(3, it) } ?: stmt.bindNull(3)
            fileId?.let { stmt.bindText(4, it) } ?: stmt.bindNull(4)
            transcription?.let { stmt.bindText(5, it) } ?: stmt.bindNull(5)
            stmt.step()
        }
    }

    private fun SQLiteConnection.taskIds(): List<Long> =
        prepare("SELECT id FROM RecordingProcessingTaskEntity ORDER BY id").use { stmt ->
            buildList { while (stmt.step()) add(stmt.getLong(0)) }
        }
}
