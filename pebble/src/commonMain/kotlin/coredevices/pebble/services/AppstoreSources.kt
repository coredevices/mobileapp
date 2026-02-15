package coredevices.pebble.services

import co.touchlab.kermit.Logger
import coredevices.database.AppstoreSource
import coredevices.database.AppstoreSourceDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val logger = Logger.withTag("AppstoreSources")
private const val PEBBLE_FEED_URL = "https://appstore-api.repebble.com/api"
const val REBBLE_FEED_URL = "https://appstore-api.rebble.io/api"

val INITIAL_APPSTORE_SOURCES = listOf(
    AppstoreSource(
        url = PEBBLE_FEED_URL,
        title = "Pebble App Store",
        algoliaAppId = "GM3S9TRYO4",
        algoliaApiKey = "0b83b4f8e4e8e9793d2f1f93c21894aa",
        algoliaIndexName = "apps"
    ),
    AppstoreSource(
        url = REBBLE_FEED_URL,
        title = "Rebble App Store",
        algoliaAppId = "7683OW76EQ",
        algoliaApiKey = "252f4938082b8693a8a9fc0157d1d24f",
        algoliaIndexName = "rebble-appstore-production",
    )
)

fun AppstoreSource.isRebbleFeed(): Boolean = url == REBBLE_FEED_URL

class AppstoreSourceInitializer(
    private val appstoreSourceDao: AppstoreSourceDao,
    private val pebbleAccount: PebbleAccountProvider,
    private val appstoreCache: AppstoreCache
) {
    suspend fun initAppstoreSourcesDB() {
        val current = appstoreSourceDao.getAllSources().first()
        //TODO: remove the migration stuff after a while
        val needsInit = current.isEmpty() ||
                current.any { it.algoliaAppId == null } || // migrate old entries
                current.firstOrNull { it.url == "https://appstore-api.repebble.com/api" }?.title != "Pebble App Store" // migrate title change
        if (needsInit) {
            logger.d { "Initializing appstore sources database" }
            current.forEach { source ->
                appstoreSourceDao.deleteSourceById(source.id)
            }
            INITIAL_APPSTORE_SOURCES.forEach { source ->
                appstoreSourceDao.insertSource(source)
            }
            appstoreCache.clearCache()
        } else {
            logger.d { "Appstore sources database already initialized" }
        }

        GlobalScope.launch {
            val rebbleSource = appstoreSourceDao.getAllSources().first()
                .firstOrNull { it.isRebbleFeed() }
            if (rebbleSource == null) {
                return@launch
            }
            pebbleAccount.get().loggedIn.collect {
                val loggedIn = it != null
                appstoreSourceDao.setSourceEnabled(rebbleSource.id, loggedIn)
            }
        }
    }
}
