@file:OptIn(ExperimentalTime::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package coredevices.ring.ui.screens.indexfeed

import CoreNav
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coredevices.indexai.data.entity.LocalRecording
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.indexfeed.fields
import coredevices.ring.data.entity.room.indexfeed.fieldsJson
import coredevices.ring.data.entity.room.indexfeed.kind
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_NOTES_SELF_ID
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_TODOS_ID
import coredevices.ring.ui.components.feed.TodoCheckCircle
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.util.relativeTime
import coredevices.ring.ui.theme.IndexTheme
import coredevices.ring.ui.theme.indexTextEntryStyle
import coredevices.ring.ui.theme.toggleButtonColors
import coredevices.ring.ui.viewmodel.ObjectDetailViewModel
import coredevices.ring.ui.viewmodel.kindLabel
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

// Item kind detail (hero card view) — split out of ObjectDetail.kt
// to keep that file focused on the route entry + chrome.

@Composable
internal fun ItemView(
    s: ObjectDetailViewModel.UiState.ItemView,
    coreNav: CoreNav,
    vm: ObjectDetailViewModel,
) {
    val it = s.item
    val colors = IndexTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    val title = s.parentList?.title?.takeIf { it.isNotBlank() } ?: kindLabel(it.kind)

    val reminderMeta = it.metadata as? coredevices.indexai.data.entity.ItemDocument.ItemMetadata.Reminder
    val notifyBeforeMillis = reminderMeta?.notifyBeforeMillis
    val canRemoveExtraNotification = notifyBeforeMillis != null

    var draftTitle by remember(it.firestoreId) { mutableStateOf(it.title) }
    var draftBody by remember(it.firestoreId) { mutableStateOf(it.body) }
    var draftKind by remember(it.firestoreId) { mutableStateOf(it.kind) }
    var kindTouched by remember(it.firestoreId) { mutableStateOf(false) }
    var draftCreatedAt by remember(it.firestoreId) { mutableStateOf(it.createdAt) }
    var createdAtTouched by remember(it.firestoreId) { mutableStateOf(false) }
    var draftDueAt by remember(it.firestoreId) { mutableStateOf(it.dueAt) }
    var dueAtTouched by remember(it.firestoreId) { mutableStateOf(false) }
    var draftParentListIds by remember(it.firestoreId) { mutableStateOf(it.parentListIds()) }
    var listsTouched by remember(it.firestoreId) { mutableStateOf(false) }
    var deleting by remember(it.firestoreId) { mutableStateOf(false) }
    var reminderDialogOpen by remember(it.firestoreId) { mutableStateOf(false) }
    var flushRequested by remember(it.firestoreId) { mutableStateOf(0) }
    val allLists by vm.allLists.collectAsStateWithLifecycle()

    // rememberUpdatedState wrappers so the auto-save lambda captured by
    // DisposableEffect / lifecycle observer always reads the LATEST draft,
    // not the one from when the effect was registered.
    val latestTitle = androidx.compose.runtime.rememberUpdatedState(draftTitle)
    val latestBody = androidx.compose.runtime.rememberUpdatedState(draftBody)
    val latestKind = androidx.compose.runtime.rememberUpdatedState(draftKind)
    val latestKindTouched = androidx.compose.runtime.rememberUpdatedState(kindTouched)
    val latestCreatedAt = androidx.compose.runtime.rememberUpdatedState(draftCreatedAt)
    val latestCreatedAtTouched = androidx.compose.runtime.rememberUpdatedState(createdAtTouched)
    val latestDueAt = androidx.compose.runtime.rememberUpdatedState(draftDueAt)
    val latestDueTouched = androidx.compose.runtime.rememberUpdatedState(dueAtTouched)
    val latestLists = androidx.compose.runtime.rememberUpdatedState(draftParentListIds)
    val latestListsTouched = androidx.compose.runtime.rememberUpdatedState(listsTouched)
    val latestOriginalTitle = androidx.compose.runtime.rememberUpdatedState(it.title)
    val latestOriginalBody = androidx.compose.runtime.rememberUpdatedState(it.body)
    val latestDeleting = androidx.compose.runtime.rememberUpdatedState(deleting)

    val changeKind: (String) -> Unit = { newKind ->
        // Only relocate parents when the kind crosses the todos-domain
        // boundary, comparing against the CURRENT draft kind so a multi-step
        // change like note → reminder → checklist relocates on each cross.
        // Switching note ↔ checklist keeps the user's actual parent list.
        val oldKind = draftKind
        draftKind = newKind
        kindTouched = true
        val isTodoDomain: (String) -> Boolean = { k ->
            k == "reminder" || k == "scheduled"
        }
        if (isTodoDomain(oldKind) != isTodoDomain(newKind)) {
            listsTouched = true
            draftParentListIds = defaultParentListsForKind(newKind)
        }
        if (newKind != "reminder" && newKind != "scheduled") {
            draftDueAt = null
            dueAtTouched = true
        }
    }

    val flushDraft: () -> Unit = flushDraft@{
        if (latestDeleting.value) return@flushDraft
        val titleChanged = latestTitle.value.trim() != latestOriginalTitle.value.trim()
        val bodyChanged = latestBody.value != latestOriginalBody.value
        val dirty = titleChanged || bodyChanged || latestKindTouched.value ||
            latestCreatedAtTouched.value || latestDueTouched.value || latestListsTouched.value
        if (dirty) {
            vm.patchItem(
                title = latestTitle.value,
                body = latestBody.value,
                kind = if (latestKindTouched.value) latestKind.value else null,
                createdAt = if (latestCreatedAtTouched.value) latestCreatedAt.value else null,
                dueAt = if (latestDueTouched.value) ObjectDetailViewModel.DueAtChange.Set(latestDueAt.value)
                        else ObjectDetailViewModel.DueAtChange.NoChange,
                parentListIds = if (latestListsTouched.value) latestLists.value else null,
            )
        }
    }

    // Type changes save at once rather than on leave; the effect runs after
    // recomposition so the latest* wrappers have caught up with the drafts.
    LaunchedEffect(flushRequested) {
        if (flushRequested > 0) flushDraft()
    }

    if (reminderDialogOpen) {
        DueAtPickerFlow(
            initial = null,
            onPicked = { dueAt ->
                reminderDialogOpen = false
                changeKind("reminder")
                draftDueAt = dueAt
                dueAtTouched = true
                flushRequested++
            },
            onDismiss = { reminderDialogOpen = false },
        )
    }

    // Save on dispose (back nav, navigating to another item, etc.).
    androidx.compose.runtime.DisposableEffect(it.firestoreId) {
        onDispose { flushDraft() }
    }
    // Save on app pause/stop (home button, swipe-to-recents).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) flushDraft()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(
            title = title,
            coreNav = coreNav,
            right = {
                // Wrapping the IconButton + DropdownMenu in a Box anchors
                // the menu to the icon's bounds; without it the menu
                // anchors to the right-slot start and renders on the left
                // side of the top bar (May 8 fix).
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, "More", tint = colors.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete ${kindLabel(it.kind).lowercase()}", color = colors.error) },
                            onClick = {
                                menuOpen = false
                                deleting = true
                                // Pop back to the existing parent list already on the
                                // stack. Using replaceWith here pushed a *new* parent
                                // instance on every delete, stacking up duplicates so
                                // back would cycle through the same page N times
                                // (MOB-8492). The parent list filters out deleted = 0,
                                // so the removed item disappears automatically.
                                vm.deleteThis { coreNav.goBack() }
                            },
                        )
                    }
                }
            },
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            item("hero") {
                // Always render the editable hero — Apple Notes style.
                // Auto-saved on dispose / app stop via flushDraft().
                ItemHeroEdit(
                    kind = draftKind,
                    title = draftTitle,
                    body = draftBody,
                    createdAt = draftCreatedAt,
                    dueAt = draftDueAt,
                    notifyBeforeMillis = notifyBeforeMillis,
                    canRemoveExtraNotification = canRemoveExtraNotification,
                    onRemoveExtraNotification = { vm.removeExtraNotification() },
                    allLists = allLists,
                    selectedListIds = draftParentListIds,
                    onKind = { newKind ->
                        when {
                            newKind == draftKind -> Unit
                            newKind == "reminder" -> reminderDialogOpen = true
                            else -> {
                                changeKind(newKind)
                                flushRequested++
                            }
                        }
                    },
                    onTitle = { draftTitle = it },
                    onBody = { draftBody = it },
                    onCreatedAt = { newCreatedAt ->
                        draftCreatedAt = newCreatedAt
                        createdAtTouched = true
                    },
                    onDueAt = { newDue ->
                        draftDueAt = newDue
                        dueAtTouched = true
                    },
                    onToggleList = { listId ->
                        listsTouched = true
                        draftParentListIds = if (listId in draftParentListIds)
                            draftParentListIds - listId
                        else draftParentListIds + listId
                    },
                )
            }
            s.sourceRecording?.let { rec ->
                item("source-label") {
                    Text(
                        "SOURCE",
                        color = colors.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.0.sp,
                        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 8.dp),
                    )
                }
                item("source-card") {
                    SourceRecordingCard(
                        recording = rec,
                        transcription = s.sourceTranscription,
                        onClick = { coreNav.navigateTo(RingRoutes.RecordingDetails(rec.id)) },
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

private fun defaultParentListsForKind(kind: String): List<String> =
    if (kind == "reminder" || kind == "scheduled") listOf(LIST_TODOS_ID)
    else listOf(LIST_NOTES_SELF_ID)

@Composable
private fun ItemHeroCard(
    it: CachedItem,
    vm: ObjectDetailViewModel,
    showCreatedMeta: Boolean = true,
) {
    val colors = IndexTheme.colors
    val canToggle = it.kind == "reminder" || it.kind == "scheduled"
    // `it.fields()` parses fieldsJson once; helper lives on the data
    // class so the View doesn't import kotlinx.serialization directly.
    val fields = remember(it.fieldsJson) { it.fields() }

    val subtitle = buildString {
        when (it.kind) {
            // Reminder hero shows "Due Apr 28 · 3:00pm" — the explicit verb
            // disambiguates it from a generic timestamp ("Created" stamp
            // lives in the meta row below when shown).
            "reminder" -> it.dueAt?.let { append("Due ${formatLongDateTime(it)}") }
            "scheduled" -> {
                // For timer/alarm the title already says "Timer · 20 min"
                // or "Alarm · 22:06"; repeating it as a subtitle is noise.
                // Leave the hero subtitle empty for those kinds — meta row
                // (or the SOURCE card below) carries the timestamp.
                val fk = fields["fireKind"]?.jsonPrimitive?.contentOrNull
                if (fk != "alarm" && fk != "timer") {
                    it.dueAt?.let { append("Due ${formatLongDateTime(it)}") }
                }
            }
            "answer" -> {
                val q = fields["question"]?.jsonPrimitive?.contentOrNull
                if (!q.isNullOrBlank()) {
                    append("Q&A capture\n")
                    append("Q: \"$q\"")
                }
            }
            "message" -> {
                val recip = fields["recipientName"]?.jsonPrimitive?.contentOrNull
                    ?: fields["contact"]?.jsonPrimitive?.contentOrNull
                val status = fields["status"]?.jsonPrimitive?.contentOrNull
                if (!recip.isNullOrBlank()) append("To $recip")
                if (!status.isNullOrBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(status.replaceFirstChar { it.uppercase() })
                }
            }
            else -> {}
        }
    }

    val createdRel = relativeTimeWithCreated(it.createdAt)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceContainerLowest)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (canToggle) {
                TodoCheckCircle(
                    done = it.done,
                    onToggle = { vm.toggleDone() },
                    size = coredevices.ring.ui.components.feed.Size.Large,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    it.title,
                    color = colors.onSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    lineHeight = 28.sp,
                    textDecoration = if (it.done) TextDecoration.LineThrough else TextDecoration.None,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        color = colors.onSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (it.body.isNotBlank()) {
                    Text(
                        it.body,
                        color = colors.onSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
        // The "KIND · Created Xh ago" meta row is suppressed when the
        // SOURCE recording card is rendered below this hero — the
        // recording's own "RECORDED · Xh AGO" header already tells the
        // user when it happened, and showing both would be a duplicate.
        if (!showCreatedMeta) return@Column
        Spacer(Modifier.height(14.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                kindLabel(it.kind).uppercase(),
                color = colors.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "·",
                color = colors.onSurfaceVariant,
                fontSize = 11.sp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Created $createdRel",
                color = colors.onSurfaceVariant,
                fontSize = 11.sp,
                letterSpacing = 0.4.sp,
            )
        }
    }
}

/** Inline edit form for an item's title, body, due-at and list membership.
 *  Mirrors the prototype's ObjectDetail edit mode. */
@OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
private fun ItemHeroEdit(
    kind: String,
    title: String,
    body: String,
    createdAt: Instant,
    dueAt: Instant?,
    notifyBeforeMillis: Long?,
    canRemoveExtraNotification: Boolean,
    onRemoveExtraNotification: () -> Unit,
    allLists: List<coredevices.ring.data.entity.room.indexfeed.CachedList>,
    selectedListIds: List<String>,
    onKind: (String) -> Unit,
    onTitle: (String) -> Unit,
    onBody: (String) -> Unit,
    onCreatedAt: (Instant) -> Unit,
    onDueAt: (Instant?) -> Unit,
    onToggleList: (String) -> Unit,
) {
    val colors = IndexTheme.colors
    val showsDue = kind == "reminder" || kind == "scheduled"
    val listOptions = if (showsDue) {
        emptyList()
    } else {
        allLists.filter { it.firestoreId != LIST_TODOS_ID }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceContainerLowest)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        BasicTextField(
            value = title,
            onValueChange = onTitle,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                color = colors.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                lineHeight = 28.sp,
            ).indexTextEntryStyle(),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            decorationBox = { inner ->
                if (title.isEmpty()) {
                    Text("Title", color = colors.onSurfaceVariant, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                inner()
            },
        )
        Spacer(Modifier.height(10.dp))
        if (showsDue) {
            DueAtRow(label = "Due", dueAt = dueAt, onChange = onDueAt)
            // Early heads-up notification, only for reminders that scheduled one and
            // that we can actually cancel (built-in reminders).
            if (kind == "reminder" && notifyBeforeMillis != null && canRemoveExtraNotification) {
                Spacer(Modifier.height(10.dp))
                EarlyNotificationRow(
                    notifyBeforeMillis = notifyBeforeMillis,
                    onRemove = onRemoveExtraNotification,
                )
            }
        } else {
            TimestampRow(timestamp = createdAt, onChange = onCreatedAt)
        }
        if (kind in setOf("note", "checklist", "reminder")) {
            Spacer(Modifier.height(14.dp))
            Text(
                "TYPE",
                color = colors.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
            TypeButtonRow(kind = kind, onKind = onKind)
        }
        if (!showsDue && listOptions.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                "LISTS",
                color = colors.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOptions.forEach { l ->
                    val selected = l.firestoreId in selectedListIds
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (selected) colors.redSurface else androidx.compose.ui.graphics.Color.Transparent)
                            .border(
                                1.dp,
                                if (selected) colors.chipOutline else colors.outlineVariant,
                                RoundedCornerShape(percent = 50),
                            )
                            .clickable { onToggleList(l.firestoreId) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        if (l.icon.isNotBlank()) {
                            Text(l.icon, fontSize = 12.sp)
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            l.title.ifBlank { "List" },
                            color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

        }
        Spacer(Modifier.height(14.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))
        Spacer(Modifier.height(12.dp))
        BasicTextField(
            value = body,
            onValueChange = onBody,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                color = colors.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            ).indexTextEntryStyle(),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            decorationBox = { inner ->
                if (body.isEmpty()) {
                    Text("Notes…", color = colors.onSurfaceVariant, fontSize = 14.sp)
                }
                inner()
            },
        )
    }
}

@Composable
private fun TimestampRow(timestamp: Instant, onChange: (Instant) -> Unit) {
    val colors = IndexTheme.colors
    var showPicker by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(10.dp))
            .clickable { showPicker = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text("Timestamp", color = colors.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.width(86.dp))
        Text(
            formatLongDateTime(timestamp),
            color = colors.onSurface,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
    }

    if (showPicker) {
        DueAtPickerFlow(
            initial = timestamp,
            onPicked = {
                showPicker = false
                onChange(it)
            },
            onDismiss = { showPicker = false },
        )
    }
}

/** Shows every type label when they fit; otherwise only the selected type keeps its label. */
@Composable
private fun TypeButtonRow(kind: String, onKind: (String) -> Unit) {
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        // The probe is measured but never placed; the visible row keeps one
        // composition across the fit/compact switch so the buttons' size
        // change animates instead of swapping.
        val unbounded = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
        val probe = subcompose("probe") { TypeButtons(kind, onKind, labelAll = true) }
            .first().measure(unbounded)
        val fits = probe.width <= constraints.maxWidth
        val row = subcompose("row") { TypeButtons(kind, onKind, labelAll = fits) }
            .first().measure(constraints)
        layout(constraints.maxWidth, row.height) { row.place(0, 0) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TypeButtons(kind: String, onKind: (String) -> Unit, labelAll: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        TypeButton(
            selected = kind == "note",
            label = "Note",
            icon = if (kind == "note") Icons.AutoMirrored.Filled.StickyNote2 else Icons.AutoMirrored.Outlined.StickyNote2,
            showLabel = labelAll,
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            onClick = { onKind("note") },
        )
        TypeButton(
            selected = kind == "checklist",
            label = "Checkbox",
            icon = if (kind == "checklist") Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
            showLabel = labelAll,
            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
            onClick = { onKind("checklist") },
        )
        TypeButton(
            selected = kind == "reminder",
            label = "Reminder",
            icon = if (kind == "reminder") Icons.Filled.Notifications else Icons.Outlined.Notifications,
            showLabel = labelAll,
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            onClick = { onKind("reminder") },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TypeButton(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    showLabel: Boolean,
    shapes: ToggleButtonShapes,
    onClick: () -> Unit,
) {
    val labelVisible = showLabel || selected
    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        shapes = shapes,
        colors = IndexTheme.toggleButtonColors(),
    ) {
        Icon(icon, contentDescription = if (labelVisible) null else label)
        AnimatedVisibility(labelVisible) {
            Row {
                Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                Text(label, maxLines = 1)
            }
        }
    }
}

/** Read-only row for a reminder's extra early notification, with a "Remove" action that
 *  cancels just that heads-up (leaving the main reminder in place). */
@Composable
private fun EarlyNotificationRow(notifyBeforeMillis: Long, onRemove: () -> Unit) {
    val colors = IndexTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text("Early alert", color = colors.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.width(86.dp))
        Text(
            "${formatLeadTime(notifyBeforeMillis)} before",
            color = colors.onSurface,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Remove",
            color = colors.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable { onRemove() }
                .padding(start = 12.dp),
        )
    }
}

/** "2h", "2h 30m", "30m" — the human lead time for an early reminder notification. */
private fun formatLeadTime(millis: Long): String {
    val d = millis.milliseconds
    return when {
        d.inWholeHours >= 1 -> {
            val hours = d.inWholeHours
            val minutes = (d - hours.hours).inWholeMinutes
            if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
        }
        d.inWholeMinutes >= 1 -> "${d.inWholeMinutes}m"
        else -> "${d.inWholeSeconds}s"
    }
}

@Composable
private fun SourceRecordingCard(
    recording: LocalRecording,
    transcription: String,
    onClick: () -> Unit,
) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.redSurface)
            .border(1.dp, colors.chipOutline, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.padding(top = 2.dp).size(28.dp).clip(CircleShape).background(colors.redSurface),
            contentAlignment = Alignment.Center,
        ) {
            RingGlyph(sizeDp = 14, color = colors.primary)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "RECORDED · ${relativeTime(recording.localTimestamp).uppercase()}",
                color = colors.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            )
            val text = transcription.takeIf { it.isNotBlank() }
                ?: recording.assistantTitle?.takeIf { it.isNotBlank() }
                ?: "Index Recording"
            Text(
                "\"$text\"",
                color = colors.onSurface,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Two concentric outlined circles + a small filled mic-pip dot at the top.
 *  Matches the prototype's `NIcon.ring` SVG (no filled center). */
@Composable
private fun RingGlyph(sizeDp: Int, color: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(sizeDp.dp)) {
        val s = this.size.minDimension / 24f
        val cx = 12f * s
        val cy = 12.5f * s
        drawCircle(
            color = color,
            radius = 8f * s,
            center = androidx.compose.ui.geometry.Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8f * s),
        )
        drawCircle(
            color = color.copy(alpha = 0.55f),
            radius = 4.5f * s,
            center = androidx.compose.ui.geometry.Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4f * s),
        )
        drawCircle(
            color = color,
            radius = 1.6f * s,
            center = androidx.compose.ui.geometry.Offset(cx, 4.2f * s),
        )
    }
}
