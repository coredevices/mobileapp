package coredevices.ring.service

import android.content.Context
import android.view.KeyEvent
import org.koin.mp.KoinPlatform

private fun dispatchMediaKey(keyCode: Int) {
    val context: Context = KoinPlatform.getKoin().get()
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    val eventTime = System.currentTimeMillis()
    audioManager.dispatchMediaKeyEvent(
        KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
    )
    audioManager.dispatchMediaKeyEvent(
        KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
    )
}

actual fun onPlayPause() = dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

actual fun onNextTrack() = dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)

actual fun onPreviousTrack() = dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
