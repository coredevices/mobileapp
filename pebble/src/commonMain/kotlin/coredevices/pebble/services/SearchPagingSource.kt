package coredevices.pebble.services

import androidx.paging.PagingSource
import androidx.paging.PagingState
import coredevices.pebble.Platform
import coredevices.pebble.ui.CommonApp
import coredevices.pebble.ui.asCommonApp
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.metadata.WatchType

class SearchPagingSource(
    private val pebbleWebServices: PebbleWebServices,
    private val search: String,
    private val appType: AppType,
    private val watchType: WatchType,
    private val platform: Platform,
) : PagingSource<Int, CommonApp>() {

    // Tracks UUIDs seen in previous pages to suppress cross-page duplicates.
    // Resets automatically when the source is invalidated (new query / refresh).
    private val seenUuids = mutableSetOf<String>()

    override fun getRefreshKey(state: PagingState<Int, CommonApp>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CommonApp> {
        val page = params.key ?: 0
        return try {
            val raw = pebbleWebServices.searchAppStore(
                search = search,
                appType = appType,
                watchType = watchType,
                page = page,
                pageSize = params.loadSize,
            )
            // asCommonApp() already deduplicates within the page (higher version / earlier source wins).
            // seenUuids.add() returns false for UUIDs already seen in earlier pages — those are filtered out.
            val items = raw.mapNotNull { (source, app) ->
                app.asCommonApp(watchType, platform, source)
            }.filter { app ->
                seenUuids.add(app.uuid.toString())
            }
            LoadResult.Page(
                data = items,
                prevKey = null,
                nextKey = if (raw.isEmpty()) null else page + 1,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
