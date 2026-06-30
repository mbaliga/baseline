package xyz.mdhv.baseline.engine.model

/**
 * A generic, multi-channel, uniformly-sampled time-series window — the raw material a
 * sport module segments and extracts features from. The engine itself never interprets
 * the channels; it just provides the container so the capture app, the sport modules, and
 * storage all agree on one shape for IMU / pose / load-cell / EEG streams.
 *
 * Channels are column-major-ish: [channels] names the columns, and each [Sample] carries
 * one value per channel in the same order. This keeps a 6-axis IMU sample as a single
 * cache-friendly DoubleArray rather than a map per sample.
 */
class TimeSeries(
    val channels: List<String>,
    val samples: List<Sample>,
    val sampleRateHz: Double,
) {
    init {
        require(sampleRateHz > 0) { "sampleRateHz must be positive" }
        require(channels.isNotEmpty()) { "channels must not be empty" }
    }

    val size: Int get() = samples.size

    val durationSeconds: Double
        get() = if (samples.size < 2) 0.0 else (samples.size - 1) / sampleRateHz

    private fun channelIndex(name: String): Int {
        val idx = channels.indexOf(name)
        require(idx >= 0) { "no such channel: $name (have $channels)" }
        return idx
    }

    /** Extract a single channel as a DoubleArray, e.g. for FFT or filtering. */
    fun channel(name: String): DoubleArray {
        val idx = channelIndex(name)
        return DoubleArray(samples.size) { i -> samples[i].values[idx] }
    }

    /** A view over [startInclusive, endExclusive) sample indices, same channels/rate. */
    fun slice(startInclusive: Int, endExclusive: Int): TimeSeries {
        require(startInclusive in 0..endExclusive && endExclusive <= samples.size) {
            "bad slice [$startInclusive, $endExclusive) over size ${samples.size}"
        }
        return TimeSeries(channels, samples.subList(startInclusive, endExclusive), sampleRateHz)
    }
}

/**
 * One instant of a [TimeSeries]: a monotonic timestamp and one value per channel.
 * [values] length must equal the owning series' channel count.
 */
class Sample(
    val tNanos: Long,
    val values: DoubleArray,
)
