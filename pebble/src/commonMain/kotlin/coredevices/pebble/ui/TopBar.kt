package coredevices.pebble.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopSearchBar
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.sp
import coreapp.pebble.generated.resources.Res
import coreapp.pebble.generated.resources.watch_home_clear_search
import coreapp.util.generated.resources.back
import coreapp.util.generated.resources.search
import org.jetbrains.compose.resources.stringResource
import coreapp.util.generated.resources.Res as UtilRes

/** What a nav bar screen's [TopBarParams] set; drawn by [ScreenTopBar]. */
@Stable
internal class ScreenTopBarState {
    var title by mutableStateOf("")
    var actions by mutableStateOf<@Composable RowScope.() -> Unit>({})
    var searchState by mutableStateOf<SearchState?>(null)
}

@Composable
internal fun ScreenTopBar(state: ScreenTopBarState, canGoBack: Boolean, onBack: () -> Unit) {
    val searchState = state.searchState
    Crossfade(
        modifier = Modifier.animateContentSize(),
        targetState = searchState?.show == true,
        label = "Search"
    ) { showSearch ->
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        val onSearchDone = {
            searchState?.typing = false
            keyboardController?.hide()
            focusManager.clearFocus()
        }
        if (showSearch) {
            LaunchedEffect(focusRequester) {
                // Only grab focus for a freshly-opened search bar; returning here with
                // a live query must not re-open the keyboard.
                if (searchState?.query.isNullOrEmpty()) {
                    focusRequester.requestFocus()
                }
            }
            TopSearchBar(
                state = rememberSearchBarState(),
                // The NavHost already pads the sides.
                windowInsets = SearchBarDefaults.windowInsets.only(WindowInsetsSides.Top),
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchState?.query ?: "",
                        onQueryChange = {
                            searchState?.query = it
                            searchState?.typing = true
                        },
                        onSearch = {
                            onSearchDone()
                        },
                        expanded = false,
                        onExpandedChange = { },
                        placeholder = { Text(stringResource(UtilRes.string.search)) },
                        modifier = Modifier.focusRequester(focusRequester),
                        trailingIcon = {
                            IconButton(onClick = {
                                searchState?.show = false
                                searchState?.query = ""
                            }) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stringResource(Res.string.watch_home_clear_search)
                                )
                            }
                        },
                        leadingIcon = {
                            IconButton(onClick = onSearchDone) {
                                Icon(
                                    Icons.Outlined.Search,
                                    contentDescription = stringResource(UtilRes.string.search)
                                )
                            }
                        },
                    )
                },
            )
        } else {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = stringResource(UtilRes.string.back)
                            )
                        }
                    }
                },
                title = {
                    Text(
                        text = state.title,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 28.sp,
                        maxLines = 1,
                    )
                },
                actions = {
                    state.actions(this)
                    if (searchState != null) {
                        TopBarIconButtonWithToolTip(
                            onClick = { searchState.show = true },
                            icon = Icons.Filled.Search,
                            description = stringResource(UtilRes.string.search),
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun TopBarIconButtonWithToolTip(
    onClick: () -> Unit,
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    badge: @Composable BoxScope.() -> Unit = {},
) {
    val tooltipState = remember { TooltipState(isPersistent = false) }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(description)
            }
        },
        state = tooltipState
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            BadgedBox(badge = badge) {
                Icon(
                    imageVector = icon,
                    contentDescription = description
                )
            }
        }
    }
}
