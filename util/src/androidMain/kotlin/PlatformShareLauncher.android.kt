import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import kotlinx.io.files.Path
import java.io.File

actual class PlatformShareLauncher(private val context: Context) {
    actual fun share(text: String?, file: Path) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(file.toString()))
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            if (text != null) putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "audio/wav"
        }
        context.startActivity(
            Intent.createChooser(intent, null)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
    actual fun shareImage(image: ImageBitmap, filename: String) {
        val bitmap = image.asAndroidBitmap()
        val dir = context.cacheDir.resolve("screenshots")
        dir.mkdirs()
        val file = File(dir, filename)
        file.outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "image/png"
        }
        context.startActivity(
            Intent.createChooser(intent, null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }
}