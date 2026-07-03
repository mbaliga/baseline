package xyz.mdhv.bocal.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mdhv.bocal.data.bundle.NoteAttempt

class NoteHoldTrackerTest {

    /** Feed a steady note for [frames] frames at [stepMs] spacing starting at [startMs]. */
    private fun NoteHoldTracker.feedSteady(
        midi: Int,
        cents: Double,
        startMs: Long,
        frames: Int,
        stepMs: Long = 10,
        confidence: Double = 0.9,
    ): List<NoteAttempt> {
        val out = mutableListOf<NoteAttempt>()
        for (i in 0 until frames) {
            onSample(midi, cents, confidence, isVoiced = true, centroidHz = null, timestampMs = startMs + i * stepMs)
                ?.let(out::add)
        }
        return out
    }

    @Test
    fun `a single sustained note yields one attempt on flush`() {
        val tracker = NoteHoldTracker(minHoldMs = 250)
        val mid = tracker.feedSteady(midi = 69, cents = -4.0, startMs = 1000, frames = 60) // 590 ms
        assertTrue("no attempt until the hold ends", mid.isEmpty())
        val attempt = tracker.flush()
        assertNotNull(attempt)
        attempt!!
        assertEquals(69, attempt.midi)
        assertEquals(0L, attempt.startOffsetMs) // first sample is the session start
        assertEquals(590L, attempt.holdMs)      // 59 steps * 10 ms
        assertEquals(-4.0, attempt.meanCents, 1e-9)
        assertEquals(0.0, attempt.centsStdDev, 1e-9)
        assertEquals(0.9, attempt.confidence, 1e-9)
    }

    @Test
    fun `changing note finalizes the previous hold`() {
        val tracker = NoteHoldTracker(minHoldMs = 100)
        tracker.feedSteady(midi = 69, cents = 2.0, startMs = 0, frames = 30)     // 290 ms A4
        val onChange = tracker.onSample(71, 1.0, 0.9, true, null, 300)           // B4 begins
        assertNotNull("note change should emit the A4 attempt", onChange)
        assertEquals(69, onChange!!.midi)
        assertEquals(290L, onChange.holdMs)

        tracker.feedSteady(midi = 71, cents = 1.0, startMs = 310, frames = 20)
        val last = tracker.flush()
        assertEquals(71, last!!.midi)
        assertEquals(300L, last.startOffsetMs) // B4 started at 300, session at 0
    }

    @Test
    fun `blip shorter than the minimum hold is discarded`() {
        val tracker = NoteHoldTracker(minHoldMs = 250)
        tracker.feedSteady(midi = 60, cents = 0.0, startMs = 0, frames = 5) // 40 ms
        assertNull(tracker.flush())
    }

    @Test
    fun `brief gap within tolerance does not split the note`() {
        val tracker = NoteHoldTracker(minHoldMs = 100, gapToleranceMs = 120)
        tracker.feedSteady(midi = 62, cents = 3.0, startMs = 0, frames = 20)   // ...to 190
        // 100 ms silence (< tolerance) — a catch-breath.
        val duringGap = tracker.onSample(0, 0.0, 0.0, isVoiced = false, centroidHz = null, timestampMs = 250)
        assertNull(duringGap)
        tracker.feedSteady(midi = 62, cents = 3.0, startMs = 300, frames = 20)
        val attempt = tracker.flush()
        assertNotNull("gap within tolerance keeps one hold", attempt)
        assertEquals(62, attempt!!.midi)
    }

    @Test
    fun `gap beyond tolerance ends the hold at the last voiced frame`() {
        val tracker = NoteHoldTracker(minHoldMs = 100, gapToleranceMs = 120)
        tracker.feedSteady(midi = 64, cents = 0.0, startMs = 0, frames = 20) // last voiced at 190
        // Long silence closes the hold; trailing silence not counted.
        val emitted = tracker.onSample(0, 0.0, 0.0, isVoiced = false, centroidHz = null, timestampMs = 500)
        assertNotNull(emitted)
        assertEquals(190L, emitted!!.holdMs)
    }

    @Test
    fun `std dev reflects wobble`() {
        val tracker = NoteHoldTracker(minHoldMs = 50)
        // Alternate +10 / -10 cents.
        for (i in 0 until 20) {
            val cents = if (i % 2 == 0) 10.0 else -10.0
            tracker.onSample(69, cents, 0.9, true, null, i * 10L)
        }
        val attempt = tracker.flush()!!
        assertEquals(0.0, attempt.meanCents, 1e-9)
        assertTrue("expected wobble, got sd=${attempt.centsStdDev}", attempt.centsStdDev > 9.0)
    }

    @Test
    fun `centroid averaged only over frames that supplied it`() {
        val tracker = NoteHoldTracker(minHoldMs = 50)
        tracker.onSample(69, 0.0, 0.9, true, 1000.0, 0)
        tracker.onSample(69, 0.0, 0.9, true, null, 30)    // frame with no timbre datum
        tracker.onSample(69, 0.0, 0.9, true, 2000.0, 60)  // last voiced at 60 → 60 ms hold
        val attempt = tracker.flush()!!
        assertEquals(1500.0, attempt.meanCentroidHz!!, 1e-9)
    }

    @Test
    fun `reset clears session origin so offsets restart`() {
        val tracker = NoteHoldTracker(minHoldMs = 50)
        tracker.feedSteady(midi = 69, cents = 0.0, startMs = 5000, frames = 20)
        tracker.flush()
        tracker.reset()
        tracker.feedSteady(midi = 60, cents = 0.0, startMs = 9000, frames = 20)
        val attempt = tracker.flush()!!
        assertEquals(0L, attempt.startOffsetMs)
    }
}
