package xyz.mdhv.bocal.haptics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticIrTest {

    @Test
    fun `ir json round trips including polymorphic events`() {
        val score = HapticScore(
            events = listOf(
                HapticEvent.Primitive(PrimitiveKind.CLICK, scale = 0.8f, delayMs = 10),
                HapticEvent.Envelope(
                    listOf(
                        Breakpoint(0, 0.1f, 0.9f),
                        Breakpoint(50, 0.6f, 0.4f),
                        Breakpoint(120, 0f, 0.4f),
                    ),
                ),
            ),
        )
        assertEquals(score, HapticScore.fromJson(score.toJson()))
    }

    @Test
    fun `out-of-range scale is rejected`() {
        try {
            HapticScore(events = listOf(HapticEvent.Primitive(PrimitiveKind.TICK, scale = 1.5f)))
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test
    fun `non-monotonic envelope is rejected`() {
        try {
            HapticScore(
                events = listOf(
                    HapticEvent.Envelope(listOf(Breakpoint(50, 0.5f, 0.5f), Breakpoint(50, 0f, 0.5f))),
                ),
            )
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
    }
}

class TunerHapticPlannerTest {

    private fun planner() = TunerHapticPlanner()

    @Test
    fun `entering tune emits one lock pulse, resting in tune stays silent`() {
        val p = planner()
        var t = 0L
        assertNotNull("first in-tune reading should pulse", p.onReading(2.0, true, t))
        repeat(20) {
            t += 100
            assertNull("in-tune should not buzz", p.onReading(1.5, true, t))
        }
    }

    @Test
    fun `hysteresis - wobbling across the enter boundary does not re-trigger`() {
        val p = planner()
        assertNotNull(p.onReading(3.0, true, 0))       // lock
        assertNull(p.onReading(6.5, true, 200))        // between enter(5) and exit(8): still locked
        assertNull(p.onReading(4.0, true, 400))        // back inside: no re-pulse
    }

    @Test
    fun `losing the lock far enough re-arms the pulse`() {
        val p = planner()
        assertNotNull(p.onReading(2.0, true, 0))
        assertNotNull("drift after losing lock should tick", p.onReading(20.0, true, 500))
        assertNotNull("re-entering tune should pulse again", p.onReading(1.0, true, 1000))
    }

    @Test
    fun `drift ticks are rate limited`() {
        val p = planner()
        assertNotNull(p.onReading(30.0, true, 0))
        assertNull(p.onReading(30.0, true, 50))       // too soon
        assertNull(p.onReading(30.0, true, 100))
        assertNotNull(p.onReading(30.0, true, 200))   // interval elapsed
    }

    @Test
    fun `drift strength grows with deviation`() {
        val p = planner()
        val small = p.onReading(10.0, true, 0)!!
        val large = p.onReading(45.0, true, 1000)!!
        val smallScale = (small.events[0] as HapticEvent.Primitive).scale
        val largeScale = (large.events[0] as HapticEvent.Primitive).scale
        assertTrue("expected $largeScale > $smallScale", largeScale > smallScale)
    }

    @Test
    fun `sharp and flat feel different`() {
        val p = planner()
        val sharp = p.onReading(25.0, true, 0)!!.events[0] as HapticEvent.Primitive
        val flat = p.onReading(-25.0, true, 1000)!!.events[0] as HapticEvent.Primitive
        assertEquals(PrimitiveKind.TICK, sharp.kind)
        assertEquals(PrimitiveKind.LOW_TICK, flat.kind)
    }

    @Test
    fun `unvoiced silences and drops the lock`() {
        val p = planner()
        assertNotNull(p.onReading(2.0, true, 0))
        assertNull(p.onReading(0.0, false, 100))
        assertNotNull("after silence, in-tune pulses again", p.onReading(2.0, true, 1000))
    }
}
