package io.rebble.libpebblecommon.shareintent

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.js.PKJSApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid/**
 * Orchestrates routing an OS-level share intent to the watchapp that
 * declared itself as a share target.
 *
 * The flow is:
 *   1. Ask the watch to bring the target app to the foreground (this triggers
 *      [io.rebble.libpebblecommon.connection.endpointmanager.CompanionAppLifecycleManager]
 *      to spin up the app's PKJS instance, if it isn't already running).
 *   2. Wait for that watchapp's PKJS to reach ready state on any connected watch.
 *   3. Deliver the share payload via the new `'shareintent'` PKJS event.
 *
 * Callers may use [enqueue] for fire-and-forget dispatch (typical for an
 * Activity that finishes immediately), or [dispatch] when they need the
 * boolean success/failure result for UI feedback. Both ultimately run on
 * the application-scoped [scope] so that activity death doesn't cancel the
 * launch+wait flow.
 */
class ShareIntentDispatcher(
    private val libPebble: LibPebble,
    private val scope: LibPebbleCoroutineScope,
    private val producer: ShareTargetsProducer,
    private val urlResolver: ShareUrlResolver,
) {
    /**
     * Snapshot of the producer's most recent emission, available
     * synchronously via [.value]. Used by [enqueueForFirstAvailable] which
     * runs on the activity main thread and can't suspend to wait for an
     * emission.
     *
     * Note that [producer.flow] is itself a shared flow (single upstream
     * collection across all subscribers), so wrapping it in an additional
     * StateFlow here doesn't multiply I/O. We just need [StateFlow.value]
     * for the synchronous read.
     */
    private val cachedTargets: StateFlow<List<ShareTargetEntry>> =
        producer.flow.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    /**
     * Read-only view of the currently registered share-capable watchapps.
     * Suitable for the host app's share-target activity to drive a chooser
     * UI when the user picks the static "Pebble" entry rather than a
     * per-watchapp Sharing Shortcut.
     */
    val availableTargets: StateFlow<List<ShareTargetEntry>> = cachedTargets

    companion object {
        private val logger = Logger.withTag(ShareIntentDispatcher::class.simpleName!!)
        /**
         * Maximum wait for the target watchapp's PKJS to become ready after
         * a share intent dispatch.
         *
         * Sized for the cold-start case: when the share intent triggers a
         * fresh watchapp launch, the path is:
         *
         *   Bluetooth roundtrip (launch command) → watch firmware launches
         *   the watchapp → watch C-side init() runs → watch announces app
         *   start to companion → companion app spawns PKJS runtime → PKJS
         *   evaluates index.js → PKJS initialization completes → ready.
         *
         * This sequence takes 15-25 seconds on real hardware in our testing
         * (varies with BT signal quality and watch model). 30 seconds is a
         * conservative ceiling that covers the 95th percentile without
         * keeping the user waiting an unreasonable time on genuine failure.
         *
         * If the watchapp was already running before the share fired, PKJS
         * is usually ready in <1s — this timeout is the cold-start budget.
         */
        val SHARE_LAUNCH_TIMEOUT = 30.seconds
    }

    /**
     * Fire-and-forget. Returns immediately; the launch/wait/signal flow
     * runs on [scope]. Failures are logged but not surfaced — use [dispatch]
     * if the caller needs to react to the outcome.
     */
    fun enqueue(uuid: Uuid, text: String, url: String? = null, subject: String? = null) {
        scope.launch {
            val ok = dispatch(uuid, text, url, subject)
            if (!ok) {
                logger.w { "fire-and-forget share dispatch for $uuid did not deliver" }
            }
        }
    }

    /**
     * Fallback path for when an [Activity] receives an ACTION_SEND with no
     * specific watchapp UUID — typically because the user picked the static
     * manifest-level share entry rather than one of the per-watchapp dynamic
     * Sharing Shortcuts (e.g. shortcuts haven't surfaced yet, or the OS chose
     * to display only the activity-level entry).
     *
     * Looks at the current set of share-target watchapps from [producer]:
     *   - 0 apps declare shareTarget → returns false; caller surfaces error
     *   - 1+ apps                    → dispatches to the first match
     *
     * V1 behavior: with multiple share-target watchapps installed, we don't
     * show a chooser — we just pick the first. The user can use the dynamic
     * Sharing Shortcut entries (which DO carry a UUID) when they want to
     * pick a specific watchapp. A future iteration could surface a real
     * chooser activity here.
     *
     * Returns true if a dispatch was scheduled, false if no eligible
     * watchapp could be found.
     */
    fun enqueueForFirstAvailable(text: String, url: String? = null, subject: String? = null): Boolean {
        // Read the cached snapshot. No re-subscription, no zip re-opens,
        // no file descriptor pressure on the share-intent hot path.
        //
        // Failure mode: if the dispatcher was constructed less than a
        // second ago and the producer hasn't emitted yet, we'll see an
        // empty list. That's the cold-start-after-force-stop case. The
        // caller surfaces "no watchapp set up to receive shares" — which
        // is preferable to blocking the activity main thread for several
        // seconds via runBlocking. The user can retry the share once the
        // app is fully spun up.
        val targets = cachedTargets.value
        if (targets.isEmpty()) {
            logger.w {
                "no watchapp declares shareTarget (cached snapshot empty; " +
                "may be cold start with locker not yet loaded)"
            }
            return false
        }
        val first = targets.first()
        logger.i {
            "fallback dispatch (no UUID in intent): picking first of " +
            "${targets.size} share-target watchapp(s) → ${first.uuid} (${first.shortName})"
        }
        enqueue(first.uuid, text, url, subject)
        return true
    }

    /**
     * Suspending dispatch. @return true if the share was delivered, false on
     * timeout / no watch / no connected device able to run the watchapp.
     *
     * Note: even when called from a caller-supplied coroutine context, the
     * actual await runs on [scope] — the result is reported back via a
     * completion channel. This protects us against premature cancellation
     * if the caller (e.g. an Activity) dies during the wait.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun dispatch(
        uuid: Uuid,
        text: String,
        url: String? = null,
        subject: String? = null,
    ): Boolean = coroutineScope {
        logger.d { "dispatch share intent for $uuid" }

        // Step 1: ensure the target watchapp is running on at least one
        // connected watch.
        //
        // Subtle but important: only call launchApp if the target ISN'T
        // already running. Calling launchApp on the active watchapp causes
        // the watch firmware to send an AppRunStateStop+AppRunStateStart
        // pair, which the companion treats as "app changed" and tears down
        // / re-creates the PKJSApp's WebView. That re-creation:
        //   - cancels any in-flight tile / resource loads from `new Image()`
        //   - clears the JS-side state including any "this tile is
        //     pending" guard flags watchapps use to deduplicate work
        //   - destroys WebKit's image cache (clearCache(true) in stop())
        // The user-visible effect is "tiles stop loading after a share" —
        // because the watchapp's PKJS state was reset mid-flight while the
        // watch firmware is still asking for tiles via the old session.
        //
        // For the share-intent UX, the only thing we need launchApp to
        // achieve is "make sure the target watchapp is foreground." If it
        // already is, the call is unnecessary — and harmful, per above.
        val alreadyRunning = libPebble.watches.value
            .filterIsInstance<ConnectedPebbleDevice>()
            .any { it.runningApp.value == uuid }
        if (!alreadyRunning) {
            logger.d { "launching $uuid (not currently running)" }
            libPebble.launchApp(uuid)
        } else {
            logger.d { "$uuid already running on at least one watch; skipping launch" }
        }

        // Step 2: in parallel with the watch launch + PKJS wait, kick off
        // URL resolution. Most maps shares from Android arrive as
        // `maps.app.goo.gl/...` short URLs that don't contain destination
        // info; resolving here (with a real HTTP client + browser headers)
        // gets the long form. PKJS-side XHR resolution doesn't work because
        // Firebase Dynamic Links serves an HTTP 403 to WebView XHR
        // requests. See ShareUrlResolver for full reasoning.
        //
        // Structured under coroutineScope so if dispatch() times out or is
        // cancelled, the resolver cancels deterministically with it (no
        // orphan HTTP requests holding the shared client's connection
        // pool). Earlier versions used scope.async (application scope)
        // which decoupled lifetime — that left in-flight resolutions
        // running even after the dispatch timed out, contributing to load
        // on the shared httpClient.
        //
        // Running concurrently with the launch+wait means the resolution
        // overlaps with Bluetooth roundtrips and PKJS spinup, which usually
        // exceed the resolver's <500ms typical case. Net-zero added latency
        // most of the time.
        val resolvedUrlDeferred = async { urlResolver.resolveIfShortened(url ?: "") }

        // Step 3: wait for *some* connected watch to expose a fully-ready
        // PKJSApp matching this uuid.
        //
        // "Fully ready" combines two signals:
        //   - PKJSApp.firstWatchMessageReceived: the watch's C-side init()
        //     has finished and its inbox subscription is active. This is
        //     the ideal signal — it proves the watchapp can receive
        //     events the dispatcher will trigger.
        //   - PKJSApp.sessionReadyFlow: the JS runtime is up. Weaker signal
        //     but the only thing available for "quiet" watchapps that
        //     don't message PKJS at startup.
        //
        // We prefer firstWatchMessageReceived when we can get it, but
        // fall back to sessionReadyFlow after WATCHAPP_READY_TIMEOUT so
        // quiet watchapps still get their share intents.
        //
        // Why we can't just observe `sessionIsReady` like the original
        // code: currentCompanionAppSessions is a Flow<List<CompanionApp>>
        // — it emits when the *list* of sessions changes (apps added or
        // removed), not when an existing PKJSApp's internal state
        // transitions. A cold-start dispatch sees the session added to
        // the list while still initializing, the predicate sampling
        // sessionIsReady returns false on that emission, and no further
        // emission ever fires when the app actually becomes ready. The
        // wait then hits SHARE_LAUNCH_TIMEOUT (30s) and gives up.
        //
        // Two-step structure:
        //   (a) find the PKJSApp by uuid via the session-list flow
        //   (b) once found, wait on its own readiness flows for state
        //       transitions
        val readyApp: PKJSApp? = withTimeoutOrNull(SHARE_LAUNCH_TIMEOUT) {
            // (a) Find the PKJSApp matching our uuid. The flatMapLatest
            // chain rebuilds the merged session flow if connected watches
            // change mid-wait. We only need *the* matching PKJSApp here,
            // not its readiness state.
            val app = libPebble.watches
                .flatMapLatest { devices ->
                    val sessionFlows = devices
                        .filterIsInstance<ConnectedPebbleDevice>()
                        .map { it.currentCompanionAppSessions }
                    if (sessionFlows.isEmpty()) {
                        flowOf<PKJSApp?>(null)
                    } else {
                        combine(sessionFlows) { sessionsByDevice ->
                            sessionsByDevice
                                .asSequence()
                                .flatten()
                                .filterIsInstance<PKJSApp>()
                                .firstOrNull { it.uuid == uuid }
                        }
                    }
                }
                .first { it != null }!!

            // (b) Wait for the strict signal first.
            val gotWatchSignal = withTimeoutOrNull(PKJSApp.WATCHAPP_READY_TIMEOUT) {
                app.firstWatchMessageReceived.first { it }
            } != null

            if (gotWatchSignal) {
                app
            } else {
                logger.w {
                    "watchapp ready signal not received within " +
                    "${PKJSApp.WATCHAPP_READY_TIMEOUT}; falling back to " +
                    "PKJS-ready (watchapp may miss the event)"
                }
                // Fall back: wait for PKJS readiness alone. Bounded
                // implicitly by the outer SHARE_LAUNCH_TIMEOUT.
                app.sessionReadyFlow.first { it }
                app
            }
        }

        if (readyApp != null) {
            // Wait for the in-flight URL resolution. resolveIfShortened's
            // own timeout is shorter than this, so by the time PKJS+watch
            // are ready we've almost always got a result.
            val resolvedUrl = resolvedUrlDeferred.await().takeIf { it.isNotEmpty() }
            // Pass the *resolved* URL as the url field. The text field stays
            // as the user originally shared it (which may be the short URL,
            // possibly with surrounding text from sharing-app prefixes).
            // PKJS receives both: it should prefer `url` for parsing, fall
            // back to `text` extraction if `url` is empty/unset.
            readyApp.triggerOnShareIntent(text, resolvedUrl ?: url, subject)
            logger.i { "delivered share intent to $uuid (url resolved: ${resolvedUrl != url})" }
            true
        } else {
            // Cancel the in-flight resolution. Structured coroutineScope
            // would also cancel it on dispatch return, but explicit cancel
            // here releases the slot in the resolver's underlying HTTP
            // request immediately rather than at scope exit.
            resolvedUrlDeferred.cancel()
            logger.w { "share intent for $uuid timed out waiting for PKJS ready" }
            false
        }
    }
}
