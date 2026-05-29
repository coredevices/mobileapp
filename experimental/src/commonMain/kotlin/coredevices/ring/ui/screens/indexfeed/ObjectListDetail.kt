@file:OptIn(ExperimentalTime::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package coredevices.ring.ui.screens.indexfeed

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coredevices.indexai.data.entity.LocalRecording
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.indexfeed.fields
import coredevices.ring.data.entity.room.indexfeed.fireKind
import coredevices.ring.data.entity.room.indexfeed.kind
import coredevices.ring.ui.components.feed.TodoCheckCircle
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

// List kind detail (children rows, sort/search, done section) — split
// out of ObjectDetail.kt for review-friendliness.

@Composable
internal fun ListView(
    s: ObjectDetailViewModel.UiState.ListView,
    coreNav: CoreNav,
    vm: ObjectDetailViewModel,
    startEditing: Boolean = false,
) {
    val colors = IndexTheme.colors
    val isTodos = s.list.firestoreId == LIST_TODOS_ID
    val inlineNoteEditor = !isTodos
    // Core seed lists (Notes to self, Todos, Shopping) are recreated by
    // DefaultListsBootstrap on next auth event if deleted, and ingest
    // routes items to them by stable id — soft-deleting them from the
    // UI is a footgun. Hide the Delete option for all three.
    val isCoreList = s.list.firestoreId in setOf(LIST_NOTES_SELF_ID, LIST_TODOS_ID, LIST_SHOPPING_ID)
    var searching by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember(s.list.firestoreId) { mutableStateOf(startEditing && !isTodos) }
    var draftTitle by remember(s.list.firestoreId) { mutableStateOf(s.list.title) }
    var draftIcon by remember(s.list.firestoreId) { mutableStateOf(s.list.icon) }
    val listTitleFocusRequester = remember(s.list.firestoreId) { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    androidx.compose.runtime.LaunchedEffect(s.list.title, s.list.icon) {
        // Refresh the draft when the list title changes from outside (e.g.
        // a Firestore sync) and we're not actively editing.
        if (!editing) {
            draftTitle = s.list.title
            draftIcon = s.list.icon
        }
    }
    LaunchedEffect(editing) {
        if (editing) {
            listTitleFocusRequester.requestFocus()
            keyboard?.show()
        }
    }
    // Items just-toggled-to-done linger in the active bucket with
    // strikethrough + faded opacity for ~600 ms so the user sees the
    // line-through animate before the row drops away. After that the
    // viewmodel removes them from animatingDoneIds and they move to
    // the done bucket. Mirrors the prototype's behaviour exactly.
    val animatingDoneIds by vm.animatingDoneIds.collectAsStateWithLifecycle()
    val active = s.children.filter { !it.done || it.firestoreId in animatingDoneIds }
    val done = s.children.filter { it.done && it.firestoreId !in animatingDoneIds }
    var focusItemId by remember(s.list.firestoreId) { mutableStateOf<String?>(null) }

    fun cancelEdit() {
        editing = false
        draftTitle = s.list.title
        draftIcon = s.list.icon
    }
    fun saveEdit() {
        val t = draftTitle.trim()
        if (t.isNotBlank()) vm.renameList(t, draftIcon.trim())
        editing = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (searching) {
            ListSearchTopBar(
                value = s.query,
                onChange = vm::setListQuery,
                onCancel = { searching = false; vm.setListQuery("") },
            )
        } else {
            DetailTopBar(
                title = s.list.title.ifBlank { "List" },
                titleSlot = if (editing) {
                    {
                        // Inline rename input. Replaces the title text while
                        // editing — Save is the only top-bar right slot.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ListEmojiPicker(
                                value = draftIcon,
                                onChange = { draftIcon = it },
                            )
                            Spacer(Modifier.width(8.dp))
                            BasicTextField(
                                value = draftTitle,
                                onValueChange = { draftTitle = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(listTitleFocusRequester),
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = colors.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.1).sp,
                                ).indexTextEntryStyle(),
                                cursorBrush = SolidColor(colors.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { saveEdit() }),
                            )
                        }
                    }
                } else if (!isTodos) {
                    {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    editing = true
                                    draftTitle = s.list.title
                                    draftIcon = s.list.icon
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (s.list.icon.isNotBlank()) {
                                Text(
                                    s.list.icon,
                                    color = colors.onSurface,
                                    fontSize = 18.sp,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                s.list.title.ifBlank { "List" },
                                color = colors.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else null,
                onTitleClick = if (!isTodos && !editing) ({
                    editing = true
                    draftTitle = s.list.title
                    draftIcon = s.list.icon
                }) else null,
                coreNav = coreNav,
                right = {
                    if (editing) {
                        Text(
                            "Save",
                            color = colors.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { saveEdit() }.padding(8.dp),
                        )
                    } else {
                        SortPill(sort = s.sort, onClick = vm::toggleSort)
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Default.Search, "Search", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, "More", tint = colors.onSurfaceVariant)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (!isTodos) {
                                    DropdownMenuItem(
                                        text = { Text("Rename list") },
                                        onClick = {
                                            menuOpen = false
                                            editing = true
                                            draftTitle = s.list.title
                                            draftIcon = s.list.icon
                                        },
                                    )
                                }
                                if (!isCoreList) {
                                    DropdownMenuItem(
                                        text = { Text("Delete list", color = colors.error) },
                                        onClick = {
                                            menuOpen = false
                                            vm.deleteThis { coreNav.goBack() }
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (active.isEmpty() && done.isEmpty()) {
                item("empty") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (s.query.isNotBlank()) "No matches." else "Nothing here yet.",
                            color = colors.onSurfaceVariant,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            if (inlineNoteEditor && s.query.isBlank() && s.sort == ObjectDetailViewModel.ListSort.Newest) {
                item("new-note-editor-top") {
                    NewNoteRow(
                        requestFocus = focusItemId == NEW_NOTE_FOCUS_ID,
                        onFocusConsumed = { focusItemId = null },
                        onCreate = { text ->
                            vm.createChildItem(text) { focusItemId = it }
                        },
                    )
                }
            }
            items(active.size, key = { active[it].firestoreId }) { idx ->
                val child = active[idx]
                if (inlineNoteEditor) {
                    EditableNoteRow(
                        child = child,
                        requestFocus = focusItemId == child.firestoreId,
                        onFocusConsumed = { focusItemId = null },
                        onSave = { text -> vm.patchChildItem(child.firestoreId, title = text) },
                        onDelete = { vm.deleteChildItem(child.firestoreId) },
                        onEnter = { text ->
                            vm.patchChildItem(child.firestoreId, title = text)
                            focusItemId = NEW_NOTE_FOCUS_ID
                        },
                        onOpen = {
                            coreNav.navigateTo(RingRoutes.ObjectDetails(child.firestoreId))
                        },
                    )
                } else {
                    ListChildRow(
                        child = child,
                        isChecklistKind = s.list.listKind == "checklist" || isTodos,
                        onToggle = { vm.toggleChildDone(child) },
                        onClick = { coreNav.navigateTo(RingRoutes.ObjectDetails(child.firestoreId)) },
                    )
                }
            }
            if (inlineNoteEditor && s.query.isBlank() && s.sort != ObjectDetailViewModel.ListSort.Newest) {
                item("new-note-editor-bottom") {
                    NewNoteRow(
                        requestFocus = focusItemId == NEW_NOTE_FOCUS_ID,
                        onFocusConsumed = { focusItemId = null },
                        onCreate = { text ->
                            vm.createChildItem(text) { focusItemId = it }
                        },
                    )
                }
            }
            if (done.isNotEmpty()) {
                item("done-section") {
                    DoneSection(
                        done = done,
                        expanded = s.showDone,
                        listKind = s.list.listKind,
                        isTodos = isTodos,
                        onToggleExpand = { vm.setShowDone(!s.showDone) },
                        onChildToggle = vm::toggleChildDone,
                        onChildClick = { coreNav.navigateTo(RingRoutes.ObjectDetails(it.firestoreId)) },
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun ListChildRow(
    child: CachedItem,
    isChecklistKind: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = IndexTheme.colors
    val showCheckbox = child.kind == "reminder" || child.kind == "scheduled" || child.kind == "checklist" || isChecklistKind
    val hasSubtitle = childSubtitle(child) != null
    // Strike-through linger fade. When `child.done` flips true the row
    // crossfades to 0.45 opacity over 600 ms; the viewmodel removes it
    // from the active bucket at the end of that window. Mirrors the
    // prototype's `transition: opacity 0.6s ease`.
    val rowAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (child.done) 0.45f else 1f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600),
        label = "child-row-fade",
    )
    Column(modifier = Modifier.graphicsLayer { alpha = rowAlpha }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(start = 22.dp, end = 22.dp, top = 7.dp, bottom = 7.dp),
            // Center the circle on the title's first-line midline. Two-line
            // titles still center within their column — the circle sits beside
            // the leading text block, not pinned to the title's top.
            verticalAlignment = if (hasSubtitle) Alignment.Top else Alignment.CenterVertically,
        ) {
            if (showCheckbox) {
                TodoCheckCircle(
                    done = child.done,
                    onToggle = onToggle,
                    // Tiny top nudge only when there's a subtitle, so the
                    // circle sits on the title row instead of straddling
                    // both lines. Single-line items use CenterVertically
                    // and need no nudge.
                    modifier = if (hasSubtitle) Modifier.padding(top = 1.dp) else Modifier,
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    child.title,
                    color = colors.onSurface,
                    fontSize = 15.sp,
                    // Prototype uses default fontWeight (400). Was Medium —
                    // looked too bold next to the prototype.
                    fontWeight = FontWeight.Normal,
                    letterSpacing = (-0.1).sp,
                    lineHeight = 18.sp,
                    textDecoration = if (child.done) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                childSubtitle(child)?.let { sub ->
                    Text(
                        sub,
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier.padding(top = 0.dp),
                    )
                }
            }
        }
        // Per Eric's request, dropped the row divider here to test the
        // tighter, divider-less look. Easy to re-enable if it reads as
        // too dense.
    }
}

private const val NEW_NOTE_FOCUS_ID = "__new_note__"

@Composable
private fun ListEmojiPicker(
    value: String,
    onChange: (String) -> Unit,
) {
    val colors = IndexTheme.colors
    var open by remember { mutableStateOf(false) }
    var query by remember(open) { mutableStateOf("") }
    val filtered = remember(query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) {
            ALL_EMOJI_OPTIONS
        } else {
            ALL_EMOJI_OPTIONS.filter { option ->
                option.emoji == q ||
                    option.name.lowercase().contains(q) ||
                    option.group.lowercase().contains(q) ||
                    option.subgroup.lowercase().contains(q)
            }
        }
    }
    Box {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(8.dp))
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                value.trim(),
                color = colors.onSurface,
                fontSize = 19.sp,
                maxLines = 1,
            )
        }
        if (open) {
            Dialog(onDismissRequest = { open = false }) {
                Column(
                    modifier = Modifier
                        .width(340.dp)
                        .height(500.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(18.dp))
                        .padding(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Emoji",
                            color = colors.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "No emoji",
                            color = colors.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onChange("")
                                    open = false
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceContainerLowest)
                            .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp),
                        textStyle = TextStyle(
                            color = colors.onSurface,
                            fontSize = 14.sp,
                        ).indexTextEntryStyle(),
                        cursorBrush = SolidColor(colors.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        decorationBox = { inner ->
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.weight(1f)) {
                                    if (query.isBlank()) {
                                        Text(
                                            "Search emojis",
                                            color = colors.onSurfaceVariant,
                                            fontSize = 14.sp,
                                        )
                                    }
                                    inner()
                                }
                            }
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(filtered.size) { index ->
                            val option = filtered[index]
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onChange(option.emoji)
                                        open = false
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(option.emoji, fontSize = 21.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditableNoteRow(
    child: CachedItem,
    requestFocus: Boolean,
    onFocusConsumed: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onEnter: (String) -> Unit,
    onOpen: () -> Unit,
) {
    val colors = IndexTheme.colors
    var draft by remember(child.firestoreId) { mutableStateOf(child.title) }
    var hadFocus by remember(child.firestoreId) { mutableStateOf(false) }
    var isFocused by remember(child.firestoreId) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    fun flush() {
        val clean = draft.trim()
        if (clean.isNotBlank() && clean != child.title.trim()) onSave(clean)
    }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            onFocusConsumed()
        }
    }
    DisposableEffect(child.firestoreId) {
        onDispose { flush() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 14.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = draft,
            onValueChange = { next ->
                if ('\n' in next) {
                    draft = next.substringBefore('\n')
                    onEnter(draft)
                } else {
                    draft = next
                }
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (hadFocus && !state.isFocused) flush()
                    isFocused = state.isFocused
                    hadFocus = state.isFocused
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            onEnter(draft)
                            true
                        }
                        Key.Backspace -> {
                            if (draft.isEmpty()) {
                                onDelete()
                                true
                            } else false
                        }
                        else -> false
                    }
                },
            textStyle = TextStyle(
                color = colors.onSurface,
                fontSize = 15.sp,
                lineHeight = 18.sp,
            ).indexTextEntryStyle(),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            decorationBox = { inner ->
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    inner()
                }
            },
        )
        IconButton(
            onClick = {
                flush()
                onOpen()
            },
            enabled = isFocused,
            modifier = Modifier
                .size(30.dp)
                .graphicsLayer { alpha = if (isFocused) 1f else 0f },
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open",
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun NewNoteRow(
    requestFocus: Boolean,
    onFocusConsumed: () -> Unit,
    onCreate: (String) -> Unit,
) {
    val colors = IndexTheme.colors
    var draft by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    fun createFromDraft() {
        val clean = draft.trim()
        if (clean.isBlank()) return
        draft = ""
        onCreate(clean)
    }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            onFocusConsumed()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .padding(start = 22.dp, end = 44.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = "Add note",
            tint = colors.onSurfaceVariant.copy(alpha = 0.58f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = draft,
            onValueChange = { next ->
                if ('\n' in next) {
                    draft = next.substringBefore('\n')
                    createFromDraft()
                } else {
                    draft = next
                }
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { if (!it.isFocused) createFromDraft() }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                        createFromDraft()
                        true
                    } else {
                        false
                    }
                },
            textStyle = TextStyle(
                color = colors.onSurface,
                fontSize = 15.sp,
                lineHeight = 18.sp,
            ).indexTextEntryStyle(),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            decorationBox = { inner ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (draft.isEmpty()) {
                        Text("Add note", color = colors.onSurfaceVariant.copy(alpha = 0.58f), fontSize = 15.sp)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun DoneSection(
    done: List<CachedItem>,
    expanded: Boolean,
    listKind: String,
    isTodos: Boolean,
    onToggleExpand: () -> Unit,
    onChildToggle: (CachedItem) -> Unit,
    onChildClick: (CachedItem) -> Unit,
) {
    val colors = IndexTheme.colors
    Column(modifier = Modifier.padding(top = 18.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { onToggleExpand() }
                .padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Text(
                if (expanded) "▾" else "▸",
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Done · ${done.size}",
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (expanded) {
            done.forEach { c ->
                ListChildRow(
                    child = c,
                    isChecklistKind = listKind == "checklist" || isTodos,
                    onToggle = { onChildToggle(c) },
                    onClick = { onChildClick(c) },
                )
            }
        }
    }
}

private fun childSubtitle(c: CachedItem): String? {
    fun dueLabel(due: Instant): String {
        // Overdue takes precedence over the formatted date so the user
        // sees urgency at a glance — same logic as the home Todos
        // preview's TaskRow.
        val now = Clock.System.now()
        return if (due.toEpochMilliseconds() < now.toEpochMilliseconds()) "Overdue"
            else formatShortDateTime(due)
    }
    return when (c.kind) {
        "reminder" -> c.dueAt?.let(::dueLabel)
        "scheduled" -> when (c.fireKind()) {
            // Timer/alarm titles already include the value (e.g. "Timer · 20 min")
            // so a subtitle would just duplicate it. Suppress.
            "alarm", "timer" -> null
            else -> c.dueAt?.let(::dueLabel)
        }
        else -> null
    }
}
