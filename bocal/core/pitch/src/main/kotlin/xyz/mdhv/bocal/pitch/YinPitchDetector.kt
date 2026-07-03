package xyz.mdhv.bocal.pitch

import xyz.mdhv.bocal.model.PitchEstimate

/**
 * Clean-room YIN, implemented from de Cheveigné & Kawahara (JASA 2002),
 * steps 1–5: difference function, cumulative-mean normalization, absolute
 * threshold, parabolic interpolation. Step 6 (best local estimate) is
 * intentionally omitted — at tuner frame sizes it buys little.
 *
 * Time-domain O(W·τmax); ~2M multiply-adds for a 4096 frame at 48 kHz,
 * comfortably real-time. An FFT difference function (FastYin) is a
 * drop-in optimization behind the same interface if profiling demands it.
 */
class YinPitchDetector(
    private val config: PitchDetectorConfig = PitchDetectorConfig(),
    /**
     * Absolute threshold from the paper's step 4; 0.10–0.15 trades octave
     * errors (higher) against dropouts (lower).
     */
    private val threshold: Double = 0.12,
) : PitchDetector {

    override fun analyze(frame: FloatArray, sampleRate: Int): PitchEstimate {
        if (rms(frame) < config.silenceRmsThreshold) return PitchEstimate.UNVOICED

        val w = frame.size / 2
        val tauMin = (sampleRate / config.maxFrequencyHz).toInt().coerceAtLeast(2)
        val tauMax = (sampleRate / config.minFrequencyHz).toInt().coerceAtMost(w - 1)
        if (tauMin >= tauMax) return PitchEstimate.UNVOICED

        // Step 2: difference function d(τ) over a window of W samples.
        val d = DoubleArray(tauMax + 1)
        for (tau in 1..tauMax) {
            var sum = 0.0
            for (j in 0 until w) {
                val delta = frame[j].toDouble() - frame[j + tau]
                sum += delta * delta
            }
            d[tau] = sum
        }

        // Step 3: cumulative mean normalized difference d'(τ); d'(0) = 1.
        val dNorm = DoubleArray(tauMax + 1)
        dNorm[0] = 1.0
        var runningSum = 0.0
        for (tau in 1..tauMax) {
            runningSum += d[tau]
            dNorm[tau] = if (runningSum == 0.0) 1.0 else d[tau] * tau / runningSum
        }

        // Step 4: absolute threshold — first τ where d' dips below, then ride
        // the dip to its local minimum.
        var tauEstimate = -1
        var tau = tauMin
        while (tau <= tauMax) {
            if (dNorm[tau] < threshold) {
                while (tau + 1 <= tauMax && dNorm[tau + 1] < dNorm[tau]) tau++
                tauEstimate = tau
                break
            }
            tau++
        }

        // No dip below threshold: fall back to the global minimum, flagged by
        // confidence; report unvoiced when even that minimum is shallow.
        if (tauEstimate == -1) {
            var best = tauMin
            for (t in tauMin..tauMax) if (dNorm[t] < dNorm[best]) best = t
            tauEstimate = best
        }

        val aperiodicity = dNorm[tauEstimate]
        if (aperiodicity > 0.5) return PitchEstimate.UNVOICED

        // Step 5: parabolic interpolation around the minimum for sub-sample τ.
        val refinedTau = parabolicInterpolate(dNorm, tauEstimate)
        val frequency = sampleRate / refinedTau
        if (frequency < config.minFrequencyHz || frequency > config.maxFrequencyHz) {
            return PitchEstimate.UNVOICED
        }

        return PitchEstimate(
            frequencyHz = frequency,
            confidence = (1.0 - aperiodicity).coerceIn(0.0, 1.0),
            isVoiced = true,
        )
    }
}
