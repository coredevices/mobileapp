package io.rebble.libpebblecommon.services.app

import TestPebbleProtocolHandler
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.packets.AppRunStateMessage
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class AppRunStateServiceTest {
    private val face = Uuid.parse("00000000-0000-0000-0000-00000000f001")
    private val app = Uuid.parse("00000000-0000-0000-0000-00000000a001")

    @Test
    fun thePreviousAppIsTheOneStartedBeforeTheCurrentOne() = runTest {
        val handler = TestPebbleProtocolHandler {}
        val service = AppRunStateService(handler, ConnectionCoroutineScope(backgroundScope.coroutineContext))
        service.init()
        runCurrent()

        handler.receivePacket(AppRunStateMessage.AppRunStateStart(face))
        runCurrent()
        assertNull(service.previousApp.value)

        handler.receivePacket(AppRunStateMessage.AppRunStateStop(face))
        handler.receivePacket(AppRunStateMessage.AppRunStateStart(app))
        runCurrent()
        assertEquals(face, service.previousApp.value)

        handler.receivePacket(AppRunStateMessage.AppRunStateStart(app))
        runCurrent()
        assertEquals(face, service.previousApp.value)

        handler.receivePacket(AppRunStateMessage.AppRunStateStart(face))
        runCurrent()
        assertEquals(app, service.previousApp.value)
    }
}
