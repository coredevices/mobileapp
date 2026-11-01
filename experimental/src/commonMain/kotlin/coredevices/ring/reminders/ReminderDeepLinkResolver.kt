package coredevices.ring.reminders

import coredevices.ring.database.room.dao.CachedItemDao
import coredevices.ring.ui.navigation.RingRoutes

/**
 * Resolves the deep link a fired reminder's notification should open.
 */
class ReminderDeepLinkResolver(
    private val cachedItemDao: CachedItemDao,
) {
    /**
     * Returns a deep link opening the reminder's feed item [itemId], or the Index tab when that
     * item can't be found locally (e.g. not synced to this device yet).
     */
    suspend fun resolveDeepLink(itemId: String): String {
        val exists = runCatching { cachedItemDao.getById(itemId) }.getOrNull() != null
        return if (exists) RingRoutes.objectDeepLink(itemId) else FALLBACK_DEEP_LINK
    }

    companion object {
        const val FALLBACK_DEEP_LINK = "pebble://navbar/index"

        /** Key under which a reminder notification carries its feed item id, so the tap handler
         *  can resolve the deep link at tap time. */
        const val USERINFO_REMINDER_ID = "ring-reminder-id"
    }
}
