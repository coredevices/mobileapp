package coredevices.ring.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Where the notification shortcut sends a recording. Provider ids resolve through
 *  [coredevices.ring.agent.integrations.providers.IntegrationProviderRepository]. */
@Serializable
sealed interface NoteShortcutType {
    @Serializable
    data object SendToMe: NoteShortcutType
    @Serializable
    data class SendToNoteProvider(@SerialName("provider") val providerId: String): NoteShortcutType
    @Serializable
    data class SendToReminderProvider(@SerialName("provider") val providerId: String): NoteShortcutType
}
