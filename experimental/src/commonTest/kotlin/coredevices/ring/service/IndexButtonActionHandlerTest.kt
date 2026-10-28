package coredevices.ring.service

import com.russhwolf.settings.MapSettings
import coredevices.ring.database.PreferencesImpl
import coredevices.ring.service.button.GestureDestination
import coredevices.ring.service.button.GestureRoutingPreferences
import coredevices.ring.service.button.RingGesture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IndexButtonActionHandlerTest {

    private val routing = GestureRoutingPreferences(MapSettings(), PreferencesImpl(MapSettings()))

    private val click = listOf(ButtonPress.Short)
    private val doubleClick = listOf(ButtonPress.Short, ButtonPress.Short)

    @Test
    fun musicRoutesMapToMediaActions() {
        routing.setRoute(RingGesture.Click, GestureDestination.PlayPause)
        routing.setRoute(RingGesture.DoubleClick, GestureDestination.NextTrack)

        assertEquals(ButtonAction.PlayPause, routing.buttonActionFor(click))
        assertEquals(ButtonAction.NextTrack, routing.buttonActionFor(doubleClick))
    }

    @Test
    fun webhookRouteCarriesTheGesture() {
        routing.setRoute(RingGesture.DoubleClick, GestureDestination.Webhook)

        assertEquals(ButtonAction.Webhook(RingGesture.DoubleClick), routing.buttonActionFor(doubleClick))
    }

    @Test
    fun agentCommandRouteCarriesTheTrimmedCommandAndSequence() {
        routing.setRoute(RingGesture.DoubleClick, GestureDestination.AgentCommand)
        routing.setCommand(RingGesture.DoubleClick, "  Unlock front door ")

        assertEquals(
            ButtonAction.AgentCommand(command = "Unlock front door", buttonSequence = "short short"),
            routing.buttonActionFor(doubleClick),
        )
    }

    @Test
    fun agentCommandRouteWithoutACommandDoesNothing() {
        routing.setRoute(RingGesture.Click, GestureDestination.AgentCommand)
        routing.setCommand(RingGesture.Click, "   ")

        assertNull(routing.buttonActionFor(click))
    }

    @Test
    fun nothingAndUnknownSequencesDoNothing() {
        routing.setRoute(RingGesture.Click, GestureDestination.Nothing)

        assertNull(routing.buttonActionFor(click))
        assertNull(routing.buttonActionFor(listOf(ButtonPress.Long, ButtonPress.Long)))
    }

    @Test
    fun recordingGesturesAreNotButtonActions() {
        routing.setRoute(RingGesture.Hold, GestureDestination.IndexAgent)

        assertNull(routing.buttonActionFor(listOf(ButtonPress.Long)))
    }
}
