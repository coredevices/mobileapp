import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.vectorResource
import coreapp.util.generated.resources.Res
import coreapp.util.generated.resources.assistant
import coreapp.util.generated.resources.core
import coreapp.util.generated.resources.notes
import coreapp.util.generated.resources.waveform

object CoreIcons {
    val Core @Composable get() = vectorResource(Res.drawable.core)
    val Notes @Composable get() = vectorResource(Res.drawable.notes)
    val Assistant @Composable get() = vectorResource(Res.drawable.assistant)
    val Waveform @Composable get() = vectorResource(Res.drawable.waveform)
}