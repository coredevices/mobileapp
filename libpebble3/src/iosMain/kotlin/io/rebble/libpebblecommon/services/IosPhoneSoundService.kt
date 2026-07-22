package io.rebble.libpebblecommon.services

import co.touchlab.kermit.Logger
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.setActive
import platform.Foundation.NSBundle
import platform.Foundation.NSURL

class IosPhoneSoundService : PhoneSoundService {
    private val logger = Logger.withTag("IosPhoneSoundService")
    private var avPlayer: AVAudioPlayer? = null
    private var soundGeneration = 0L

    init {
        try {
            setupAudioSession()
            prewarmRingtone()
        } catch (e: Exception) {
            logger.w(e) { "Failed to prime audio session" }
        }
    }

    private fun prewarmRingtone() {
        val path = NSBundle.mainBundle.pathForResource("opening", "mp3") ?: return
        val url = NSURL.fileURLWithPath(path)
        val player = AVAudioPlayer(contentsOfURL = url, error = null) ?: return
        player.numberOfLoops = 2
        player.volume = 1.0f
        player.prepareToPlay()
        avPlayer = player
    }

    private fun setupAudioSession() {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, withOptions = AVAudioSessionCategoryOptionDuckOthers, error = null)
        session.setActive(true, error = null)
    }

    override fun playDefaultRingtone() {
        try {
            stopSound()
            setupAudioSession()
            val player = avPlayer ?: run {
                val path = NSBundle.mainBundle.pathForResource("opening", "mp3") ?: run {
                    logger.e { "opening.mp3 not found in bundle" }
                    return
                }
                val url = NSURL.fileURLWithPath(path)
                val p = AVAudioPlayer(contentsOfURL = url, error = null) ?: run {
                    logger.e { "Failed to create AVAudioPlayer" }
                    return
                }
                p.apply {
                    numberOfLoops = 2
                    volume = 1.0f
                }
            }
            avPlayer = player
            player.currentTime = 0.0
            player.play()
            logger.v { "Playing default ringtone in loop" }
        } catch (e: Exception) {
            logger.e(e) { "Error playing default ringtone" }
        }
    }

    override fun stopSound() {
        soundGeneration++
        avPlayer?.stop()
        avPlayer?.prepareToPlay()
    }
}
