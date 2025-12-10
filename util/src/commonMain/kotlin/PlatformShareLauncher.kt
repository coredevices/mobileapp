import kotlinx.io.files.Path

expect class PlatformShareLauncher {
    fun share(text: String?, file: Path)
}