package coredevices.ring.service

import android.content.Context
import android.view.KeyEvent
import org.koin.core.Koin
import org.koin.mp.KoinPlatform

actual fun onPlayPause() {
    val context: Context = KoinPlatform.getKoin().get()
    // Send play/pause media button event
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    val eventTime = System.currentTimeMillis()
    val downEvent = KeyEvent(
        eventTime,
        eventTime,
        KeyEvent.ACTION_DOWN,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        0
    )
    val upEvent = KeyEvent(
        eventTime,
        eventTime,
        KeyEvent.ACTION_UP,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        0
    )
    audioManager.dispatchMediaKeyEvent(downEvent)
    audioManager.dispatchMediaKeyEvent(upEvent)
}

actual fun onNextTrack() {
    val context: Context = KoinPlatform.getKoin().get()
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    val eventTime = System.currentTimeMillis()
    val downEvent = KeyEvent(
        eventTime,
        eventTime,
        KeyEvent.ACTION_DOWN,
        KeyEvent.KEYCODE_MEDIA_NEXT,
        0
    )
    val upEvent = KeyEvent(
        eventTime,
        eventTime,
        KeyEvent.ACTION_UP,
        KeyEvent.KEYCODE_MEDIA_NEXT,
        0
    )
    audioManager.dispatchMediaKeyEvent(downEvent)
    audioManager.dispatchMediaKeyEvent(upEvent)
}