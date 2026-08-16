package io.rebble.libpebblecommon.disk.pbw

import io.rebble.libpebblecommon.database.entity.asMetadata
import io.rebble.libpebblecommon.metadata.WatchType
import kotlinx.io.files.Path
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class PbwAppVariantTest {

    /**
     * Legacy multi-platform pbw: declares `targetPlatforms: ["chalk"]` in appinfo.json but also
     * ships a root (aplite) build. Emery is aplite-compatible, so we must fall back to the root
     * build instead of refusing the install (the bug behind MOB-7701). The root application.size
     * is 9104 and the chalk build is 2612, so we can confirm the ROOT build was selected.
     */
    @Test
    fun emeryFallsBackToRootApliteBuildNotDeclaredInTargetPlatforms() {
        val app = PbwApp(loadPbw("variant_root_and_chalk.pbw"))

        val variant = app.bestVariantFor(WatchType.EMERY)

        assertEquals(WatchType.APLITE, variant)
        // APLITE resolves to the root build (no aplite/ subdir), proving we picked the root, not chalk.
        assertEquals(9104, app.getManifest(variant!!)!!.application.size)
    }

    @Test
    fun basaltFallsBackToRootApliteBuild() {
        val app = PbwApp(loadPbw("variant_root_and_chalk.pbw"))
        assertEquals(WatchType.APLITE, app.bestVariantFor(WatchType.BASALT))
    }

    @Test
    fun chalkPicksChalkBuild() {
        val app = PbwApp(loadPbw("variant_root_and_chalk.pbw"))

        val variant = app.bestVariantFor(WatchType.CHALK)

        assertEquals(WatchType.CHALK, variant)
        assertEquals(2612, app.getManifest(variant!!)!!.application.size)
    }

    /**
     * A genuine chalk-only pbw (round Pebble Time Round build only, no root build) must NOT install
     * on emery — there is no compatible square build, so selection returns null.
     */
    @Test
    fun chalkOnlyPbwHasNoVariantForEmery() {
        val app = PbwApp(loadPbw("variant_chalk_only.pbw"))
        assertNull(app.bestVariantFor(WatchType.EMERY))
    }

    @Test
    fun chalkOnlyPbwStillInstallsOnChalk() {
        val app = PbwApp(loadPbw("variant_chalk_only.pbw"))
        assertEquals(WatchType.CHALK, app.bestVariantFor(WatchType.CHALK))
    }

    /**
     * The locker entry drives the BlobDB app metadata, which is what actually gates the install.
     * It must agree with [bestVariantFor]: a root (aplite) build undeclared in targetPlatforms
     * still has to yield emery-compatible metadata, or the app silently never syncs to the watch.
     */
    @Test
    fun lockerEntryIncludesRootApliteBuildNotDeclaredInTargetPlatforms() {
        val app = PbwApp(loadPbw("variant_root_and_chalk.pbw"))

        val entry = app.toLockerEntry(Instant.fromEpochSeconds(0), orderIndex = 0)

        assertEquals(
            setOf(WatchType.APLITE.codename, WatchType.CHALK.codename),
            entry.platforms.map { it.name }.toSet(),
        )
        // Icon 11 is the root build's, 22 the chalk one — proving emery resolved to the root build.
        assertEquals(11u, entry.asMetadata(WatchType.EMERY)?.icon?.get())
    }

    @Test
    fun lockerEntryForChalkOnlyPbwHasNoEmeryMetadata() {
        val app = PbwApp(loadPbw("variant_chalk_only.pbw"))

        val entry = app.toLockerEntry(Instant.fromEpochSeconds(0), orderIndex = 0)

        assertEquals(listOf(WatchType.CHALK.codename), entry.platforms.map { it.name })
        assertNull(entry.asMetadata(WatchType.EMERY))
        assertNotNull(entry.asMetadata(WatchType.CHALK))
    }

    /**
     * appinfo.json with no targetPlatforms defaults to aplite, but chalk has no aplite fallback,
     * so trusting that default made every such app "incompatible" on round watches (MOB-11410).
     */
    @Test
    fun lockerEntryUsesShippedBuildsWhenTargetPlatformsIsAbsent() {
        val app = PbwApp(loadPbw("variant_no_target_platforms.pbw"))

        assertEquals(listOf(WatchType.APLITE.codename), app.info.targetPlatforms)

        val entry = app.toLockerEntry(Instant.fromEpochSeconds(0), orderIndex = 0)

        // Icon 32 is the chalk build's, 31 the aplite one.
        assertEquals(32u, entry.asMetadata(WatchType.CHALK)?.icon?.get())
    }

    private fun loadPbw(resourceName: String): Path {
        val inputStream = javaClass.classLoader.getResourceAsStream(resourceName)
            ?: throw AssertionError("Resource not found: $resourceName")
        val tempFile = File.createTempFile("test_file", ".pbw")
        tempFile.deleteOnExit()
        FileOutputStream(tempFile).use { output -> inputStream.use { it.copyTo(output) } }
        return Path(tempFile.absolutePath)
    }
}
