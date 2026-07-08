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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
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
    var deleteDialogOpen by remember { mutableStateOf(false) }
    var deleteAction by remember(s.list.firestoreId) { mutableStateOf(DeleteListAction.MoveItems) }
    var deleteTargetListId by remember(s.list.firestoreId) { mutableStateOf(LIST_NOTES_SELF_ID) }
    var editing by remember(s.list.firestoreId) { mutableStateOf(startEditing && !isTodos) }
    val initialNewListRename = startEditing && s.list.title == "New list"
    var draftTitle by remember(s.list.firestoreId) {
        val initialTitle = if (initialNewListRename) "" else s.list.title
        mutableStateOf(TextFieldValue(initialTitle, selection = TextRange(initialTitle.length)))
    }
    var draftIcon by remember(s.list.firestoreId) { mutableStateOf(s.list.icon) }
    var titleHadFocus by remember(s.list.firestoreId) { mutableStateOf(false) }
    var emojiPickerOpen by remember(s.list.firestoreId) { mutableStateOf(false) }
    var saveAfterEmojiPickerCloses by remember(s.list.firestoreId) { mutableStateOf(false) }
    val listTitleFocusRequester = remember(s.list.firestoreId) { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    androidx.compose.runtime.LaunchedEffect(s.list.title, s.list.icon) {
        // Refresh the draft when the list title changes from outside (e.g.
        // a Firestore sync) and we're not actively editing.
        if (!editing) {
            draftTitle = TextFieldValue(s.list.title, selection = TextRange(s.list.title.length))
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
    val deleteTargetLists = remember(s.allLists, s.list.firestoreId) {
        s.allLists
            .filter { !it.deleted && it.firestoreId != s.list.firestoreId && it.firestoreId != LIST_TODOS_ID }
            .sortedWith(
                compareBy<coredevices.ring.data.entity.room.indexfeed.CachedList> {
                    if (it.firestoreId == LIST_NOTES_SELF_ID) 0 else 1
                }.thenBy { it.title.lowercase() },
            )
    }
    LaunchedEffect(deleteDialogOpen, deleteTargetLists) {
        if (deleteDialogOpen) {
            val ids = deleteTargetLists.map { it.firestoreId }.toSet()
            if (deleteTargetListId !in ids) {
                deleteTargetListId = deleteTargetLists.firstOrNull()?.firestoreId ?: LIST_NOTES_SELF_ID
            }
            if (deleteTargetLists.isEmpty()) {
                deleteAction = DeleteListAction.DeleteItems
            }
        }
    }

    fun commitListEdit(titleText: String = draftTitle.text, iconText: String = draftIcon) {
        val t = titleText.trim()
        val icon = iconText.trim()
        if (t.isNotBlank() && (t != s.list.title || icon != s.list.icon)) {
            vm.renameList(t, icon)
        }
    }
    fun saveEdit() {
        commitListEdit()
        editing = false
        titleHadFocus = false
        emojiPickerOpen = false
        saveAfterEmojiPickerCloses = false
    }
    fun startTitleEdit() {
        draftTitle = TextFieldValue(s.list.title, selection = TextRange(s.list.title.length))
        draftIcon = s.list.icon
        titleHadFocus = false
        emojiPickerOpen = false
        saveAfterEmojiPickerCloses = false
        editing = true
    }
    fun handleTitleFocusLost() {
        if (emojiPickerOpen) {
            saveAfterEmojiPickerCloses = true
        } else {
            saveEdit()
        }
    }
    fun handleEmojiPickerOpenChange(open: Boolean) {
        emojiPickerOpen = open
        if (!open && saveAfterEmojiPickerCloses && editing) {
            saveEdit()
        }
    }
    val latestEditing by rememberUpdatedState(editing)
    val latestDraftTitle by rememberUpdatedState(draftTitle)
    val latestDraftIcon by rememberUpdatedState(draftIcon)
    DisposableEffect(s.list.firestoreId) {
        onDispose {
            if (latestEditing) {
                commitListEdit(latestDraftTitle.text, latestDraftIcon)
            }
        }
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
                                onOpenChange = ::handleEmojiPickerOpenChange,
                            )
                            Spacer(Modifier.width(8.dp))
                            BasicTextField(
                                value = draftTitle,
                                onValueChange = { draftTitle = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(listTitleFocusRequester)
                                    .onFocusChanged { state ->
                                        if (titleHadFocus && !state.isFocused) handleTitleFocusLost()
                                        titleHadFocus = state.isFocused
                                    },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = colors.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ).indexTextEntryStyle(),
                                cursorBrush = SolidColor(colors.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { saveEdit() }),
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (draftTitle.text.isBlank()) {
                                            Text(
                                                "New list",
                                                color = colors.onSurfaceVariant.copy(alpha = 0.55f),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                        }
                    }
                } else if (!isTodos) {
                    {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { startTitleEdit() }
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
                                style = TextStyle(
                                    color = colors.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ).indexTextEntryStyle(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else null,
                onTitleClick = if (!isTodos && !editing) ({
                    startTitleEdit()
                }) else null,
                coreNav = coreNav,
                onBack = {
                    if (editing) saveEdit()
                    coreNav.goBack()
                },
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
                                            startTitleEdit()
                                        },
                                    )
                                }
                                if (!isCoreList) {
                                    DropdownMenuItem(
                                        text = { Text("Delete list", color = colors.error) },
                                        onClick = {
                                            menuOpen = false
                                            if (s.childCount == 0) {
                                                vm.deleteListAndChildren {
                                                    coreNav.replaceWith(RingRoutes.AllLists)
                                                }
                                                return@DropdownMenuItem
                                            }
                                            deleteTargetListId = deleteTargetLists.firstOrNull {
                                                it.firestoreId == LIST_NOTES_SELF_ID
                                            }?.firestoreId ?: deleteTargetLists.firstOrNull()?.firestoreId.orEmpty()
                                            deleteAction = if (deleteTargetLists.isNotEmpty()) {
                                                DeleteListAction.MoveItems
                                            } else {
                                                DeleteListAction.DeleteItems
                                            }
                                            deleteDialogOpen = true
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
                            vm.createChildItem(text) { focusItemId = NEW_NOTE_FOCUS_ID }
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
                        onToggle = { vm.toggleChildDone(child) },
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
                            vm.createChildItem(text) { focusItemId = NEW_NOTE_FOCUS_ID }
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

    if (deleteDialogOpen) {
        DeleteListDialog(
            listTitle = s.list.title.ifBlank { "this list" },
            childCount = s.childCount,
            targetLists = deleteTargetLists,
            selectedAction = deleteAction,
            selectedTargetListId = deleteTargetListId,
            onAction = { deleteAction = it },
            onTargetList = { deleteTargetListId = it },
            onDismiss = { deleteDialogOpen = false },
            onConfirm = {
                deleteDialogOpen = false
                if (deleteAction == DeleteListAction.MoveItems && deleteTargetListId.isNotBlank()) {
                    vm.deleteListMovingChildren(deleteTargetListId) {
                        coreNav.replaceWith(RingRoutes.AllLists)
                    }
                } else {
                    vm.deleteListAndChildren {
                        coreNav.replaceWith(RingRoutes.AllLists)
                    }
                }
            },
        )
    }
}

private enum class DeleteListAction { MoveItems, DeleteItems }

@Composable
private fun DeleteListDialog(
    listTitle: String,
    childCount: Int,
    targetLists: List<coredevices.ring.data.entity.room.indexfeed.CachedList>,
    selectedAction: DeleteListAction,
    selectedTargetListId: String,
    onAction: (DeleteListAction) -> Unit,
    onTargetList: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = IndexTheme.colors
    val selectedTarget = targetLists.firstOrNull { it.firestoreId == selectedTargetListId }
    var targetMenuOpen by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Delete list?",
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = if (childCount > 0) {
            {
                Column {
                    Text(
                        "$listTitle has $childCount ${if (childCount == 1) "subitem" else "subitems"}.",
                        color = colors.onSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    if (targetLists.isNotEmpty()) {
                        DeleteChoiceRow(
                            selected = selectedAction == DeleteListAction.MoveItems,
                            title = "Move subitems",
                            subtitle = "Add them to another list before deleting this one.",
                            onClick = { onAction(DeleteListAction.MoveItems) },
                        )
                        if (selectedAction == DeleteListAction.MoveItems) {
                            Box(modifier = Modifier.padding(start = 42.dp, top = 8.dp, bottom = 4.dp)) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
                                        .clickable { targetMenuOpen = true }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        selectedTarget?.icon.orEmpty(),
                                        color = colors.onSurface,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                    )
                                    if (!selectedTarget?.icon.isNullOrBlank()) Spacer(Modifier.width(8.dp))
                                    Text(
                                        selectedTarget?.title?.ifBlank { "List" } ?: "Notes to self",
                                        color = colors.onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                DropdownMenu(
                                    expanded = targetMenuOpen,
                                    onDismissRequest = { targetMenuOpen = false },
                                ) {
                                    targetLists.forEach { list ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (list.icon.isNotBlank()) {
                                                        Text(list.icon, fontSize = 14.sp)
                                                        Spacer(Modifier.width(8.dp))
                                                    }
                                                    Text(list.title.ifBlank { "List" })
                                                }
                                            },
                                            onClick = {
                                                onTargetList(list.firestoreId)
                                                targetMenuOpen = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    DeleteChoiceRow(
                        selected = selectedAction == DeleteListAction.DeleteItems,
                        title = "Delete all subitems",
                        subtitle = "Remove every subitem in this list.",
                        onClick = { onAction(DeleteListAction.DeleteItems) },
                    )
                }
            }
        } else null,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete list", color = colors.error, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.onSurfaceVariant)
            }
        },
    )
}

@Composable
private fun DeleteChoiceRow(
    selected: Boolean,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f).padding(top = 2.dp)) {
            Text(
                title,
                color = colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    color = colors.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
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
    onOpenChange: (Boolean) -> Unit,
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
    val trimmedValue = value.trim()
    fun setOpen(next: Boolean) {
        open = next
        onOpenChange(next)
    }
    Box {
        if (trimmedValue.isNotBlank()) {
            Text(
                trimmedValue,
                color = colors.onSurface,
                fontSize = 18.sp,
                maxLines = 1,
                modifier = Modifier.clickable { setOpen(true) },
            )
        } else {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(8.dp))
                    .clickable { setOpen(true) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    trimmedValue,
                    color = colors.onSurface,
                    fontSize = 18.sp,
                    maxLines = 1,
                )
            }
        }
        if (open) {
            Dialog(onDismissRequest = { setOpen(false) }) {
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
                                        setOpen(false)
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
                                        setOpen(false)
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
    onToggle: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onEnter: (String) -> Unit,
    onOpen: () -> Unit,
) {
    val colors = IndexTheme.colors
    var draft by remember(child.firestoreId) { mutableStateOf(child.title) }
    var hadFocus by remember(child.firestoreId) { mutableStateOf(false) }
    var isFocused by remember(child.firestoreId) { mutableStateOf(false) }
    // Tracks whether this entry's text wraps to more than one line. Set
    // from the text field's layout result (see onTextLayout below).
    var isMultiLine by remember(child.firestoreId) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    // Per-item kind indicator: a checklist item shows the same tickable
    // circle the home Todos carousel uses; a plain note has no glyph
    // (keeps the column visually clean — note text starts at 22dp
    // exactly like the rest of the screen). Mixing both within a single
    // notes-domain list (e.g. Index features holding a checklist among
    // notes) now reads correctly at a glance.
    val isChecklist = child.kind == "checklist"

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
    // Refresh the draft when the title changes from outside (an edit made on
    // the item's detail screen, or a Firestore sync) while this row isn't
    // being actively edited. draft is keyed only on firestoreId, so without
    // this the row keeps rendering the title captured at first composition and
    // external edits wouldn't appear until the list is fully reopened. Mirrors
    // the list-header refresh above.
    LaunchedEffect(child.title) {
        if (!isFocused) draft = child.title
    }
    DisposableEffect(child.firestoreId) {
        onDispose { flush() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 22.dp,
                end = 14.dp,
                // Single-line notes stay tight (the compact, divider-less
                // look). A note that wraps to multiple lines gets extra
                // vertical air so it doesn't blur into its neighbours —
                // otherwise the gap between entries is smaller than the line
                // spacing within a wrapped entry and several notes read as
                // one block (MOB-8502).
                top = if (isMultiLine) 7.dp else 1.dp,
                bottom = if (isMultiLine) 7.dp else 1.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Reserve the same left "gutter" on every row regardless of kind
        // so all titles in a mixed list (notes and checklists side by
        // side) align at the same x-coordinate. Checklist rows fill that
        // gutter with the tickable circle; plain-note rows leave it
        // empty. Width = TodoCheckCircle.Small (19dp) + 12dp gap.
        if (isChecklist) {
            TodoCheckCircle(
                done = child.done,
                onToggle = onToggle,
            )
            Spacer(Modifier.width(12.dp))
        } else {
            Spacer(Modifier.width(31.dp))
        }
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
                textDecoration = if (child.done) TextDecoration.LineThrough else TextDecoration.None,
            ).indexTextEntryStyle(),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            onTextLayout = { isMultiLine = it.lineCount > 1 },
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
