import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import coredevices.util.CommonBuildKonfig
import kotlinx.serialization.json.Json
import org.koin.compose.koinInject

private val jsonPretty = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

@Composable
fun BugReportButton(
    coreNav: CoreNav,
    pebble: Boolean,
    screenContext: Map<String, String> = emptyMap(),
    recordingPath: String? = null,
) {
    if (!CommonBuildKonfig.QA) return
    val nextBugReportContext: NextBugReportContext = koinInject()
    IconButton(
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
        onClick = {
            val screenContextJson = screenContext.let { jsonPretty.encodeToString(screenContext) }
            nextBugReportContext.nextContext = screenContextJson
            coreNav.navigateTo(CommonRoutes.BugReport(
                pebble = pebble,
                recordingPath = recordingPath,
            ))
        }
    ) {
        Icon(Icons.Default.BugReport, contentDescription = "Report a bug")
    }
}

class NextBugReportContext {
    var nextContext: String? = null
}