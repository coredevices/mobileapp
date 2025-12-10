import kotlinx.io.Source

data class DocumentAttachment(
    val fileName: String,
    val mimeType: String?,
    val source: Source,
    val size: Long,
)