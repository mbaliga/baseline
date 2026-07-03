package xyz.mdhv.bocal.audio

/**
 * Reframes arbitrary-size capture chunks into overlapping analysis frames:
 * emits a [frameSize] window every [hopSize] samples once primed. Single
 * producer; allocation-free on the hot path except the emitted copy.
 */
class FrameHopper(
    val frameSize: Int,
    val hopSize: Int,
) {
    init {
        require(frameSize > 0 && hopSize in 1..frameSize) {
            "invalid frame/hop: $frameSize/$hopSize"
        }
    }

    private val window = FloatArray(frameSize)
    private var filled = 0

    fun push(samples: FloatArray, count: Int = samples.size, onFrame: (FloatArray) -> Unit) {
        var offset = 0
        while (offset < count) {
            val toCopy = minOf(count - offset, frameSize - filled)
            System.arraycopy(samples, offset, window, filled, toCopy)
            filled += toCopy
            offset += toCopy
            if (filled == frameSize) {
                onFrame(window.copyOf())
                System.arraycopy(window, hopSize, window, 0, frameSize - hopSize)
                filled = frameSize - hopSize
            }
        }
    }

    fun reset() {
        filled = 0
    }
}
