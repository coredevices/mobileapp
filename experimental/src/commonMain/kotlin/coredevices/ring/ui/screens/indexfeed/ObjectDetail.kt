@file:OptIn(ExperimentalTime::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package coredevices.ring.ui.screens.indexfeed

import coredevices.ring.ui.relativeTime
import CoreNav
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coredevices.indexai.data.entity.LocalRecording
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_NOTES_SELF_ID
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_SHOPPING_ID
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_TODOS_ID
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.ring.ui.theme.IndexTheme
import coredevices.ring.ui.theme.IndexThemeHost
import coredevices.ring.ui.theme.indexTextEntryStyle
import coredevices.ring.ui.viewmodel.ObjectDetailViewModel
import coredevices.ring.ui.viewmodel.kindLabel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Detail page for a single item (note / reminder / scheduled / message /
 * answer / action_log) or for a list of items. Routes by kind into
 * [ItemView] or [ListView].
 */
@Composable
fun ObjectDetail(coreNav: CoreNav, objectId: String, startEditing: Boolean = false) {
    val snackbarHostState = remember { SnackbarHostState() }
    val vm = koinViewModel<ObjectDetailViewModel> { parametersOf(objectId, snackbarHostState) }
    val state by vm.state.collectAsStateWithLifecycle()

    IndexThemeHost {
        val colors = IndexTheme.colors
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = colors.surface,
        ) { insets ->
            Box(modifier = Modifier.padding(insets).fillMaxSize().background(colors.surface)) {
                when (val s = state) {
                    is ObjectDetailViewModel.UiState.Loading -> {
                        DetailTopBar(title = "", coreNav = coreNav, right = {})
                    }
                    is ObjectDetailViewModel.UiState.NotFound -> {
                        Column {
                            DetailTopBar(title = "Not found", coreNav = coreNav, right = {})
                            Text(
                                "This item is gone.",
                                modifier = Modifier.padding(24.dp),
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    is ObjectDetailViewModel.UiState.ItemView -> ItemView(s, coreNav, vm)
                    is ObjectDetailViewModel.UiState.ListView -> ListView(s, coreNav, vm, startEditing)
                }
            }
        }
    }
}

// Item-detail composables live in ObjectItemDetail.kt.
// List-detail composables live in ObjectListDetail.kt.


// ── Top bars + small bits ──────────────────────────────────────────────

@Composable
internal fun DetailTopBar(
    title: String,
    coreNav: CoreNav,
    right: @Composable () -> Unit,
    titleSlot: (@Composable () -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    onBack: () -> Unit = coreNav::goBack,
) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(colors.surface)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Default.ArrowBack, "Back", tint = colors.onSurface)
        }
        Box(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
            if (titleSlot != null) {
                titleSlot()
            } else {
                Text(
                    title,
                    color = colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .let { if (onTitleClick != null) it.clickable { onTitleClick() } else it },
                )
            }
        }
        right()
    }
}

@Composable
internal fun ListSearchTopBar(
    value: String,
    onChange: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = IndexTheme.colors
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(colors.surfaceContainerLowest)
                .border(1.5.dp, colors.outlineVariant, RoundedCornerShape(percent = 50))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                singleLine = true,
                textStyle = TextStyle(color = colors.onSurface, fontSize = 15.sp).indexTextEntryStyle(),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                }),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text("Search…", color = colors.onSurfaceVariant, fontSize = 15.sp)
                    }
                    inner()
                },
            )
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Clear", tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        }
        Text(
            "Cancel",
            color = colors.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { onCancel() }.padding(8.dp),
        )
    }
}

/** Outlined sort pill that cycles through three states:
 *  - `Newest`: ↓ icon, "Newest" label (createdAt desc)
 *  - `Oldest`: ↑ icon, "Oldest" label (createdAt asc)
 *  - `DueDate`: clock icon, "Due date" label (most-overdue / soonest-due first) */
@Composable
internal fun SortPill(sort: ObjectDetailViewModel.ListSort, onClick: () -> Unit) {
    val colors = IndexTheme.colors
    val (icon, label) = when (sort) {
        ObjectDetailViewModel.ListSort.Newest -> Icons.Default.ArrowDownward to "Newest"
        ObjectDetailViewModel.ListSort.Oldest -> Icons.Default.ArrowUpward to "Oldest"
        ObjectDetailViewModel.ListSort.DueDate -> Icons.Outlined.AccessTime to "Due date"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(percent = 50))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// `relativeTime` and `relativeTimeWithCreated` (identical body) moved to
// `coredevices.ring.ui.util` so home + index-feed screens share one impl.

internal fun relativeTimeWithCreated(at: Instant): String = relativeTime(at)

internal fun formatShortDateTime(at: Instant): String {
    // TODO: localize — for now match prototype style "May 24 · 10:41pm"
    val ldt = at.toLocalDateTimeOrNull() ?: return ""
    val month = monthAbbrev(ldt.monthNumber)
    val day = ldt.dayOfMonth
    val hour12 = (ldt.hour % 12).let { if (it == 0) 12 else it }
    val ampm = if (ldt.hour < 12) "am" else "pm"
    val mm = ldt.minute.toString().padStart(2, '0')
    return "$month $day · $hour12:$mm$ampm"
}

internal fun formatLongDateTime(at: Instant): String = formatShortDateTime(at)

internal fun monthAbbrev(m: Int): String = when (m) {
    1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"; 5 -> "May"; 6 -> "Jun"
    7 -> "Jul"; 8 -> "Aug"; 9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; else -> "Dec"
}

private data class LDT(val monthNumber: Int, val dayOfMonth: Int, val hour: Int, val minute: Int)
private fun Instant.toLocalDateTimeOrNull(): LDT? = runCatching {
    val ldt = this.toLocalDateTime(TimeZone.currentSystemDefault())
    LDT(ldt.monthNumber, ldt.dayOfMonth, ldt.hour, ldt.minute)
}.getOrNull()
