package coredevices.ring.glance

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.RadioButton
import androidx.glance.appwidget.RadioButtonDefaults
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import coredevices.ring.R
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.reminders.ReminderCompleter
import coredevices.ring.reminders.ReminderDeepLinkResolver
import coredevices.ring.reminders.upcomingReminders
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.ring.ui.screens.indexfeed.formatShortDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import theme.greyScheme
import theme.lightScheme

class RemindersWidget : GlanceAppWidget(), KoinComponent {
    private val itemRepository: ItemRepository by inject()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val reminders = reminders(itemRepository)
        val initial = reminders.first()
        provideContent {
            val items by reminders.collectAsState(initial)
            GlanceTheme(ColorProviders(light = lightScheme, dark = greyScheme)) {
                Content(items)
            }
        }
    }

    @Composable
    private fun Content(items: List<CachedItem>) {
        val context = LocalContext.current
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(android.R.dimen.system_app_widget_background_radius)
                .clickable(actionStartActivity(openIntent(context, ReminderDeepLinkResolver.FALLBACK_DEEP_LINK))),
        ) {
            Text(
                context.getString(R.string.reminders_widget_title),
                style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                modifier = GlanceModifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            )
            if (items.isEmpty()) {
                Text(
                    context.getString(R.string.reminders_widget_empty),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                    modifier = GlanceModifier.padding(horizontal = 16.dp),
                )
            } else {
                LazyColumn {
                    items(items) { item ->
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                checked = item.done,
                                onClick = actionRunCallback<MarkReminderDoneAction>(
                                    actionParametersOf(MarkReminderDoneAction.ItemId to item.firestoreId)
                                ),
                                colors = RadioButtonDefaults.colors(
                                    checkedColor = GlanceTheme.colors.primary,
                                    uncheckedColor = GlanceTheme.colors.primary,
                                ),
                                modifier = GlanceModifier.semantics {
                                    contentDescription = context.getString(R.string.reminders_widget_mark_done, item.title)
                                },
                            )
                            Column(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .padding(vertical = 4.dp)
                                    .clickable(actionStartActivity(openIntent(context, RingRoutes.objectDeepLink(item.firestoreId)))),
                            ) {
                                Text(
                                    item.title,
                                    maxLines = 2,
                                    style = TextStyle(
                                        color = if (item.done) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
                                        fontSize = 14.sp,
                                        textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                                    ),
                                )
                                item.dueAt?.let {
                                    Text(
                                        formatShortDateTime(it),
                                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openIntent(context: Context, deepLink: String) =
        Intent(context, Class.forName("coredevices.coreapp.MainActivity")).apply {
            data = deepLink.toUri()
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    companion object {
        const val DONE_LINGER_MS = 2_000L
        val lingeringIds = MutableStateFlow(emptySet<String>())

        fun reminders(itemRepository: ItemRepository) =
            combine(itemRepository.getAllFlow(), lingeringIds) { items, ids -> upcomingReminders(items, ids) }
    }
}

class MarkReminderDoneAction : ActionCallback, KoinComponent {
    private val reminderCompleter: ReminderCompleter by inject()

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ItemId] ?: return
        RemindersWidget.lingeringIds.update { it + id }
        try {
            reminderCompleter.markDone(id)
            RemindersWidget().update(context, glanceId)
            delay(RemindersWidget.DONE_LINGER_MS)
        } finally {
            RemindersWidget.lingeringIds.update { it - id }
        }
        RemindersWidget().update(context, glanceId)
    }

    companion object {
        val ItemId = ActionParameters.Key<String>("itemId")
    }
}

class RemindersWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RemindersWidget()
}
