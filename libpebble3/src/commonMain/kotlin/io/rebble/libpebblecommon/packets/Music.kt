package io.rebble.libpebblecommon.packets

import io.rebble.libpebblecommon.protocolhelpers.PacketRegistry
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import io.rebble.libpebblecommon.structmapper.*
import io.rebble.libpebblecommon.util.Endian

public open class MusicControl internal constructor(public val message: Message) : PebblePacket(ProtocolEndpoint.MUSIC_CONTROL) {
    internal val command: SUByte = SUByte(m, message.value)

    init {
        type = command.get()
    }

    public enum class Message(public val value: UByte) {
        PlayPause(0x01u),
        Pause(0x02u),
        Play(0x03u),
        NextTrack(0x04u),
        PreviousTrack(0x05u),
        VolumeUp(0x06u),
        VolumeDown(0x07u),
        GetCurrentTrack(0x08u),
        UpdateCurrentTrack(0x10u),
        UpdatePlayStateInfo(0x11u),
        UpdateVolumeInfo(0x12u),
        UpdatePlayerInfo(0x13u),
    }

    internal class UpdateCurrentTrack internal constructor(
        artist: String = "",
        album: String = "",
        title: String = "",
        /**
         * Length of current track in milliseconds
         */
        trackLength: Int? = null,
        trackCount: Int? = null,
        currentTrack: Int? = null
    ) : MusicControl(Message.UpdateCurrentTrack) {
        internal val artist: SString = SString(m, artist)
        internal val album: SString = SString(m, album)
        internal val title: SString = SString(m, title)
        internal val trackLength: SOptional<UInt> = SOptional(
            m,
            SUInt(StructMapper(), trackLength?.toUInt() ?: 0u, Endian.Little),
            trackLength != null
        )
        internal val trackCount: SOptional<UInt> = SOptional(
            m,
            SUInt(StructMapper(), trackCount?.toUInt() ?: 0u, Endian.Little),
            trackCount != null
        )
        internal val currentTrack: SOptional<UInt> = SOptional(
            m,
            SUInt(StructMapper(), currentTrack?.toUInt() ?: 0u, Endian.Little),
            currentTrack != null
        )
    }

    internal class UpdatePlayStateInfo internal constructor(
        playbackState: PlaybackState = PlaybackState.Unknown,
        /**
         * Current playback position in milliseconds
         */
        trackPosition: UInt = 0u,
        /**
         * Play rate in percentage (100 = normal speed)
         */
        playRate: UInt = 0u,
        shuffle: ShuffleState = ShuffleState.Unknown,
        repeat: RepeatState = RepeatState.Unknown,
        /**
         * Whether next/previous seeks within the track instead of changing track. Watches that
         * predate this field ignore the trailing byte.
         */
        skipSeeksWithinTrack: Boolean = false,
    ) : MusicControl(Message.UpdatePlayStateInfo) {
        internal val state: SUByte = SUByte(m, playbackState.value)
        internal val trackPosition: SUInt = SUInt(m, trackPosition, Endian.Little)
        internal val playRate: SUInt = SUInt(m, playRate, Endian.Little)
        internal val shuffle: SUByte = SUByte(m, shuffle.value)
        internal val repeat: SUByte = SUByte(m, repeat.value)
        internal val skipSeeks: SUByte = SUByte(m, if (skipSeeksWithinTrack) SKIP_SEEKS_WITHIN_TRACK else 0u)

        companion object {
            internal const val SKIP_SEEKS_WITHIN_TRACK: UByte = 1u
        }
    }

    internal class UpdateVolumeInfo internal constructor(
        volumePercent: UByte = 0u,
    ) : MusicControl(Message.UpdateVolumeInfo) {
        internal val volumePercent: SUByte = SUByte(m, volumePercent)
    }

    internal class UpdatePlayerInfo internal constructor(
        pkg: String = "",
        name: String = ""
    ) : MusicControl(Message.UpdatePlayerInfo) {
        internal val pkg: SString = SString(m, pkg)
        internal val name: SString = SString(m, name)
    }

    public enum class PlaybackState(public val value: UByte) {
        Paused(0x00u),
        Playing(0x01u),
        Rewinding(0x02u),
        FastForwarding(0x03u),
        Unknown(0x04u),
    }

    internal enum class ShuffleState(val value: UByte) {
        Unknown(0x00u),
        Off(0x01u),
        On(0x02u),
    }

    public enum class RepeatState(public val value: UByte) {
        Unknown(0x00u),
        Off(0x01u),
        One(0x02u),
        All(0x03u),
    }
}

internal fun musicPacketsRegister() {
    PacketRegistry.register(
        ProtocolEndpoint.MUSIC_CONTROL,
        MusicControl.Message.PlayPause.value
    ) { MusicControl(MusicControl.Message.PlayPause) }

    PacketRegistry.register(
        ProtocolEndpoint.MUSIC_CONTROL,
        MusicControl.Message.Pause.value
    ) { MusicControl(MusicControl.Message.Pause) }

    PacketRegistry.register(
        ProtocolEndpoint.MUSIC_CONTROL,
        MusicControl.Message.Play.value
    ) { MusicControl(MusicControl.Message.Play) }

    PacketRegistry.register(
        ProtocolEndpoint.MUSIC_CONTROL,
        MusicControl.Message.NextTrack.value
    ) { MusicControl(MusicControl.Message.NextTrack) }

    PacketRegistry.register(
        ProtocolEndpoint.MUSIC_CONTROL,
        MusicControl.Message.PreviousTrack.value
    ) { MusicControl(MusicControl.Message.PreviousTrack) }

    PacketRegistry.register(
        ProtocolEndpoint.MUSIC_CONTROL,
        MusicControl.Message.VolumeUp.value
    ) { MusicControl(MusicControl.Message.VolumeUp) }

    PacketRegistry.register(
        ProtocolEndpoint.MUSIC_CONTROL,
        MusicControl.Message.VolumeDown.value
    ) { MusicControl(MusicControl.Message.VolumeDown) }

    PacketRegistry.register(
        ProtocolEndpoint.MUSIC_CONTROL,
        MusicControl.Message.GetCurrentTrack.value
    ) { MusicControl(MusicControl.Message.GetCurrentTrack) }

    PacketRegistry.register(
        ProtocolEndpoint.MUSIC_CONTROL,
        MusicControl.Message.UpdateCurrentTrack.value
    ) { MusicControl.UpdateCurrentTrack() }

    PacketRegistry.register(
        ProtocolEndpoint.MUSIC_CONTROL,
        MusicControl.Message.UpdatePlayStateInfo.value
    ) { MusicControl.UpdatePlayStateInfo() }

    PacketRegistry.register(
        ProtocolEndpoint.MUSIC_CONTROL,
        MusicControl.Message.UpdateVolumeInfo.value
    ) { MusicControl.UpdateVolumeInfo() }

    PacketRegistry.register(
        ProtocolEndpoint.MUSIC_CONTROL,
        MusicControl.Message.UpdatePlayerInfo.value
    ) { MusicControl.UpdatePlayerInfo() }
}