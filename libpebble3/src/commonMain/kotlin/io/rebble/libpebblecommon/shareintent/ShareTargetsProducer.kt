package io.rebble.libpebblecommon.shareintent

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.database.dao.LockerEntryRealDao
import io.rebble.libpebblecommon.database.entity.LockerEntry
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.disk.pbw.DiskUtil
import io.rebble.libpebblecommon.disk.pbw.PbwApp
import io.rebble.libpebblecommon.disk.pbw.PbwResourcePack
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.locker.Locker
import io.rebble.libpebblecommon.locker.LockerPBWCache
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.metadata.pbw.appinfo.Media
import io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.io.files.Path

/**
 * Bridges [Locker] state into a stream of [ShareTargetEntry] suitable for
 * platform-specific share-sheet wiring (e.g. [io.rebble.libpebblecommon.shareintent.ShareTargetSync]
 * on Android).
 *
 * For each installed watchapp, we lazily load its `PbwApp` (a small zip read
 * to access `appinfo.json`), check for a `shareTarget` declaration, and emit
 * a flat record. We also extract the watchapp's menu icon PNG from the PBW
 * (when present) so platform code can render per-watchapp identity in the
 * share sheet. This recomputes whenever the locker contents change. Loads
 * happen on [Dispatchers.IO] to keep the calling context unblocked.
 *
 * Watchfaces are filtered out — they don't have PKJS and can't receive
 * share-intent events, so they have no business being share targets even if
 * a malformed `package.json` declared one.
 *
 * The exposed [flow] is a *shared* flow (single upstream collection,
 * multiple downstream subscribers). Without this, every collector triggers
 * an independent crawl of the locker that opens every watchapp's PBW zip
 * — significant disk I/O on [Dispatchers.IO]. With multiple subscribers
 * (currently [ShareTargetSync] for shortcut sync and [ShareIntentDispatcher]
 * for cached fallback dispatch), the I/O multiplies. Sharing means a single
 * upstream collection serves both.
 *
 * The IO load itself was the cause of a maps-stop-loading bug after share
 * intents: heavy disk reads on Dispatchers.IO would pin enough of the
 * dispatcher's thread pool that PKJS WebView's URLRequestContext got
 * starved of net-thread time. Symptoms included Image() loads stopping,
 * XHRs returning status=0 + TIMEOUT, watchapp tile loading dying. See
 * the related commit message for the diagnostic trail.
 */
class ShareTargetsProducer(
    private val lockerEntryDao: LockerEntryRealDao,
    private val lockerPBWCache: LockerPBWCache,
    private val locker: Locker,
    private val scope: LibPebbleCoroutineScope,
) {
    companion object {
        private val logger = Logger.withTag(ShareTargetsProducer::class.simpleName!!)
    }

    val flow: Flow<List<ShareTargetEntry>> =
        lockerEntryDao
            .getAllFlow(AppType.Watchapp.code, searchQuery = null, limit = Int.MAX_VALUE)
            .map { entries -> entries.mapNotNull { tryRead(it) } }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()
            .shareIn(
                scope = scope,
                // Eagerly: start collecting at flow construction time. This
                // pre-warms the share-targets list during app startup so
                // [ShareIntentDispatcher.cachedTargets] has data ready by
                // the time the user's first share fires. WhileSubscribed
                // would defer collection until first subscriber, which adds
                // perceptible latency and zip-read I/O exactly when the user
                // is most performance-sensitive (waiting on a share).
                started = SharingStarted.Eagerly,
                // replay = 1 lets late subscribers (e.g. lazy-injected
                // dispatcher) immediately see the most recent emission
                // without re-running the pipeline.
                replay = 1,
            )

    private suspend fun tryRead(entry: LockerEntry): ShareTargetEntry? {
        val (path, info) = try {
            val p = lockerPBWCache.getPBWFileForApp(entry.id, entry.version, locker)
            p to PbwApp(p).info
        } catch (e: Exception) {
            // PBW not yet downloaded, corrupt, or otherwise unreadable. Treat
            // as "no share target declared." Logged at debug because it is
            // expected during locker-sync transients.
            logger.d(e) { "couldn't read PBW for ${entry.id}" }
            return null
        }
        val target = info.shareTarget ?: return null
        return ShareTargetEntry(
            uuid = entry.id,
            label = target.label?.takeIf { it.isNotBlank() } ?: info.shortName,
            shortName = info.shortName,
            longName = info.longName,
            shareTarget = target,
            iconBytes = readMenuIconBytes(path, info),
        )
    }

    /**
     * Best-effort extraction of the watchapp's menu-icon PNG from its PBW.
     *
     * Tries two sources in order:
     *
     *  1. **Raw source PNG at zip root.** Some toolchains preserve the
     *     original PNG file at its declared `resources.media[i].file` path
     *     inside the PBW. If present we just read it.
     *
     *  2. **Compiled `app_resources.pbpack`.** The standard Pebble SDK and
     *     CloudPebble compile all media into a flat `.pbpack` resource
     *     pack and do NOT ship the source PNGs. We parse the pack and pull
     *     the resource at the menu icon's index in the `resources.media`
     *     array (which corresponds to its 1-based file_id in the pack —
     *     the SDK preserves declaration order).
     *
     * Returns null when:
     *  - no media resource is flagged as the menu icon
     *  - the resource isn't a PNG ("type" != "png")
     *  - neither source yields readable bytes
     *  - any I/O / parse error occurs (treated as "no icon available";
     *    callers fall back to a platform-provided generic icon)
     *
     * Watchapp menu icons are typically tiny (≤25×25), often white-on-
     * transparent for the watch's display. Platform code is responsible for
     * scaling and styling for share-sheet rendering.
     */
    private fun readMenuIconBytes(pbwPath: Path, info: PbwAppInfo): ByteArray? {
        val menuIconResource = info.resources.media.firstOrNull { media ->
            media.menuIcon.value
        } ?: return null
        if (!menuIconResource.type.equals("png", ignoreCase = true)) {
            return null
        }

        // Tier 1: the source PNG might be shipped at its declared path.
        DiskUtil.readPbwResourceFileOrNull(pbwPath, menuIconResource.resourceFile)
            ?.let { return it }

        // Tier 2: extract from the compiled resource pack. The resource
        // pack groups its contents by integer index, in the order the
        // resources were declared in `appinfo.json`'s `resources.media`
        // array; index here matches table position in the .pbpack.
        val resourceIndex = info.resources.media.indexOf(menuIconResource)
        if (resourceIndex < 0) return null

        // Resource packs are per-platform — pick any platform the watchapp
        // declares. Menu icons are typically the same across platforms in
        // a multi-platform PBW, so any one works for sharing-UI rendering.
        val watchType = info.targetPlatforms
            .firstNotNullOfOrNull { codename -> WatchType.fromCodename(codename) }
            ?: return null

        val packBytes = DiskUtil.readPbwResourcePackBytesOrNull(pbwPath, watchType)
            ?: return null

        return PbwResourcePack.extractResource(packBytes, resourceIndex)
    }
}
