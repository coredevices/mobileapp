import android.content.Context
import android.content.Intent
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
}