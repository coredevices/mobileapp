package coredevices.util.transcription

interface InferenceBoost {
    fun acquire()
    fun release()
}

class NoOpInferenceBoost : InferenceBoost {
    override fun acquire() {}
    override fun release() {}
}
