package xyz.mdhv.bocal.pitch

import xyz.mdhv.bocal.model.PitchEstimate

/**
 * The seam every pitch engine sits behind — DSP (YIN/MPM) today, neural
 * (SwiftF0 via ONNX) later. Implementations must be allocation-light and
 * safe to call at ≥50 Hz on a dedicated analysis thread.
 */
interface PitchDetector {
    /**
     * Analyze one frame of mono float PCM (typically 2048–4096 samples).
     * Implementations must not retain [frame] after returning.
     */
    fun analyze(frame: FloatArray, sampleRate: Int): PitchEstimate
}

/** Shared knobs across DSP detectors. */
data class PitchDetectorConfig(
    /** Lowest detectable fundamental. 50 Hz covers baritone sax low A (~51.9 Hz). */
    val minFrequencyHz: Double = 50.0,
    /** Highest detectable fundamental. ~2.1 kHz covers C7; altissimo overtones sit above. */
    val maxFrequencyHz: Double = 2200.0,
    /** Frames with RMS below this are reported unvoiced without analysis. */
    val silenceRmsThreshold: Double = 1e-4,
)

internal fun rms(frame: FloatArray): Double {
    var sum = 0.0
    for (s in frame) sum += s.toDouble() * s
    return kotlin.math.sqrt(sum / frame.size)
}

/**
 * Parabolic interpolation of the extremum of [values] around index [i],
 * returning the refined fractional index. Standard three-point fit; used by
 * both YIN (on the difference minimum) and MPM (on the NSDF maximum).
 */
internal fun parabolicInterpolate(values: DoubleArray, i: Int): Double {
    if (i <= 0 || i >= values.size - 1) return i.toDouble()
    val a = values[i - 1]
    val b = values[i]
    val c = values[i + 1]
    val denom = a - 2.0 * b + c
    if (denom == 0.0) return i.toDouble()
    return i + 0.5 * (a - c) / denom
}
