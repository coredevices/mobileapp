package io.rebble.libpebblecommon.services

import co.touchlab.kermit.Logger

class AndroidPhoneSoundService : PhoneSoundService {
    private val logger = Logger.withTag("AndroidPhoneSoundService")

    override fun playDefaultRingtone() {
        // TBD: Implement Android ringtone playback (e.g. MediaPlayer with bundled res)
        logger.v { "playDefaultRingtone() - TBD" }
    }

    override fun stopSound() {
        // TBD: Stop Android ringtone playback
        logger.v { "stopSound() - TBD" }
    }
}
