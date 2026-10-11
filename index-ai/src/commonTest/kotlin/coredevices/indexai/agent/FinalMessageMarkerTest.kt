package coredevices.indexai.agent

import kotlin.test.Test
import kotlin.test.assertEquals

class FinalMessageMarkerTest {

    @Test
    fun queryAnswerIsStrippedAndFlagged() {
        val parsed = FinalMessageMarker.parse("<query_answer> It is 3pm. ")
        assertEquals(FinalMessageMarker.Parsed(isQueryAnswer = true, text = "It is 3pm."), parsed)
    }

    @Test
    fun actionCompleteIsStrippedOnly() {
        val parsed = FinalMessageMarker.parse("\n<action_complete>Done")
        assertEquals(FinalMessageMarker.Parsed(isQueryAnswer = false, text = "Done"), parsed)
    }

    @Test
    fun closingTagIsDropped() {
        val parsed = FinalMessageMarker.parse("<query_answer>Sunny</query_answer>")
        assertEquals(FinalMessageMarker.Parsed(isQueryAnswer = true, text = "Sunny"), parsed)
    }

    @Test
    fun unmarkedContentIsUntouched() {
        assertEquals(FinalMessageMarker.Parsed(false, " plain "), FinalMessageMarker.parse(" plain "))
        assertEquals(FinalMessageMarker.Parsed(false, null), FinalMessageMarker.parse(null))
    }

    @Test
    fun emptyAnswerBecomesNull() {
        assertEquals(FinalMessageMarker.Parsed(isQueryAnswer = true, text = null), FinalMessageMarker.parse("<query_answer>"))
    }

    @Test
    fun markerMidMessageIsIgnored() {
        val content = "Sure <action_complete>"
        assertEquals(FinalMessageMarker.Parsed(false, content), FinalMessageMarker.parse(content))
    }
}
