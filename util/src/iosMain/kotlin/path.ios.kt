import kotlinx.io.files.Path
import platform.Foundation.NSURL

fun Path.toNSURL(): NSURL {
    return NSURL.fileURLWithPath(this.toString())
}