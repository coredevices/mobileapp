package coredevices.pebble.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import coredevices.database.AppstoreSource
import coredevices.database.AppstoreSourceDao
import coredevices.pebble.services.AppstoreService
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.metadata.WatchType
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import kotlin.coroutines.cancellation.CancellationException

private const val TOP_CHART_LIMIT = 50
private const val TOP_CHART_CAROUSEL_SIZE = 10

enum class TopChartSort(val slug: String, val label: String) {
    MostUsed("top-used", "Most used"),
    MostHearted("top-hearted", "Most hearted"),
}

enum class TopChartPeriod(val value: String, val label: String) {
    SinceOct2025("2025-10", "Published since Oct 2025"),
    Last30Days("30d", "Published in the last 30 days"),
    Last7Days("7d", "Published in the last 7 days"),
    AllTime("all", "Published any time"),
}

/** Usage is only measured for watchfaces. */
private fun AppType.topChartSorts(): List<TopChartSort> =
    if (this == AppType.Watchface) TopChartSort.entries else listOf(TopChartSort.MostHearted)

data class TopChartSelection(val sort: TopChartSort, val period: TopChartPeriod)

data class TopChartQuery(
    val sourceId: Int,
    val type: AppType,
    val sort: TopChartSort,
    val period: TopChartPeriod,
    val watchType: WatchType,
)

/** Shared by the store home carousel and the detail page, so both show the same selection. */
class TopChartsViewModel(
    private val appstoreSourceDao: AppstoreSourceDao,
) : ViewModel(), KoinComponent {
    private val logger = Logger.withTag("TopChartsViewModel")
    private val selections = mutableStateMapOf<AppType, TopChartSelection>()
    private val charts = mutableStateMapOf<TopChartQuery, List<CommonApp>>()
    private val loading = mutableSetOf<TopChartQuery>()
    var generation by mutableIntStateOf(0)
        private set

    fun selection(type: AppType): TopChartSelection =
        selections[type] ?: TopChartSelection(type.topChartSorts().first(), TopChartPeriod.SinceOct2025)

    fun select(type: AppType, selection: TopChartSelection) {
        selections[type] = selection
    }

    /** Null while loading; empty if the chart is empty or failed to load. */
    fun chart(query: TopChartQuery): List<CommonApp>? = charts[query]

    fun clear() {
        charts.clear()
        generation++
    }

    fun load(query: TopChartQuery) {
        if (query in charts || !loading.add(query)) return
        viewModelScope.launch {
            val apps = try {
                appstoreSourceDao.getSourceById(query.sourceId)?.let { source ->
                    get<AppstoreService> { parametersOf(source) }.fetchCollectionApps(
                        path = "collection/${query.sort.slug}",
                        appType = query.type,
                        hardwarePlatform = query.watchType,
                        limit = TOP_CHART_LIMIT,
                        extraParameters = mapOf("since" to query.period.value),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(e) { "Failed to load top chart $query" }
                null
            }
            charts[query] = apps.orEmpty()
            loading.remove(query)
        }
    }
}

/** The chart for the current selection, filtered like the other store collections. */
@Composable
private fun rememberTopChart(sourceId: Int, type: AppType): List<CommonApp>? {
    val viewModel: TopChartsViewModel = koinInject()
    val sharedViewModel: SharedLockerViewModel = koinInject()
    val selection = viewModel.selection(type)
    val query = TopChartQuery(sourceId, type, selection.sort, selection.period, sharedViewModel.watchType.value)
    LaunchedEffect(query, viewModel.generation) {
        viewModel.load(query)
    }
    val apps = viewModel.chart(query)
    val showIncompatible = sharedViewModel.showIncompatible.value
    val showScaled = sharedViewModel.showScaled.value
    return remember(apps, showIncompatible, showScaled) {
        apps?.filter { app ->
            (showIncompatible || app.isCompatible) && (showScaled || app.isNativelyCompatible)
        }?.distinctBy { it.uuid }
    }
}

@Composable
private fun TopChartControls(type: AppType, modifier: Modifier = Modifier) {
    val viewModel: TopChartsViewModel = koinInject()
    val selection = viewModel.selection(type)
    val sorts = type.topChartSorts()
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).then(modifier),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sorts.size > 1) {
            TopChartDropdown(selection.sort, sorts, { it.label }) {
                viewModel.select(type, selection.copy(sort = it))
            }
        } else {
            Text(selection.sort.label, color = Color.Gray)
        }
        TopChartDropdown(selection.period, TopChartPeriod.entries, { it.label }) {
            viewModel.select(type, selection.copy(period = it))
        }
    }
}

@Composable
fun TopChartsSection(
    source: AppstoreSource,
    type: AppType,
    navBarNav: NavBarNav,
    topBarParams: TopBarParams,
) {
    val apps = rememberTopChart(source.id, type)
    Column {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp).clickable {
                navBarNav.navigateTo(PebbleNavBarRoutes.TopChartsRoute(sourceId = source.id, appType = type.code))
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Top Charts", fontSize = 24.sp, modifier = Modifier.padding(vertical = 8.dp))
            Spacer(modifier = Modifier.width(7.dp))
            Icon(Icons.AutoMirrored.Default.ArrowForward, contentDescription = "See all")
        }
        TopChartControls(type, modifier = Modifier.padding(horizontal = 16.dp))
        TopChartContent(apps) { visibleApps ->
            LazyRow(
                modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(horizontal = 5.dp),
            ) {
                itemsIndexed(visibleApps.take(TOP_CHART_CAROUSEL_SIZE), key = { _, app -> app.uuid }) { index, app ->
                    RankedWatchfaceCard(index, app, navBarNav, topBarParams, width = 100.dp)
                }
            }
        }
    }
}

@Composable
fun TopChartsScreen(
    navBarNav: NavBarNav,
    topBarParams: TopBarParams,
    sourceId: Int,
    type: AppType,
) {
    val sharedViewModel: SharedLockerViewModel = koinInject()
    sharedViewModel.Init()
    LaunchedEffect(Unit) {
        topBarParams.title("Top Charts")
        topBarParams.actions {}
        topBarParams.searchAvailable(null)
    }
    val apps = rememberTopChart(sourceId, type)
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize()) {
        AppsFilterRow(
            selectedType = null,
            sharedLockerViewModel = sharedViewModel,
            showWatchfaceOrderSetting = false,
        )
        TopChartControls(type, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        TopChartContent(apps) { visibleApps ->
            when (type) {
                AppType.Watchface -> LazyVerticalGrid(
                    columns = GridCells.FixedSize(120.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    itemsIndexed(visibleApps, key = { _, app -> app.uuid }) { index, app ->
                        RankedWatchfaceCard(index, app, navBarNav, topBarParams, width = 120.dp)
                    }
                }

                AppType.Watchapp, AppType.Plugin -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                ) {
                    itemsIndexed(visibleApps, key = { _, app -> app.uuid }) { index, app ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${index + 1}",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(32.dp).padding(start = 8.dp),
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                NativeWatchfaceListItem(
                                    app,
                                    onClick = {
                                        navBarNav.navigateTo(
                                            PebbleNavBarRoutes.LockerAppRoute(
                                                uuid = app.uuid.toString(),
                                                storedId = app.storeId,
                                                storeSource = app.appstoreSource?.id,
                                            )
                                        )
                                    },
                                    topBarParams = topBarParams,
                                    highlightInLocker = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopChartContent(apps: List<CommonApp>?, content: @Composable (List<CommonApp>) -> Unit) {
    when {
        apps == null -> Box(
            modifier = Modifier.fillMaxWidth().height(NATIVE_SCREENSHOT_HEIGHT + 50.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        apps.isEmpty() -> Text(
            "Nothing here yet",
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        else -> content(apps)
    }
}

@Composable
private fun RankedWatchfaceCard(
    index: Int,
    app: CommonApp,
    navBarNav: NavBarNav,
    topBarParams: TopBarParams,
    width: Dp,
) {
    Box {
        NativeWatchfaceCard(
            app,
            navBarNav,
            width = width,
            topBarParams = topBarParams,
            highlightInLocker = true,
        )
        Text(
            "${index + 1}",
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 6.dp, top = 6.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun <T> TopChartDropdown(
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = false,
            onClick = { expanded = true },
            label = { Text(label(selected)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    leadingIcon = if (option == selected) {
                        { Icon(Icons.Filled.Done, contentDescription = "Selected") }
                    } else null,
                )
            }
        }
    }
}
