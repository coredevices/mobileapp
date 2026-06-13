package io.rebble.libpebblecommon.disk.pbw

import io.rebble.libpebblecommon.metadata.WatchType
import kotlinx.io.files.Path
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    private fun loadPbw(resourceName: String): Path {
        val inputStream = javaClass.classLoader.getResourceAsStream(resourceName)
            ?: throw AssertionError("Resource not found: $resourceName")
        val tempFile = File.createTempFile("test_file", ".pbw")
        tempFile.deleteOnExit()
        FileOutputStream(tempFile).use { output -> inputStream.use { it.copyTo(output) } }
        return Path(tempFile.absolutePath)
    }
}
