package coredevices.ring.util

import android.media.AudioFormat
import coredevices.util.AudioEncoding

fun AudioEncoding.toAudioFormat(): Int {
    return when (this) {
        AudioEncoding.PCM_16BIT -> AudioFormat.ENCODING_PCM_16BIT
        AudioEncoding.PCM_FLOAT_32BIT -> AudioFormat.ENCODING_PCM_FLOAT
    }
}