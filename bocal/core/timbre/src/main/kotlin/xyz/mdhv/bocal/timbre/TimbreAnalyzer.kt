package xyz.mdhv.bocal.timbre

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One harmonic partial: 1-based index, its frequency, and linear amplitude. */
data class Harmonic(val index: Int, val frequencyHz: Double, val amplitude: Double)

/**
 * Tristimulus timbre coordinates (Pollard & Jansson): T1 = fundamental energy
 * share, T2 = harmonics 2–4, T3 = 5 and above. Sums to 1 for a harmonic tone.
 */
data class Tristimulus(val t1: Double, val t2: Double, val t3: Double)

/** Per-window timbre measurement. */
data class TimbreFrame(
    /** Amplitude-weighted mean frequency — the standard "brightness" correlate. */
    val spectralCentroidHz: Double,
    val harmonics: List<Harmonic>,
    val tristimulus: Tristimulus,
    /** Energy in odd harmonics (3,5,…) over even harmonics (2,4,…); fundamental excluded. */
    val oddEvenRatio: Double,
    val rms: Double,
)

class TimbreAnalyzer(
    private val harmonicCount: Int = 12,
    /** Search half-width around each expected harmonic, as a fraction of f0. */
    private val searchWidth: Double = 0.15,
) {

    /**
     * Analyze one frame given the already-detected fundamental [f0Hz]
     * (from :core:pitch). Frame length must be a power of two.
     */
    fun analyze(frame: FloatArray, sampleRate: Int, f0Hz: Double): TimbreFrame {
        require(f0Hz > 0) { "f0 must be positive" }
        val spectrum = Fft.magnitudeSpectrum(frame)
        val binHz = sampleRate.toDouble() / frame.size

        var weighted = 0.0
        var total = 0.0
        for (k in 1 until spectrum.size) {
            weighted += k * binHz * spectrum[k]
            total += spectrum[k]
        }
        val centroid = if (total == 0.0) 0.0 else weighted / total

        // Harmonic amplitudes: peak-pick near integer multiples of f0.
        val harmonics = ArrayList<Harmonic>(harmonicCount)
        for (h in 1..harmonicCount) {
            val target = f0Hz * h
            if (target >= sampleRate / 2.0) break
            val centerBin = (target / binHz).roundToInt()
            val halfWidth = ((f0Hz * searchWidth) / binHz).roundToInt().coerceAtLeast(1)
            var bestBin = centerBin.coerceIn(0, spectrum.size - 1)
            for (k in (centerBin - halfWidth).coerceAtLeast(0)..
                (centerBin + halfWidth).coerceAtMost(spectrum.size - 1)) {
                if (spectrum[k] > spectrum[bestBin]) bestBin = k
            }
            harmonics.add(Harmonic(h, bestBin * binHz, spectrum[bestBin]))
        }

        val energies = harmonics.map { it.amplitude * it.amplitude }
        val totalEnergy = energies.sum()
        val tristimulus = if (totalEnergy == 0.0) {
            Tristimulus(0.0, 0.0, 0.0)
        } else {
            val t1 = energies.getOrElse(0) { 0.0 }
            val t2 = (1..3).sumOf { energies.getOrElse(it) { 0.0 } }
            val t3 = energies.drop(4).sum()
            Tristimulus(t1 / totalEnergy, t2 / totalEnergy, t3 / totalEnergy)
        }

        var odd = 0.0
        var even = 0.0
        for (h in harmonics) {
            if (h.index == 1) continue
            if (h.index % 2 == 1) odd += h.amplitude * h.amplitude
            else even += h.amplitude * h.amplitude
        }
        val oddEven = if (even == 0.0) Double.POSITIVE_INFINITY else odd / even

        var sum = 0.0
        for (s in frame) sum += s.toDouble() * s

        return TimbreFrame(
            spectralCentroidHz = centroid,
            harmonics = harmonics,
            tristimulus = tristimulus,
            oddEvenRatio = oddEven,
            rms = sqrt(sum / frame.size),
        )
    }
}

/**
 * Streaming "tone steadiness" over a held note: mean/variance of the centroid
 * (brightness stability) and mean spectral flux (frame-to-frame change).
 * Feed it every analyzed frame; read the summary at note end.
 */
class ToneStabilityTracker {
    private var prevSpectrum: DoubleArray? = null
    private var count = 0
    private var centroidMean = 0.0
    private var centroidM2 = 0.0
    private var fluxSum = 0.0
    private var fluxCount = 0

    fun add(frame: FloatArray, sampleRate: Int) {
        val spectrum = Fft.magnitudeSpectrum(frame)
        val binHz = sampleRate.toDouble() / frame.size
        var weighted = 0.0
        var total = 0.0
        for (k in 1 until spectrum.size) {
            weighted += k * binHz * spectrum[k]
            total += spectrum[k]
        }
        val centroid = if (total == 0.0) 0.0 else weighted / total

        // Welford running mean/variance.
        count++
        val delta = centroid - centroidMean
        centroidMean += delta / count
        centroidM2 += delta * (centroid - centroidMean)

        prevSpectrum?.let { prev ->
            var flux = 0.0
            var norm = 0.0
            for (k in spectrum.indices) {
                val d = spectrum[k] - prev[k]
                if (d > 0) flux += d          // positive (onset-style) flux
                norm += abs(spectrum[k])
            }
            if (norm > 0) {
                fluxSum += flux / norm
                fluxCount++
            }
        }
        prevSpectrum = spectrum
    }

    val frameCount: Int get() = count
    val meanCentroidHz: Double get() = centroidMean
    val centroidStdDevHz: Double
        get() = if (count < 2) 0.0 else sqrt(centroidM2 / (count - 1))
    val meanFlux: Double get() = if (fluxCount == 0) 0.0 else fluxSum / fluxCount

    fun reset() {
        prevSpectrum = null
        count = 0
        centroidMean = 0.0
        centroidM2 = 0.0
        fluxSum = 0.0
        fluxCount = 0
    }
}
