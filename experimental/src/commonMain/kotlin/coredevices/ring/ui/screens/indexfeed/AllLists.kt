@file:OptIn(ExperimentalTime::class)

package coredevices.ring.ui.screens.indexfeed

import CoreNav
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.SEED_TODOS
import coredevices.ring.ui.screens.home.NoteListCard
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.ring.ui.theme.IndexTheme
import coredevices.ring.ui.theme.IndexThemeHost
import coredevices.ring.ui.theme.indexTextEntryStyle
import coredevices.ring.ui.viewmodel.AllListsViewModel
import kotlin.time.ExperimentalTime
import org.koin.compose.viewmodel.koinViewModel

/**
 * Full-screen 2-col grid of every list except the system Todos list.
 * Matches the prototype's `AllListsScreen`.
 */
@Composable
fun AllLists(coreNav: CoreNav) {
    val vm = koinViewModel<AllListsViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsState()
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(searching) { if (!searching) vm.setQuery("") }

    IndexThemeHost {
        val colors = IndexTheme.colors
        val statusBarPad = WindowInsets.statusBars.asPaddingValues()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surface)
                .padding(top = statusBarPad.calculateTopPadding()),
        ) {
            if (searching) {
                ListsSearchTopBar(
                    value = query,
                    onChange = vm::setQuery,
                    onCancel = { searching = false; vm.setQuery("") },
                )
            } else {
                TopBar(
                    title = "All notes",
                    coreNav = coreNav,
                    right = {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Default.Search, "Search", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                        // "+" new-list — creates an empty list then jumps
                        // straight into rename mode on its detail page.
                        Text(
                            "+",
                            color = colors.primary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable {
                                    vm.newList { newId ->
                                        coreNav.navigateTo(RingRoutes.ObjectDetails(newId, startEditing = true))
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    },
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            ) {
                if (state.lists.isEmpty()) {
                    item("empty") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (query.isNotBlank()) "No matching lists." else "No lists yet.",
                                color = colors.onSurfaceVariant,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
                val rows = state.lists.chunked(2)
                items(items = rows, key = { it.first().list.firestoreId }) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { entry ->
                            Box(modifier = Modifier.weight(1f)) {
                                NoteListCard(
                                    list = entry.list,
                                    items = entry.items,
                                    onClick = { coreNav.navigateTo(RingRoutes.ObjectDetails(entry.list.firestoreId)) },
                                )
                            }
                        }
                        if (row.size == 1) Box(modifier = Modifier.weight(1f))
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun TopBar(title: String, coreNav: CoreNav, right: @Composable () -> Unit) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = coreNav::goBack) {
            Icon(Icons.AutoMirrored.Default.ArrowBack, "Back", tint = colors.onSurface)
        }
        Text(
            title,
            color = colors.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        right()
    }
}

@Composable
private fun ListsSearchTopBar(value: String, onChange: (String) -> Unit, onCancel: () -> Unit) {
    val colors = IndexTheme.colors
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
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
                    if (value.isEmpty()) Text("Search…", color = colors.onSurfaceVariant, fontSize = 15.sp)
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
