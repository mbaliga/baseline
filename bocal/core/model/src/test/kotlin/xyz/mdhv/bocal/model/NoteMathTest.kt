package xyz.mdhv.bocal.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteMathTest {

    @Test
    fun `a440 resolves to A4 at zero cents`() {
        val note = NoteMath.resolve(440.0)
        assertEquals(69, note.midi)
        assertEquals("A", note.name)
        assertEquals(4, note.octave)
        assertEquals("A4", note.label)
        assertEquals(0.0, note.cents, 1e-9)
    }

    @Test
    fun `middle C and boundary octaves`() {
        assertEquals("C4", NoteMath.resolve(261.6256).label)
        assertEquals("C-1", NoteMath.resolve(8.1758).label)
        assertEquals("B3", NoteMath.resolve(246.94).label)
    }

    @Test
    fun `sharp and flat deviations are signed`() {
        // 10 cents sharp of A4.
        val sharp = NoteMath.resolve(440.0 * Math.pow(2.0, 10.0 / 1200.0))
        assertEquals(69, sharp.midi)
        assertEquals(10.0, sharp.cents, 1e-6)

        val flat = NoteMath.resolve(440.0 * Math.pow(2.0, -10.0 / 1200.0))
        assertEquals(69, flat.midi)
        assertEquals(-10.0, flat.cents, 1e-6)
    }

    @Test
    fun `rounding boundary at 50 cents goes to the nearer note`() {
        // 49 cents sharp still resolves to A4; 51 cents resolves to A#4.
        val below = NoteMath.resolve(440.0 * Math.pow(2.0, 49.0 / 1200.0))
        assertEquals(69, below.midi)
        val above = NoteMath.resolve(440.0 * Math.pow(2.0, 51.0 / 1200.0))
        assertEquals(70, above.midi)
        assertEquals(-49.0, above.cents, 1e-6)
    }

    @Test
    fun `alternate tuning reference shifts resolution`() {
        val baroque = Tuning(a4Hz = 415.0)
        val note = NoteMath.resolve(415.0, baroque)
        assertEquals(69, note.midi)
        assertEquals(0.0, note.cents, 1e-9)
    }

    @Test
    fun `midi to frequency round trips`() {
        for (midi in 21..108) {
            val hz = NoteMath.midiToFrequency(midi)
            assertEquals(midi.toDouble(), NoteMath.frequencyToMidi(hz), 1e-9)
        }
    }

    @Test
    fun `cents between frequencies`() {
        assertEquals(1200.0, NoteMath.centsBetween(220.0, 440.0), 1e-9)
        assertEquals(-1200.0, NoteMath.centsBetween(440.0, 220.0), 1e-9)
        assertEquals(100.0, NoteMath.centsBetween(440.0, NoteMath.midiToFrequency(70)), 1e-6)
    }

    @Test
    fun `transposition for common saxes`() {
        // Concert A4 is written B4 on tenor (B-flat) and F#5 on alto (E-flat).
        assertEquals("B4", labelOf(NoteMath.writtenMidi(69, Transposition.B_FLAT)))
        assertEquals("F#5", labelOf(NoteMath.writtenMidi(69, Transposition.E_FLAT)))
        assertEquals("A4", labelOf(NoteMath.writtenMidi(69, Transposition.CONCERT)))
    }

    private fun labelOf(midi: Int) = "${NoteMath.noteName(midi)}${NoteMath.octave(midi)}"
}
