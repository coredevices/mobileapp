import androidx.compose.runtime.Composable

@Composable
expect fun rememberOpenDocumentLauncher(onResult: (List<DocumentAttachment>?) -> Unit): (mimeTypeFilter: List<String>) -> Unit

@Composable
expect fun rememberOpenPhotoLauncher(onResult: (List<DocumentAttachment>?) -> Unit): () -> Unit
