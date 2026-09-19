package io.rebble.libpebblecommon.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationSenderTest {
    @Test
    fun conversationTitleWinsRegardlessOfGroupFlag() {
        assertEquals(
            "PG | Elite",
            selectSender(
                conversationTitle = "PG | Elite",
                isGroupConversation = true,
                newestIncomingSender = "Aloha",
            ),
        )
        assertEquals(
            "PG | Elite",
            selectSender(
                conversationTitle = "PG | Elite",
                isGroupConversation = false,
                newestIncomingSender = "Aloha",
            ),
        )
    }

    @Test
    fun blankConversationTitleIsIgnored() {
        assertEquals(
            "Anna",
            selectSender(conversationTitle = " ", isGroupConversation = false, newestIncomingSender = "Anna"),
        )
    }

    @Test
    fun directConversationUsesNewestIncomingSender() {
        assertEquals(
            "Anna",
            selectSender(conversationTitle = null, isGroupConversation = false, newestIncomingSender = "Anna"),
        )
    }

    @Test
    fun groupConversationWithoutTitleIsNull() {
        assertNull(selectSender(conversationTitle = null, isGroupConversation = true, newestIncomingSender = "Aloha"))
    }

    @Test
    fun nothingKnownIsNull() {
        assertNull(selectSender(conversationTitle = null, isGroupConversation = false, newestIncomingSender = null))
    }
}
