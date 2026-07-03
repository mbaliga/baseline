package xyz.mdhv.bocal.model

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Reference tuning. A4 is configurable (415–466 Hz covers baroque through
 * high-pitch bands); everything else derives from it in equal temperament.
 */
data class Tuning(val a4Hz: Double = 440.0) {
    init {
        require(a4Hz in 200.0..600.0) { "Unreasonable A4 reference: $a4Hz" }
    }
}

/**
 * A frequency resolved against a tuning: the nearest equal-tempered note plus
 * the signed deviation from it in cents (negative = flat, positive = sharp).
 */
data class ResolvedNote(
    val midi: Int,
    val cents: Double,
) {
    val name: String get() = NoteMath.noteName(midi)
    val octave: Int get() = NoteMath.octave(midi)
    /** e.g. "A4", "C#5" */
    val label: String get() = "$name$octave"
}

/** Common transpositions for wind instruments: written pitch = concert pitch + offset. */
enum class Transposition(val semitonesWrittenAboveConcert: Int) {
    CONCERT(0),

    /** B♭ instruments (tenor/soprano sax, clarinet, trumpet): written a major 2nd above concert. */
    B_FLAT(2),

    /** E♭ instruments (alto/baritone sax): written a major 6th above concert. */
    E_FLAT(9),

    /** F instruments (english horn, french horn): written a perfect 5th above concert. */
    F(7),
}

object NoteMath {
    private val NOTE_NAMES =
        arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    const val MIDI_A4 = 69

    /** Fractional MIDI number for a frequency (69.0 = A4 at the tuning reference). */
    fun frequencyToMidi(frequencyHz: Double, tuning: Tuning = Tuning()): Double {
        require(frequencyHz > 0) { "Frequency must be positive: $frequencyHz" }
        return MIDI_A4 + 12.0 * log2(frequencyHz / tuning.a4Hz)
    }

    fun midiToFrequency(midi: Int, tuning: Tuning = Tuning()): Double =
        tuning.a4Hz * 2.0.pow((midi - MIDI_A4) / 12.0)

    /** Nearest note plus signed cents deviation (-50 < cents <= 50). */
    fun resolve(frequencyHz: Double, tuning: Tuning = Tuning()): ResolvedNote {
        val fractionalMidi = frequencyToMidi(frequencyHz, tuning)
        val nearest = fractionalMidi.roundToInt()
        return ResolvedNote(midi = nearest, cents = (fractionalMidi - nearest) * 100.0)
    }

    /** Signed cents from [referenceHz] to [frequencyHz]. */
    fun centsBetween(referenceHz: Double, frequencyHz: Double): Double {
        require(referenceHz > 0 && frequencyHz > 0)
        return 1200.0 * log2(frequencyHz / referenceHz)
    }

    fun noteName(midi: Int): String = NOTE_NAMES[Math.floorMod(midi, 12)]

    fun octave(midi: Int): Int = midi / 12 - 1

    /** The note the player reads for a sounding (concert) pitch on a transposing instrument. */
    fun writtenMidi(concertMidi: Int, transposition: Transposition): Int =
        concertMidi + transposition.semitonesWrittenAboveConcert
}
