package coredevices.pebble.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.Flow

fun interface SnackbarDisplay {
    fun showSnackbar(message: String)
}

@Stable
data class TopBarParams(
    val searchAvailable: (SearchState?) -> Unit,
    val actions: (@Composable RowScope.() -> Unit) -> Unit,
    val title: (String) -> Unit,
    val overrideGoBack: Flow<Unit>,
    private val showSnackbar: (String) -> Unit,
    val scrollToTop: Flow<Unit>,
) : SnackbarDisplay {
    override fun showSnackbar(message: String) = showSnackbar.invoke(message)
}

@Composable
fun rememberSearchState() = remember { SearchState() }

class SearchState {
    var query by mutableStateOf("")
    var typing by mutableStateOf(false)
    var show by mutableStateOf(false)
}
