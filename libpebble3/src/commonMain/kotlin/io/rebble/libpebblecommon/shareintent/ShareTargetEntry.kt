package io.rebble.libpebblecommon.shareintent

import io.rebble.libpebblecommon.metadata.pbw.appinfo.ShareTarget
import kotlin.uuid.Uuid

/**
 * A flat record of a single watchapp's share-target metadata, suitable for
 * platform-specific share-sheet integrations (e.g. Android Sharing Shortcuts).
 *
 * Constructed from a [io.rebble.libpebblecommon.database.entity.LockerEntry]
 * paired with its parsed [io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo].
 */
data class ShareTargetEntry(
    val uuid: Uuid,
    /** Display name; prefer [ShareTarget.label] if set, else the watchapp's [shortName]. */
    val label: String,
    val shortName: String,
    val longName: String,
    val shareTarget: ShareTarget,
    /**
     * Raw bytes of the watchapp's menu icon resource (a PNG declared in
     * `package.json` with `menuIcon: true`), or `null` if the watchapp has
     * no menu icon, the resource couldn't be read, or it isn't a PNG.
     *
     * Watchapp menu icons are typically small (≤25×25 px), monochrome, and
     * designed for the watch's display. Platform-specific share-sheet code
     * (e.g. Android's [io.rebble.libpebblecommon.shareintent.ShareTargetSync])
     * is responsible for scaling and styling for share-sheet rendering, with
     * a sensible fallback when this is null.
     */
    val iconBytes: ByteArray? = null,
) {
    // Equals/hashCode overridden to handle ByteArray sensibly. Without this,
    // ByteArray's reference-equality semantics defeat distinctUntilChanged()
    // upstream, causing every locker re-emission to look "different" and
    // re-publish all shortcuts unnecessarily.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ShareTargetEntry) return false
        return uuid == other.uuid &&
                label == other.label &&
                shortName == other.shortName &&
                longName == other.longName &&
                shareTarget == other.shareTarget &&
                iconBytes.contentEqualsOrBothNull(other.iconBytes)
    }

    override fun hashCode(): Int {
        var result = uuid.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + shortName.hashCode()
        result = 31 * result + longName.hashCode()
        result = 31 * result + shareTarget.hashCode()
        result = 31 * result + (iconBytes?.contentHashCode() ?: 0)
        return result
    }
}

private fun ByteArray?.contentEqualsOrBothNull(other: ByteArray?): Boolean =
    if (this == null) other == null else other != null && this.contentEquals(other)
