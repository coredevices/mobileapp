package io.rebble.libpebblecommon.packets

import io.rebble.libpebblecommon.protocolhelpers.PacketRegistry
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import io.rebble.libpebblecommon.structmapper.*


internal sealed class PhoneControl(message: Message, cookie: UInt) : PebblePacket(endpoint) {
    enum class Message(val value: UByte) {
        Unknown(0u),
        Answer(0x01u),
        Hangup(0x02u),
        GetState(0x03u),
        IncomingCall(0x04u),
        OutgoingCall(0x05u),
        MissedCall(0x06u),
        Ring(0x07u),
        Start(0x08u),
        End(0x09u)
    }
    companion object {
        val endpoint: ProtocolEndpoint = ProtocolEndpoint.PHONE_CONTROL
    }

    val command: SUByte = SUByte(m, message.value)
    val cookie: SUInt = SUInt(m, cookie)

    class IncomingCall(cookie: UInt = 0u, callerNumber: String = "", callerName: String = "") : PhoneControl(Message.IncomingCall, cookie) {
        val callerNumber: SString = SString(m, callerNumber)
        val callerName: SString = SString(m, callerName)
    }
    class MissedCall(cookie: UInt = 0u, callerNumber: String = "", callerName: String = "") : PhoneControl(Message.MissedCall, cookie) {
        val callerNumber: SString = SString(m, callerNumber)
        val callerName: SString = SString(m, callerName)
    }
    class Ring(cookie: UInt = 0u) : PhoneControl(Message.Ring, cookie)
    class Start(cookie: UInt = 0u) : PhoneControl(Message.Start, cookie)
    class End(cookie: UInt = 0u) : PhoneControl(Message.End, cookie)

    class Answer(cookie: UInt = 0u) : PhoneControl(Message.Answer, cookie)
    class Hangup(cookie: UInt = 0u) : PhoneControl(Message.Hangup, cookie)
}

internal fun phoneControlPacketsRegister() {
    PacketRegistry.register(
        PhoneControl.endpoint,
        PhoneControl.Message.Answer.value,
    ) { PhoneControl.Answer() }
    PacketRegistry.register(
        PhoneControl.endpoint,
        PhoneControl.Message.Hangup.value,
    ) { PhoneControl.Hangup() }
}