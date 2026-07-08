@file:OptIn(ExperimentalTime::class)

package coredevices.ring.ui.screens.indexfeed

import coredevices.ring.ui.relativeTime
import CoreNav
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.ui.navigation.RingRoutes
import coredevices.ring.ui.theme.IndexTheme
import coredevices.ring.ui.theme.IndexThemeHost
import coredevices.ring.ui.theme.indexTextEntryStyle
import coredevices.ring.ui.viewmodel.AllAnswersViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import org.koin.compose.viewmodel.koinViewModel

/**
 * Full-screen list of every Q&A capture, newest first. Mirrors the
 * prototype's `AllAnswersScreen`.
 */
@Composable
fun AllAnswers(coreNav: CoreNav) {
    val vm = koinViewModel<AllAnswersViewModel>()
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
                AnswersSearchTopBar(
                    value = query,
                    onChange = vm::setQuery,
                    onCancel = { searching = false; vm.setQuery("") },
                )
            } else {
                TopBar(
                    title = "You asked",
                    coreNav = coreNav,
                    right = {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Default.Search, "Search", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    },
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            ) {
                if (state.answers.isEmpty()) {
                    item("empty") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (query.isNotBlank()) "No matching questions." else "No questions yet. Ask your Index.",
                                color = colors.onSurfaceVariant,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
                items(items = state.answers, key = { it.firestoreId }) { ans ->
                    AnswerRow(
                        answer = ans,
                        onClick = { coreNav.navigateTo(RingRoutes.ObjectDetails(ans.firestoreId)) },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun AnswerRow(answer: CachedItem, onClick: () -> Unit) {
    val colors = IndexTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainerLow)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            relativeTime(answer.createdAt).uppercase(),
            color = colors.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            answer.title,
            color = colors.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.1).sp,
            lineHeight = 19.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (answer.body.isNotBlank()) {
            Text(
                answer.body,
                color = colors.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp),
            )
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
private fun AnswersSearchTopBar(value: String, onChange: (String) -> Unit, onCancel: () -> Unit) {
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

// `relativeTime` is shared with ObjectDetail (internal in this package)
// — the duplicate that used to live here was deleted to dodge an
// overload-resolution ambiguity after the ObjectDetail split.
