package io.rebble.libpebblecommon.packets

import io.rebble.libpebblecommon.protocolhelpers.PacketRegistry
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import io.rebble.libpebblecommon.structmapper.*
import io.rebble.libpebblecommon.util.Endian

open class MusicControl(val message: Message) : PebblePacket(ProtocolEndpoint.MUSIC_CONTROL) {
    val command = SUByte(m, message.value)

    init {
        type = command.get()
    }

    enum class Message(val value: UByte) {
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

    class UpdateCurrentTrack(
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
        val artist = SString(m, artist)
        val album = SString(m, album)
        val title = SString(m, title)
        val trackLength = SOptional(
            m,
            SUInt(StructMapper(), trackLength?.toUInt() ?: 0u, Endian.Little),
            trackLength != null
        )
        val trackCount = SOptional(
            m,
            SUInt(StructMapper(), trackCount?.toUInt() ?: 0u, Endian.Little),
            trackCount != null
        )
        val currentTrack = SOptional(
            m,
            SUInt(StructMapper(), currentTrack?.toUInt() ?: 0u, Endian.Little),
            currentTrack != null
        )
    }

    class UpdatePlayStateInfo(
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
        val state = SUByte(m, playbackState.value)
        val trackPosition = SUInt(m, trackPosition, Endian.Little)
        val playRate = SUInt(m, playRate, Endian.Little)
        val shuffle = SUByte(m, shuffle.value)
        val repeat = SUByte(m, repeat.value)
        val skipSeeks = SUByte(m, if (skipSeeksWithinTrack) SKIP_SEEKS_WITHIN_TRACK else 0u)

        companion object {
            const val SKIP_SEEKS_WITHIN_TRACK: UByte = 1u
        }
    }

    class UpdateVolumeInfo(
        volumePercent: UByte = 0u,
    ) : MusicControl(Message.UpdateVolumeInfo) {
        val volumePercent = SUByte(m, volumePercent)
    }

    class UpdatePlayerInfo(
        pkg: String = "",
        name: String = ""
    ) : MusicControl(Message.UpdatePlayerInfo) {
        val pkg = SString(m, pkg)
        val name = SString(m, name)
    }

    enum class PlaybackState(val value: UByte) {
        Paused(0x00u),
        Playing(0x01u),
        Rewinding(0x02u),
        FastForwarding(0x03u),
        Unknown(0x04u),
    }

    enum class ShuffleState(val value: UByte) {
        Unknown(0x00u),
        Off(0x01u),
        On(0x02u),
    }

    enum class RepeatState(val value: UByte) {
        Unknown(0x00u),
        Off(0x01u),
        One(0x02u),
        All(0x03u),
    }
}

fun musicPacketsRegister() {
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