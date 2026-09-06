package coredevices.ring.glance

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.indexfeed.displayTitle
import coredevices.ring.data.entity.room.indexfeed.recentNotesAndTodos
import coredevices.ring.data.entity.room.indexfeed.widgetRowSubtitle
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_TODOS_ID
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.util.CoreConfigHolder
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import theme.greyScheme
import theme.lightScheme

/** Home-screen widget listing the most recent Index notes and to-dos.
 *  Instantiated by Glance (not Koin), so dependencies come via [KoinComponent]. */
class IndexNotesWidget : GlanceAppWidget(), KoinComponent {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val enableIndex = get<CoreConfigHolder>().config.value.enableIndex
        val signedIn = Firebase.auth.currentUser != null
        val rows = if (enableIndex && signedIn) recentNotesAndTodos(get<ItemRepository>().getAllFlow().first()) else emptyList()
        provideContent {
            GlanceTheme(ColorProviders(light = lightScheme, dark = greyScheme)) {
                when {
                    !enableIndex -> Message(context, "Turn on Index to see your notes")
                    !signedIn -> Message(context, "Sign in to see your notes")
                    else -> Content(context, rows)
                }
            }
        }
    }

    @Composable
    private fun Message(context: Context, text: String) {
        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(12.dp)
                .clickable(actionStartActivity(launchIntent(context, null))),
        ) {
            Text(text, style = TextStyle(color = GlanceTheme.colors.onSurface))
        }
    }

    @Composable
    private fun Content(context: Context, rows: List<CachedItem>) {
        Column(modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.surface).padding(12.dp)) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                HeaderLink(context, "Notes", RingRoutes.allListsDeepLink())
                Spacer(GlanceModifier.width(16.dp))
                HeaderLink(context, "To-dos", RingRoutes.objectDeepLink(LIST_TODOS_ID))
            }
            if (rows.isEmpty()) {
                Text(
                    "Nothing yet — tap to add",
                    modifier = GlanceModifier.padding(top = 8.dp)
                        .clickable(actionStartActivity(launchIntent(context, RingRoutes.allListsDeepLink()))),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                )
                return@Column
            }
            LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
                items(rows, itemId = { it.firestoreId.hashCode().toLong() }) { item ->
                    Column(
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 6.dp)
                            .clickable(actionStartActivity(launchIntent(context, RingRoutes.objectDeepLink(item.firestoreId)))),
                    ) {
                        Text(item.displayTitle, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp))
                        Text(widgetRowSubtitle(item), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
                    }
                }
            }
        }
    }

    @Composable
    private fun HeaderLink(context: Context, label: String, deepLink: String) {
        Text(
            label,
            modifier = GlanceModifier.clickable(actionStartActivity(launchIntent(context, deepLink))),
            style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp),
        )
    }

    /** Same explicit-component launch as [VoiceWidget]; a null [deepLink] is a bare app launch. */
    private fun launchIntent(context: Context, deepLink: String?): Intent =
        Intent(context, Class.forName("coredevices.coreapp.MainActivity")).apply {
            deepLink?.let { data = Uri.parse(it) }
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
}
