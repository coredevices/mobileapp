package coredevices.pebble.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.Flow

@Stable
data class TopBarParams(
    val searchAvailable: (SearchState?) -> Unit,
    val actions: (@Composable RowScope.() -> Unit) -> Unit,
    val title: (String) -> Unit,
    val overrideGoBack: Flow<Unit>,
    val showSnackbar: (String) -> Unit,
    val scrollToTop: Flow<Unit>,
)

@Composable
fun rememberSearchState() = remember { SearchState() }

class SearchState {
    var query by mutableStateOf("")
    var typing by mutableStateOf(false)
    var show by mutableStateOf(false)
}
