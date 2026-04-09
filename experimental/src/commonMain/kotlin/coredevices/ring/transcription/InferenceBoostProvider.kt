package coredevices.ring.transcription

import coredevices.util.transcription.InferenceBoost

interface InferenceBoostProvider : InferenceBoost {
    override fun acquire()
    override fun release()
}

class NoOpInferenceBoostProvider : InferenceBoostProvider {
    override fun acquire() {}
    override fun release() {}
}
