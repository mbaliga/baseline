package xyz.mdhv.bocal.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakCalculatorTest {

    private val calc = StreakCalculator()
    private val today = 20_000L
    private fun ago(n: Int) = today - n

    @Test
    fun `no practice yields an empty streak`() {
        val s = calc.compute(emptyList(), today)
        assertEquals(0, s.current)
        assertEquals(0, s.longest)
        assertFalse(s.practicedToday)
        assertFalse(s.atRisk)
    }

    @Test
    fun `practicing today alone is a one-day streak`() {
        val s = calc.compute(listOf(today), today)
        assertEquals(1, s.current)
        assertEquals(1, s.longest)
        assertTrue(s.practicedToday)
        assertFalse(s.atRisk)
    }

    @Test
    fun `consecutive days accumulate`() {
        val s = calc.compute(listOf(ago(4), ago(3), ago(2), ago(1), today), today)
        assertEquals(5, s.current)
        assertEquals(5, s.longest)
        assertTrue(s.practicedToday)
        assertEquals(0, s.graceDaysUsed)
    }

    @Test
    fun `a single missed day is forgiven - never miss twice`() {
        // Missed ago(2) but practiced around it and today.
        val s = calc.compute(listOf(ago(4), ago(3), ago(1), today), today)
        assertEquals(4, s.current)
        assertEquals(1, s.graceDaysUsed)
        assertTrue(s.practicedToday)
        assertFalse(s.atRisk)
    }

    @Test
    fun `two consecutive misses break the streak`() {
        // Missed ago(3) and ago(2); resumed ago(1) and today.
        val s = calc.compute(listOf(ago(5), ago(4), ago(1), today), today)
        assertEquals(2, s.current) // only today + yesterday
        assertEquals(0, s.graceDaysUsed)
        assertTrue(s.practicedToday)
    }

    @Test
    fun `missing yesterday puts the streak at risk but alive on grace`() {
        // Last practice was two days ago; yesterday and today not yet practiced.
        val s = calc.compute(listOf(ago(3), ago(2)), today)
        assertEquals(2, s.current)
        assertTrue("should be at risk", s.atRisk)
        assertFalse(s.practicedToday)
        // The missed yesterday IS the grace keeping it alive — count it.
        assertEquals(1, s.graceDaysUsed)
    }

    @Test
    fun `grace count is continuous when the at-risk day is then practiced`() {
        // At risk: last practice 2 days ago, yesterday missed.
        val atRisk = calc.compute(listOf(ago(2)), today)
        assertTrue(atRisk.atRisk)
        // Practicing today bridges the missed day; grace used must not jump.
        val kept = calc.compute(listOf(ago(2), today), today)
        assertFalse(kept.atRisk)
        assertTrue(kept.practicedToday)
        assertEquals(
            "grace-days-used must be stable across practicing the at-risk day",
            atRisk.graceDaysUsed,
            kept.graceDaysUsed,
        )
        assertEquals(1, kept.graceDaysUsed)
    }

    @Test
    fun `two days without practice breaks even the grace`() {
        val s = calc.compute(listOf(ago(4), ago(3)), today)
        assertEquals(0, s.current)
        assertFalse(s.atRisk)
        assertEquals(2, s.longest) // history still recorded
    }

    @Test
    fun `practiced yesterday with today still open is intact and not at risk`() {
        val s = calc.compute(listOf(ago(2), ago(1)), today)
        assertEquals(2, s.current)
        assertFalse(s.practicedToday)
        assertFalse(s.atRisk)
    }

    @Test
    fun `longest reflects history even after a break`() {
        val past = (14..20).map { ago(it) } // 7 consecutive days, long ago
        val recent = listOf(ago(1), today)
        val s = calc.compute(past + recent, today)
        assertEquals(7, s.longest)
        assertEquals(2, s.current)
    }

    @Test
    fun `duplicates and unordered input are handled`() {
        val s = calc.compute(listOf(today, today, ago(1), ago(2), ago(1)), today)
        assertEquals(3, s.current)
        assertTrue(s.practicedToday)
    }

    @Test
    fun `future days are ignored`() {
        val s = calc.compute(listOf(today + 5, today, ago(1)), today)
        assertEquals(2, s.current)
        assertTrue(s.practicedToday)
    }
}
