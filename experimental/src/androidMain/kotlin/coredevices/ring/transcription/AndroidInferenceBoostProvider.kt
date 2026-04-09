package coredevices.ring.transcription

import android.content.Context
import coredevices.ring.service.InferenceForegroundService

class AndroidInferenceBoostProvider(private val context: Context) : InferenceBoostProvider {
    override fun acquire() {
        InferenceForegroundService.acquire(context)
    }

    override fun release() {
        InferenceForegroundService.release(context)
    }
}
