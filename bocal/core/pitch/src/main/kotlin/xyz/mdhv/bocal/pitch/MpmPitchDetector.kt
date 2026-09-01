package xyz.mdhv.bocal.pitch

import xyz.mdhv.bocal.model.PitchEstimate

/**
 * Clean-room MPM (McLeod Pitch Method), implemented from McLeod & Wyvill,
 * "A Smarter Way To Find Pitch" (ICMC 2005): normalized square difference
 * function (NSDF), key-maximum picking between positive zero crossings, and
 * the k·max threshold that makes octave decisions robust on harmonically
 * rich tones — which is why it's the selectable alternative for winds.
 */
class MpmPitchDetector(
    private val config: PitchDetectorConfig = PitchDetectorConfig(),
    /** McLeod's constant k: accept the first key maximum ≥ k · highest maximum. */
    private val k: Double = 0.9,
    /** Minimum clarity (NSDF peak value) to call the frame voiced. */
    private val clarityThreshold: Double = 0.6,
) : PitchDetector {

    override fun analyze(frame: FloatArray, sampleRate: Int): PitchEstimate {
        if (rms(frame) < config.silenceRmsThreshold) return PitchEstimate.UNVOICED

        val n = frame.size
        val tauMin = (sampleRate / config.maxFrequencyHz).toInt().coerceAtLeast(2)
        val tauMax = (sampleRate / config.minFrequencyHz).toInt().coerceAtMost(n - 2)
        if (tauMin >= tauMax) return PitchEstimate.UNVOICED

        // NSDF n(τ) = 2·r(τ) / m(τ) with r the autocorrelation and m the sum
        // of squares of both windows (McLeod & Wyvill eq. 6–9).
        val nsdf = DoubleArray(tauMax + 1)
        for (tau in 0..tauMax) {
            var acf = 0.0
            var norm = 0.0
            for (j in 0 until n - tau) {
                val x0 = frame[j].toDouble()
                val x1 = frame[j + tau].toDouble()
                acf += x0 * x1
                norm += x0 * x0 + x1 * x1
            }
            nsdf[tau] = if (norm == 0.0) 0.0 else 2.0 * acf / norm
        }

        // Key maxima: the highest NSDF value between each positive-going zero
        // crossing and the following negative-going one.
        val maxPositions = ArrayList<Int>()
        // Walk out of the trivial lobe around τ=0 first — starting at tauMin
        // would land inside the true peak's lobe for high pitches and skip it.
        var pos = 2
        while (pos <= tauMax && nsdf[pos] > 0) pos++
        while (pos <= tauMax) {
            // Find next positive-going crossing.
            while (pos <= tauMax && nsdf[pos] <= 0) pos++
            if (pos > tauMax) break
            var peak = pos
            while (pos <= tauMax && nsdf[pos] > 0) {
                if (nsdf[pos] > nsdf[peak]) peak = pos
                pos++
            }
            maxPositions.add(peak)
        }
        if (maxPositions.isEmpty()) return PitchEstimate.UNVOICED

        val highest = maxPositions.maxOf { nsdf[it] }
        val cutoff = k * highest
        val chosen = maxPositions.first { nsdf[it] >= cutoff }

        val clarity = nsdf[chosen]
        if (clarity < clarityThreshold) return PitchEstimate.UNVOICED

        val refinedTau = parabolicInterpolate(nsdf, chosen)
        val frequency = sampleRate / refinedTau
        if (frequency < config.minFrequencyHz || frequency > config.maxFrequencyHz) {
            return PitchEstimate.UNVOICED
        }

        return PitchEstimate(
            frequencyHz = frequency,
            confidence = clarity.coerceIn(0.0, 1.0),
            isVoiced = true,
        )
    }
}
