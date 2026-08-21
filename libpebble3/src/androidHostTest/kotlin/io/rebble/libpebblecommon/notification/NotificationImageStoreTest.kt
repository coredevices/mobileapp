package io.rebble.libpebblecommon.notification

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationImageStoreTest {
    private fun files(count: Int): List<File> {
        val dir = Files.createTempDirectory("notification-images").toFile()
        return (0 until count).map { i ->
            File(dir, "image-$i.jpg").apply {
                writeBytes(byteArrayOf(0))
                setLastModified(1_000_000L + i * 1000L)
            }
        }
    }

    @Test
    fun evictsOldestBeyondLimit() {
        val all = files(5)
        val evicted = NotificationImageStore.filesToEvict(all.shuffled(), keep = 3)
        assertEquals(listOf(all[1].name, all[0].name), evicted.map { it.name })
    }

    @Test
    fun evictsNothingUnderLimit() {
        assertTrue(NotificationImageStore.filesToEvict(files(3), keep = 3).isEmpty())
    }
}
