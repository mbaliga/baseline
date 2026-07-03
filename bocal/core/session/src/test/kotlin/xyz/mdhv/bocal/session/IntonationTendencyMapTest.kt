package xyz.mdhv.bocal.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mdhv.bocal.data.bundle.NoteAttempt

class IntonationTendencyMapTest {

    private val map = IntonationTendencyMap()

    private fun attempt(midi: Int, meanCents: Double, holdMs: Long = 1000) = NoteAttempt(
        midi = midi, startOffsetMs = 0, holdMs = holdMs, meanCents = meanCents,
        centsStdDev = 0.0, confidence = 0.9,
    )

    @Test
    fun `octaves fold into one pitch class`() {
        // F#4 (66) and F#5 (78) both map to pitch class 6.
        val result = map.fromAttempts(listOf(attempt(66, 8.0), attempt(78, 12.0)))
        assertEquals(1, result.size)
        val fSharp = result[6]!!
        assertEquals("F#", fSharp.noteName)
        assertEquals(2, fSharp.attemptCount)
        assertEquals(10.0, fSharp.meanCents, 1e-9) // equal holds → simple mean
    }

    @Test
    fun `bias is hold-time weighted`() {
        // A long sharp attempt should dominate a short flat one.
        val result = map.fromAttempts(
            listOf(
                attempt(60, 10.0, holdMs = 9000),
                attempt(60, -10.0, holdMs = 1000),
            ),
        )
        val c = result[0]!!
        assertEquals(8.0, c.meanCents, 1e-9) // (9000*10 - 1000*10)/10000
    }

    @Test
    fun `std dev captures within-class spread`() {
        val steady = map.fromAttempts(listOf(attempt(69, 5.0), attempt(69, 5.0)))[9]!!
        val spread = map.fromAttempts(listOf(attempt(69, 20.0), attempt(69, -20.0)))[9]!!
        assertEquals(0.0, steady.stdDevCents, 1e-9)
        assertTrue(spread.stdDevCents > 15.0)
    }

    @Test
    fun `sharpest and flattest pick the extremes and respect sign`() {
        val attempts = listOf(attempt(60, 15.0), attempt(62, -12.0), attempt(64, 1.0))
        assertEquals(0, map.sharpest(attempts)!!.pitchClass)   // C, +15
        assertEquals(2, map.flattest(attempts)!!.pitchClass)   // D, -12
    }

    @Test
    fun `all-sharp set has no flattest, all-flat has no sharpest`() {
        val allSharp = listOf(attempt(60, 5.0), attempt(62, 8.0))
        assertNull(map.flattest(allSharp))
        assertTrue(map.sharpest(allSharp) != null)

        val allFlat = listOf(attempt(60, -5.0), attempt(62, -8.0))
        assertNull(map.sharpest(allFlat))
        assertTrue(map.flattest(allFlat) != null)
    }

    @Test
    fun `empty attempts yield an empty map`() {
        assertTrue(map.fromAttempts(emptyList()).isEmpty())
        assertNull(map.sharpest(emptyList()))
    }

    @Test
    fun `zero-hold attempts still aggregate by count`() {
        val result = map.fromAttempts(listOf(attempt(60, 4.0, holdMs = 0), attempt(60, 8.0, holdMs = 0)))
        assertEquals(6.0, result[0]!!.meanCents, 1e-9)
    }

    @Test
    fun `a zero-hold class keeps its bias even when other classes have hold time`() {
        // Regression: a global hold-weighting flag would zero out C's weight
        // (all zero-hold) once D contributed hold time, erasing C's real bias.
        val attempts = listOf(
            attempt(60, 40.0, holdMs = 0),   // C: consistently +40, but no hold logged
            attempt(60, 40.0, holdMs = 0),
            attempt(62, 5.0, holdMs = 2000), // D: mild sharp, with hold
        )
        val result = map.fromAttempts(attempts)
        assertEquals("C's bias must survive", 40.0, result[0]!!.meanCents, 1e-9)
        assertEquals(5.0, result[2]!!.meanCents, 1e-9)
        // Sharpest is C (+40), not D (+5).
        assertEquals(0, map.sharpest(attempts)!!.pitchClass)
    }
}
