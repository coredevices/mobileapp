import android.app.Activity
import android.content.Context

actual class PlatformContext(val context: Context)
actual class PlatformUiContext(val activity: Activity)