import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.io.files.Path

expect class PlatformShareLauncher {
    fun share(text: String?, file: Path)
    fun shareImage(image: ImageBitmap, filename: String)
}