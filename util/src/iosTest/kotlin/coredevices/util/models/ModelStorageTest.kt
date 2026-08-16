package coredevices.util.models

import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelStorageTest {
    private val fileManager = NSFileManager.defaultManager
    private lateinit var root: String
    private lateinit var appSupport: String
    private lateinit var caches: String

    private val legacyModels get() = "$caches/models"
    private val modelName = "parakeet-tdt-0.6b-v3"

    @BeforeTest
    fun setUp() {
        root = "${NSTemporaryDirectory()}model-storage-${NSUUID().UUIDString}"
        appSupport = "$root/Application Support"
        caches = "$root/Caches"
        mkdirs(root)
    }

    @AfterTest
    fun tearDown() {
        fileManager.removeItemAtPath(root, null)
    }

    private fun mkdirs(path: String) {
        fileManager.createDirectoryAtPath(
            path, withIntermediateDirectories = true, attributes = null, error = null
        )
    }

    private fun touch(path: String) {
        fileManager.createFileAtPath(path, contents = null, attributes = null)
    }

    private fun exists(path: String) = fileManager.fileExistsAtPath(path)

    private fun resolve() = resolveModelsDirectory(appSupportDir = appSupport, cachesDir = caches)

    @Test
    fun createsModelsDirectoryUnderApplicationSupport() {
        val dir = resolve()

        assertEquals("$appSupport/models", dir)
        assertTrue(exists(dir))
    }

    @Test
    fun freshInstallWithNoLegacyDirectoryMigratesNothing() {
        val dir = resolve()

        assertTrue(fileManager.contentsOfDirectoryAtPath(dir, null).orEmpty().isEmpty())
    }

    /**
     * The MOB-9371 case: the broken build created an empty `models` dir in Application Support
     * at startup, then downloaded into Caches. A directory-level move would skip this entirely.
     */
    @Test
    fun migratesLegacyModelWhenDestinationExistsButIsEmpty() {
        mkdirs("$appSupport/models")
        mkdirs("$legacyModels/$modelName")
        touch("$legacyModels/$modelName/config.txt")

        val dir = resolve()

        assertTrue(exists("$dir/$modelName/config.txt"))
        assertFalse(exists(legacyModels))
    }

    @Test
    fun migratesLegacyModelWhenDestinationMissing() {
        mkdirs("$legacyModels/$modelName")
        touch("$legacyModels/$modelName/config.txt")

        val dir = resolve()

        assertTrue(exists("$dir/$modelName/config.txt"))
        assertFalse(exists(legacyModels))
    }

    @Test
    fun migratesEveryLegacyModel() {
        mkdirs("$legacyModels/$modelName")
        touch("$legacyModels/$modelName/config.txt")
        mkdirs("$legacyModels/other-model")
        touch("$legacyModels/other-model/config.txt")

        val dir = resolve()

        assertTrue(exists("$dir/$modelName/config.txt"))
        assertTrue(exists("$dir/other-model/config.txt"))
        assertFalse(exists(legacyModels))
    }

    @Test
    fun keepsExistingModelAndDiscardsRedundantLegacyCopy() {
        mkdirs("$appSupport/models/$modelName")
        touch("$appSupport/models/$modelName/from-app-support")
        mkdirs("$legacyModels/$modelName")
        touch("$legacyModels/$modelName/from-caches")

        val dir = resolve()

        assertTrue(exists("$dir/$modelName/from-app-support"))
        assertFalse(exists("$dir/$modelName/from-caches"))
        assertFalse(exists(legacyModels))
    }

    @Test
    fun migrationIsIdempotent() {
        mkdirs("$legacyModels/$modelName")
        touch("$legacyModels/$modelName/config.txt")

        resolve()
        val dir = resolve()

        assertTrue(exists("$dir/$modelName/config.txt"))
        assertFalse(exists(legacyModels))
    }
}
