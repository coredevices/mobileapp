package io.rebble.libpebblecommon.connection.bt.ble.ppog

import io.ktor.utils.io.availableForRead
import io.ktor.utils.io.readByteArray
import io.rebble.libpebblecommon.BleConfig
import io.rebble.libpebblecommon.BleConfigFlow
import io.rebble.libpebblecommon.LibPebbleConfig
import io.rebble.libpebblecommon.asFlow
import io.rebble.libpebblecommon.connection.ConnectionException
import io.rebble.libpebblecommon.connection.PebbleProtocolStreams
import io.rebble.libpebblecommon.connection.bt.ble.BlePlatformConfig
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class PPoGTest {
    private val ppStreams = PebbleProtocolStreams()
    private val ppogStreams = PPoGStream()
    private val outboundPPoGPackets = Channel<ByteArray>(capacity = 100)
    private val sender = object : PPoGPacketSender {
        override suspend fun sendPacket(packet: ByteArray): Boolean {
            outboundPPoGPackets.send(packet)
            return true
        }

        override fun wasRestoredWithSubscribedCentral(): Boolean {
            return false
        }
    }
    val bleConfig = BleConfig(
        reversedPPoG = false,
    )
    val blePlatformConfig = BlePlatformConfig(
        initialMtu = 23,
        desiredTxWindow = 20,
        desiredRxWindow = 19,
    )
    val bleConfigFlow = bleConfig.asFlow()
    private lateinit var ppog: PPoG
    private var mtu = blePlatformConfig.initialMtu
    private var ppogVersion = PPoGVersion.ONE

    private fun ppogDataPacket(sequence: Int): PPoGPacket.Data {
        val bytes = randomBytes()
        return PPoGPacket.Data(sequence = sequence, data = bytes)
    }

    private fun setMtu(mtu: Int) {
        this.mtu = mtu
        ppog.updateMtu(mtu)
    }

    private fun randomBytes(size: Int = mtu - 4) = Random.nextBytes(size)

    private suspend fun receivePacket(packet: PPoGPacket) {
        ppogStreams.inboundPPoGBytesChannel.send(packet.serialize(ppogVersion))
    }

    private suspend fun init(
        sendResetComplete: Boolean = true,
        version: PPoGVersion = PPoGVersion.ONE,
        rxWindow: Int = 19,
    ) {
        receivePacket(PPoGPacket.ResetRequest(sequence = 0, ppogVersion = version))
        assertOutboundPPoGPacket(
            PPoGPacket.ResetComplete(
                sequence = 0,
                rxWindow = 19,
                txWindow = 20
            )
        )
        if (sendResetComplete) {
            receivePacket(
                PPoGPacket.ResetComplete(sequence = 0, rxWindow = rxWindow, txWindow = 20)
            )
        }
    }

    @Test
    fun initNormal() = runTest {
        val scope = ConnectionCoroutineScope(backgroundScope.coroutineContext)
        ppog = PPoG(ppStreams, ppogStreams, sender, bleConfigFlow, blePlatformConfig, scope)
        ppog.run(false)
        init()
        testScheduler.advanceTimeBy(30.seconds)
    }

    @Test
    fun initTimeout() {
        assertThrows(ConnectionException::class.java) {
            runTest {
                val scope = ConnectionCoroutineScope(backgroundScope.coroutineContext)
                ppog = PPoG(ppStreams, ppogStreams, sender, bleConfigFlow, blePlatformConfig, scope)
                ppog.run(false)
                init(sendResetComplete = false)
                testScheduler.advanceTimeBy(30.seconds)
            }
        }
    }

    @Test
    fun runNormalV0() = runTest {
        val scope = ConnectionCoroutineScope(backgroundScope.coroutineContext)
        ppog = PPoG(ppStreams, ppogStreams, sender, bleConfigFlow, blePlatformConfig, scope)
        ppog.run(false)
        init(version = PPoGVersion.ZERO)
        setMtu(50)

        // Packets from watch
        val inbound0 = ppogDataPacket(0)
        receivePacket(inbound0)
        val inbound1 = ppogDataPacket(1)
        receivePacket(inbound1)
        assertOutboundPPoGPacket(PPoGPacket.Ack(sequence = 0))
        assertOutboundPPoGPacket(PPoGPacket.Ack(sequence = 1))
        assertInboundPPBytes(inbound0.data)
        assertInboundPPBytes(inbound1.data)

        // Packet to watch
        val outboundBytes0 = randomBytes(75)
        val outboundBytes2 = randomBytes()
        ppStreams.outboundPPBytes.send(outboundBytes0)
        ppStreams.outboundPPBytes.send(outboundBytes2)
        assertOutboundPPoGData(sequence = 0, data = outboundBytes0)
        assertOutboundPPoGData(sequence = 2, data = outboundBytes2)
    }

//    @Test
//    fun runNormalV1_coalescedAcks() = runTest {
//        val scope = ConnectionCoroutineScope(backgroundScope.coroutineContext)
//        ppog = PPoG(ppStreams, ppogStreams, sender, bleConfig, scope)
//        ppog.run()
//        init(version = PPoGVersion.ONE)
//        setMtu(50)
//
//        // Packets from watch
//        val inbound0 = ppogDataPacket(0)
//        receivePacket(inbound0)
//        val inbound1 = ppogDataPacket(1)
//        receivePacket(inbound1)
//        assertInboundPPBytes(inbound0.data)
//        assertInboundPPBytes(inbound1.data)
//        // coalesced ack
//        assertTrue(outboundPPoGPackets.isEmpty)
////        testScheduler.advanceTimeBy(30.seconds)
//        assertOutboundPPoGPacket(PPoGPacket.Ack(sequence = 1))
//
//        // Packet to watch
//        val outboundBytes0 = randomBytes(75)
//        val outboundBytes2 = randomBytes()
//        ppStreams.outboundPPBytes.send(outboundBytes0)
//        ppStreams.outboundPPBytes.send(outboundBytes2)
//        assertOutboundPPoGData(sequence = 0, data = outboundBytes0)
//        assertOutboundPPoGData(sequence = 2, data = outboundBytes2)
//    }

    @Test
    fun inboundOutOfSequenceResendAck() = runTest {
        val scope = ConnectionCoroutineScope(backgroundScope.coroutineContext)
        ppog = PPoG(ppStreams, ppogStreams, sender, bleConfigFlow, blePlatformConfig, scope)
        ppog.run(false)
        init()
        val inbound0 = ppogDataPacket(0)
        receivePacket(inbound0)
        val inbound2 = ppogDataPacket(2)
        receivePacket(inbound2)
        assertOutboundPPoGPacket(PPoGPacket.Ack(sequence = 0))
        assertInboundPPBytes(inbound0.data)
        // out of sequence data packet; resend previous ack
        assertOutboundPPoGPacket(PPoGPacket.Ack(sequence = 0))
        // no data written to PP
        assertEquals(0, ppStreams.inboundPPBytes.availableForRead)
    }

    @Test
    fun windowSize() = runTest {
        val scope = ConnectionCoroutineScope(backgroundScope.coroutineContext)
        ppog = PPoG(ppStreams, ppogStreams, sender, bleConfigFlow, blePlatformConfig, scope)
        ppog.run(false)
        init(rxWindow = 2)
        val outboundBytes0 = randomBytes()
        val outboundBytes1 = randomBytes()
        val outboundBytes2 = randomBytes()
        ppStreams.outboundPPBytes.send(outboundBytes0)
        ppStreams.outboundPPBytes.send(outboundBytes1)
        ppStreams.outboundPPBytes.send(outboundBytes2)
        assertOutboundPPoGData(sequence = 0, data = outboundBytes0)
        assertOutboundPPoGData(sequence = 1, data = outboundBytes1)
        assertTrue(outboundPPoGPackets.isEmpty)
        // Only sends the 3rd packet after the first was ACKd
        receivePacket(PPoGPacket.Ack(sequence = 0))
        assertOutboundPPoGData(sequence = 2, data = outboundBytes2)
    }

    @Test
    fun retrySendOnTimeout() = runTest {
        val crashed = MutableStateFlow(false)
        val exceptionHandler = CoroutineExceptionHandler { _, t ->
            crashed.value = true
        }
        val scope =
            ConnectionCoroutineScope(backgroundScope.coroutineContext + SupervisorJob() + exceptionHandler)
        ppog = PPoG(ppStreams, ppogStreams, sender, bleConfigFlow, blePlatformConfig, scope)
        ppog.run(false)
        init()

        val outboundBytes0 = randomBytes()
        val outboundBytes1 = randomBytes()
        val outboundBytes2 = randomBytes()
        ppStreams.outboundPPBytes.send(outboundBytes0)
        ppStreams.outboundPPBytes.send(outboundBytes1)
        ppStreams.outboundPPBytes.send(outboundBytes2)
        assertOutboundPPoGData(sequence = 0, data = outboundBytes0)
        assertOutboundPPoGData(sequence = 1, data = outboundBytes1)
        assertOutboundPPoGData(sequence = 2, data = outboundBytes2)
        receivePacket(PPoGPacket.Ack(sequence = 0))
        // retries
        assertOutboundPPoGData(sequence = 1, data = outboundBytes1)
        assertOutboundPPoGData(sequence = 2, data = outboundBytes2)
        assertOutboundPPoGData(sequence = 1, data = outboundBytes1)
        assertOutboundPPoGData(sequence = 2, data = outboundBytes2)
        testScheduler.advanceTimeBy(30.seconds)
        crashed.first { it }
    }

    @Test
    fun retrySendOnDuplicateAck() = runTest {
        val scope = ConnectionCoroutineScope(backgroundScope.coroutineContext)
        ppog = PPoG(ppStreams, ppogStreams, sender, bleConfigFlow, blePlatformConfig, scope)
        ppog.run(false)
        init()

        val outboundBytes0 = randomBytes()
        val outboundBytes1 = randomBytes()
        val outboundBytes2 = randomBytes()
        ppStreams.outboundPPBytes.send(outboundBytes0)
        ppStreams.outboundPPBytes.send(outboundBytes1)
        ppStreams.outboundPPBytes.send(outboundBytes2)
        assertOutboundPPoGData(sequence = 0, data = outboundBytes0)
        assertOutboundPPoGData(sequence = 1, data = outboundBytes1)
        assertOutboundPPoGData(sequence = 2, data = outboundBytes2)
        receivePacket(PPoGPacket.Ack(sequence = 0))
        // Dup ack received (other side missed a data packet)
        receivePacket(PPoGPacket.Ack(sequence = 0))
        // Retries
        assertOutboundPPoGData(sequence = 1, data = outboundBytes1)
        assertOutboundPPoGData(sequence = 2, data = outboundBytes2)
        receivePacket(PPoGPacket.Ack(sequence = 2))
        testScheduler.advanceTimeBy(30.seconds)
    }

    private suspend fun assertOutboundPPoGData(sequence: Int, data: ByteArray) {
        val chunks = data.asList().chunked(mtu - 4)
        var seq = sequence
        chunks.forEach {
            assertOutboundPPoGPacket(PPoGPacket.Data(sequence = seq, data = it.toByteArray()))
            seq++
        }
    }

    private suspend fun assertOutboundPPoGPacket(ppogPacket: PPoGPacket) {
        val sentPacket = outboundPPoGPackets.receive().asPPoGPacket()
        when {
            ppogPacket is PPoGPacket.Data && sentPacket is PPoGPacket.Data -> {
                assertEquals(ppogPacket.sequence, sentPacket.sequence)
                assertTrue(ppogPacket.data.contentEquals(sentPacket.data))
            }

            else -> assertEquals(ppogPacket, sentPacket)
        }
    }

    private suspend fun assertInboundPPBytes(data: ByteArray) {
        assertTrue(ppStreams.inboundPPBytes.readByteArray(data.size).contentEquals(data))
    }
}