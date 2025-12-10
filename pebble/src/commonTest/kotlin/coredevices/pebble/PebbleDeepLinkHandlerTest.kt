package coredevices.pebble

import com.eygraber.uri.Uri
import coredevices.pebble.PebbleDeepLinkHandler.Companion.parseTokenFrom
import kotlin.test.Test
import kotlin.test.assertEquals

class PebbleDeepLinkHandlerTest {
    @Test fun handlesIosBootConfig() {
        val uri = Uri.parse("pebble://custom-boot-config-url/https%3A%2F%2Fboot.rebble.io%2Fapi%2Fstage2%2Fios%3Faccess_token%3DabcdefGHiJKLM01234567890OpQrST%26t%3D1744915159")
        val token = parseTokenFrom(uri.path)
        assertEquals("abcdefGHiJKLM01234567890OpQrST", token)
    }

    @Test fun handlesAndroidBootConfig() {
        val uri = Uri.parse("pebble://custom-boot-config-url/https%3A%2F%2Fboot.rebble.io%2Fapi%2Fstage2%2F%3Faccess_token%3DabcdefGHiJKLM01234567890OpQrST%26t%3D1742938502")
        val token = parseTokenFrom(uri.path)
        assertEquals("abcdefGHiJKLM01234567890OpQrST", token)
    }
}