package coredevices.ring.agent.integrations

import PlatformUiContext
import com.eygraber.uri.Uri
import coredevices.util.Platform
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class UIEmailIntegration: NoteIntegration, KoinComponent {
    private val platform: Platform by inject()
    override suspend fun createNote(content: String): String {
        val userEmail = Firebase.auth.currentUser?.email
        val uri = Uri.Builder()
            .scheme("mailto")
            .appendQueryParameter("body", content)
        if (userEmail != null) {
            uri.appendQueryParameter("to", userEmail)
        }
        platform.openUrl(uri.build().toString())
        return "email"
    }

    override suspend fun signIn(uiContext: PlatformUiContext): Boolean {
        return true
    }

    override suspend fun unlink() {

    }

}