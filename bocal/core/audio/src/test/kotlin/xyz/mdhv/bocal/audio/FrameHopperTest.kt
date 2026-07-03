package xyz.mdhv.bocal.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameHopperTest {

    @Test
    fun `emits sliding frames at hop intervals`() {
        val hopper = FrameHopper(frameSize = 8, hopSize = 2)
        val frames = mutableListOf<FloatArray>()
        // Ramp 0..19 pushed in odd-sized chunks to exercise chunk boundaries.
        val input = FloatArray(20) { it.toFloat() }
        hopper.push(input.copyOfRange(0, 3), onFrame = frames::add)
        hopper.push(input.copyOfRange(3, 10), onFrame = frames::add)
        hopper.push(input.copyOfRange(10, 20), onFrame = frames::add)

        // First frame after 8 samples, then one per 2: (20-8)/2 + 1 = 7.
        assertEquals(7, frames.size)
        assertArrayEquals(FloatArray(8) { it.toFloat() }, frames[0], 0f)
        assertArrayEquals(FloatArray(8) { (it + 2).toFloat() }, frames[1], 0f)
        assertArrayEquals(FloatArray(8) { (it + 12).toFloat() }, frames.last(), 0f)
    }

    @Test
    fun `no emission until primed`() {
        val hopper = FrameHopper(frameSize = 1024, hopSize = 256)
        var count = 0
        hopper.push(FloatArray(1023)) { count++ }
        assertEquals(0, count)
        hopper.push(FloatArray(1)) { count++ }
        assertEquals(1, count)
    }

    @Test
    fun `emitted frames are defensive copies`() {
        val hopper = FrameHopper(frameSize = 4, hopSize = 4)
        val captured = mutableListOf<FloatArray>()
        hopper.push(floatArrayOf(1f, 2f, 3f, 4f), onFrame = captured::add)
        hopper.push(floatArrayOf(5f, 6f, 7f, 8f), onFrame = captured::add)
        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 4f), captured[0], 0f)
        assertArrayEquals(floatArrayOf(5f, 6f, 7f, 8f), captured[1], 0f)
    }

    @Test
    fun `reset drops partial fill`() {
        val hopper = FrameHopper(frameSize = 4, hopSize = 2)
        var count = 0
        hopper.push(FloatArray(3)) { count++ }
        hopper.reset()
        hopper.push(FloatArray(3)) { count++ }
        assertEquals(0, count)
        hopper.push(FloatArray(1)) { count++ }
        assertEquals(1, count)
    }

    @Test
    fun `rejects invalid hop`() {
        try {
            FrameHopper(frameSize = 4, hopSize = 8)
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
        assertTrue(true)
    }
}
