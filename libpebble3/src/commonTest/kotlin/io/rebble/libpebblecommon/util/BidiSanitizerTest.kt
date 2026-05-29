package io.rebble.libpebblecommon.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BidiSanitizerTest {
    @Test
    fun stripBidiIsolates_nullInput() {
        assertNull(stripBidiIsolates(null))
    }

    @Test
    fun stripBidiIsolates_removesIsolateMarkers() {
        val input = "⁨Юлия⁩ and ⁦abc⁩ and ⁧xyz⁩"
        val expected = "Юлия and abc and xyz"
        assertEquals(expected, stripBidiIsolates(input))
    }

    @Test
    fun stripBidiIsolates_noopWhenNonePresent() {
        val input = "Sender Name"
        assertEquals(input, stripBidiIsolates(input))
    }

    @Test
    fun stripBidiIsolates_removesInvisibleAndZeroWidthChars() {
        // Soft hyphen, CGJ, ZWNJ, ZWJ, ZWSP, hair space interleaved with letters.
        val input = "A­B͏C‌D‍E​F G"
        assertEquals("ABCDEFG", stripBidiIsolates(input))
    }

    @Test
    fun stripBidiIsolates_handlesOutlookPreheaderTail() {
        // Padding seen in Temu forwards via Outlook (MOB-6722):
        // visible emoji + space-separated CGJ / ZWNJ / SHY runs.
        val input = "Shop now. 🛒 ͏ ‌ ­͏ ‌ ­͏"
        assertEquals("Shop now. 🛒     ", stripBidiIsolates(input))
    }

    @Test
    fun stripBidiIsolates_removesLegacyBidiAndBomAndWordJoiner() {
        // LRM, RLM, LRE, RLE, PDF, LRO, RLO, word joiner, BOM.
        val input = "X\u200EY\u200FZ\u202A\u202B\u202C\u202D\u202EW\u2060V\uFEFFU"
        assertEquals("XYZWVU", stripBidiIsolates(input))
    }

    @Test
    fun stripBidiIsolates_replacesNarrowNoBreakSpaceWithRegularSpace() {
        // Android's ICU/CLDR time formatter emits U+202F before AM/PM in en_US (MOB-7052).
        // The firmware font has no glyph for it; we must replace (not strip) to keep the space.
        val input = "9:30 \u2013 10:30\u202FPM"
        assertEquals("9:30 \u2013 10:30 PM", stripBidiIsolates(input))
    }

    @Test
    fun stripBidiIsolates_preservesNarrowNoBreakSpaceAlongsideStrippedBidiMarks() {
        // Real payload: bidi isolates wrap a time with U+202F inside.
        val input = "\u200E\u202A9:30 \u2013 10:30\u202FPM\u202C\u200E"
        assertEquals("9:30 \u2013 10:30 PM", stripBidiIsolates(input))
    }
}
