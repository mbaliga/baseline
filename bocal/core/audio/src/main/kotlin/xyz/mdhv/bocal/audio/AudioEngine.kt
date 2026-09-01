package xyz.mdhv.bocal.audio

/**
 * The capture seam. Implementations deliver fixed-size analysis frames on a
 * background thread; the callback must return quickly (push into a conflated
 * channel, no DSP inside it).
 */
interface AudioEngine {
    /** Actual capture sample rate; query AFTER start(). */
    val sampleRate: Int

    /** Samples per emitted analysis frame. */
    val frameSize: Int

    val isRunning: Boolean

    /**
     * Begin capture. [onFrame] receives a freshly copied frame every hop.
     * Throws [SecurityException] if RECORD_AUDIO is not granted.
     */
    fun start(onFrame: (FloatArray) -> Unit)

    fun stop()
}
