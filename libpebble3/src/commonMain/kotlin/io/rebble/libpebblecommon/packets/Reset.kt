package io.rebble.libpebblecommon.packets

import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import io.rebble.libpebblecommon.structmapper.SUByte

internal sealed class ResetMessage(message: ResetType) : PebblePacket(endpoint) {
    enum class ResetType(val value: UByte) {
        Reset(0x00u),
        CoreDump(0x01u),
        FactoryReset(0xfeu),
        ResetIntoPrf(0xffu),
    }

    val command: SUByte = SUByte(m, message.value)

    data object Reset : ResetMessage(ResetType.Reset)
    data object CoreDump : ResetMessage(ResetType.CoreDump)
    data object FactoryReset : ResetMessage(ResetType.FactoryReset)
    data object ResetIntoPrf : ResetMessage(ResetType.ResetIntoPrf)

    companion object {
        val endpoint: ProtocolEndpoint = ProtocolEndpoint.RESET
    }
}