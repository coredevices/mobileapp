package io.rebble.libpebblecommon.notification

import androidx.core.app.NotificationCompat.MessagingStyle.Message
import androidx.core.app.Person
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IncomingBatchTest {

    private val user = person("You")

    private fun person(name: String) = Person.Builder().setName(name).build()

    private fun message(text: String, timestamp: Long, from: Person?) =
        Message(text, timestamp, from)

    private fun texts(batch: List<Message>) = batch.map { it.text.toString() }

    @Test
    fun `newest message is unchanged after we reply`() {
        val sender = person("Steve Penna")
        val incoming = listOf(message("Hiiiiii", 1_000, sender))
        val afterReply = incoming + message("hello back", 11_000, null)

        assertEquals(
            texts(incomingBatch(incoming, user)),
            texts(incomingBatch(afterReply, user)),
        )
    }

    @Test
    fun `reply attributed to the user by name is excluded`() {
        val sender = person("Steve Penna")
        val batch = incomingBatch(
            listOf(
                message("Hiiiiii", 1_000, sender),
                message("hello back", 11_000, person("You")),
            ),
            user,
        )
        assertEquals(listOf("Hiiiiii"), texts(batch))
    }

    @Test
    fun `messages sent together stay in the batch`() {
        val sender = person("Steve Penna")
        val batch = incomingBatch(
            listOf(
                message("first", 1_000, sender),
                message("second", 1_500, sender),
            ),
            user,
        )
        assertEquals(listOf("second", "first"), texts(batch))
    }

    @Test
    fun `earlier message outside the window is not in the batch`() {
        val sender = person("Steve Penna")
        val batch = incomingBatch(
            listOf(
                message("old", 1_000, sender),
                message("new", 9_000, sender),
            ),
            user,
        )
        assertEquals(listOf("new"), texts(batch))
    }

    @Test
    fun `conversation with only our own messages has no batch`() {
        val batch = incomingBatch(listOf(message("hello back", 11_000, null)), user)
        assertTrue(batch.isEmpty())
    }
}
