@file:OptIn(ExperimentalTime::class)

package coredevices.ring.glance

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import coredevices.ring.R
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.indexfeed.WidgetCounts
import coredevices.ring.data.entity.room.indexfeed.displayTitle
import coredevices.ring.data.entity.room.indexfeed.isWidgetTodo
import coredevices.ring.data.entity.room.indexfeed.recentNotesAndTodos
import coredevices.ring.data.entity.room.indexfeed.relativeTime
import coredevices.ring.data.entity.room.indexfeed.widgetCounts
import coredevices.ring.data.entity.room.indexfeed.widgetRowLabel
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.database.room.repository.ListRepository
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_TODOS_ID
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.ring.ui.theme.IndexColors
import coredevices.util.CoreConfigHolder
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Index theme tokens as day/night providers so the widget matches the feed in both modes. */
private object W {
    private fun both(pick: (IndexColors) -> androidx.compose.ui.graphics.Color) =
        ColorProvider(day = pick(IndexColors.Light), night = pick(IndexColors.Dark))
    val surface = both { it.surface }
    val card = both { it.surfaceContainerLow }
    val ink = both { it.onSurface }
    val meta = both { it.onSurfaceVariant }
    val outline = both { it.outline }
    val divider = both { it.outlineVariant }
    val red = ColorProvider(IndexColors.Light.primary)
    val onRed = ColorProvider(IndexColors.Light.onPrimary)
}

/** Home-screen widget: a Pebble-red band with today's counts over the most recent Index
 *  notes and to-dos. Instantiated by Glance (not Koin), so dependencies come via [KoinComponent].
 *
 *  Data is collected INSIDE the composition: a Glance session can stay alive for tens of
 *  seconds and update()/updateAll() only recomposes it — provideGlance is not re-run — so a
 *  one-shot snapshot here would render stale for the session's lifetime. The .first() reads
 *  only seed the initial frame. Mirrors the in-app feed: local Room data, gated on
 *  enableIndex only (the feed never gates on sign-in). */
class IndexNotesWidget : GlanceAppWidget(), KoinComponent {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val config = get<CoreConfigHolder>().config
        val itemsFlow = get<ItemRepository>().getAllFlow()
        val listsFlow = get<ListRepository>().getAllFlow()
        val initialItems = itemsFlow.first()
        val initialLists = listsFlow.first()
        provideContent {
            val enableIndex = config.collectAsState().value.enableIndex
            val items by itemsFlow.collectAsState(initialItems)
            val lists by listsFlow.collectAsState(initialLists)
            val titles = remember(lists) { lists.associate { it.firestoreId to it.displayTitle } }
            val rows = remember(items) { recentNotesAndTodos(items) }
            val counts = remember(items) { widgetCounts(items) }
            Column(modifier = GlanceModifier.fillMaxSize().background(W.surface).cornerRadius(20.dp)) {
                if (enableIndex) Banner(context, rows, counts, titles, Clock.System.now())
                else Message(context, "Turn on Index to see your notes")
            }
        }
    }

    @Composable
    private fun Message(context: Context, text: String) {
        Text(
            text,
            modifier = GlanceModifier.fillMaxWidth().padding(16.dp)
                .clickable(actionStartActivity(launchIntent(context, null))),
            style = TextStyle(color = W.meta, fontSize = 13.sp),
        )
    }

    @Composable
    private fun ColumnScope.Banner(context: Context, rows: List<CachedItem>, counts: WidgetCounts, titles: Map<String, String>, now: Instant) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().background(W.red).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                "Index",
                modifier = GlanceModifier.clickable(actionStartActivity(launchIntent(context, null))),
                style = TextStyle(color = W.onRed, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.defaultWeight())
            Column(horizontalAlignment = Alignment.End) {
                CountLine(context, plural(counts.todos, "to-do"), RingRoutes.objectDeepLink(LIST_TODOS_ID))
                CountLine(context, plural(counts.notes, "note"), RingRoutes.allListsDeepLink())
            }
        }
        if (rows.isEmpty()) {
            Text(
                "Nothing yet — tap to add",
                modifier = GlanceModifier.fillMaxWidth().padding(16.dp)
                    .clickable(actionStartActivity(launchIntent(context, RingRoutes.allListsDeepLink()))),
                style = TextStyle(color = W.meta, fontSize = 13.sp),
            )
            return
        }
        // Rows as cards: 4 dp gaps, 10 dp side margin, so five still fit at 3×3.
        LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight().padding(horizontal = 10.dp)) {
            items(rows) { item ->
                Column(modifier = GlanceModifier.fillMaxWidth().padding(top = if (item === rows.first()) 8.dp else 4.dp)) {
                    RowCard(context, item, titles, now)
                }
            }
        }
    }

    @Composable
    private fun CountLine(context: Context, text: String, deepLink: String) {
        Text(
            text,
            modifier = GlanceModifier.clickable(actionStartActivity(launchIntent(context, deepLink))),
            style = TextStyle(color = W.onRed, fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
        )
    }

    @Composable
    private fun RowCard(context: Context, item: CachedItem, titles: Map<String, String>, now: Instant) {
        val todo = item.isWidgetTodo()
        val label = widgetRowLabel(item, titles, now)
        val meta = if (todo) label else "$label · ${relativeTime(item.updatedAt, now)}"
        val metaColor = if (todo && label != "To-do") W.red else W.meta
        Row(
            modifier = GlanceModifier.fillMaxWidth().background(W.card).cornerRadius(12.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clickable(actionStartActivity(launchIntent(context, RingRoutes.objectDeepLink(item.firestoreId)))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (todo) Ring(W.red, fill = W.card) else NotePad()
            Spacer(GlanceModifier.width(10.dp))
            Column {
                Text(item.displayTitle, maxLines = 1, style = TextStyle(color = W.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Medium))
                Text(meta, maxLines = 1, style = TextStyle(color = metaColor, fontSize = 11.5.sp, fontWeight = if (metaColor === W.red) FontWeight.Medium else FontWeight.Normal))
            }
        }
    }

    /** Hollow 13 dp circle (a to-do's unchecked box) drawn as a ring-coloured box with a
     *  [fill]-coloured box inside — Glance has no border modifier. */
    @Composable
    private fun Ring(ring: ColorProvider, fill: ColorProvider) {
        Box(GlanceModifier.size(13.dp).background(ring).cornerRadius(7.dp), contentAlignment = Alignment.Center) {
            Box(GlanceModifier.size(10.dp).background(fill).cornerRadius(5.dp)) {}
        }
    }

    /** Notepad glyph for note rows, tinted with the outline colour. */
    @Composable
    private fun NotePad() {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_note),
            contentDescription = null,
            modifier = GlanceModifier.size(15.dp),
            colorFilter = ColorFilter.tint(W.outline),
        )
    }

    private fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

    /** Same explicit-component launch as [VoiceWidget]; a null [deepLink] is a bare app launch. */
    private fun launchIntent(context: Context, deepLink: String?): Intent =
        Intent(context, Class.forName("coredevices.coreapp.MainActivity")).apply {
            deepLink?.let { data = Uri.parse(it) }
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
}
