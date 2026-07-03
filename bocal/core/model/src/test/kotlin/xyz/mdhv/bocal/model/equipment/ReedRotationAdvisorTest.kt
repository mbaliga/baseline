package xyz.mdhv.bocal.model.equipment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReedRotationAdvisorTest {

    private val advisor = ReedRotationAdvisor(retireAfterSessions = 40, retireAfterDays = 90)
    private val today = 20_000L

    @Test
    fun `next reed is the least-played eligible one`() {
        val reeds = listOf(
            Reed(id = "a", label = "A", sessionsPlayed = 10, firstUsedEpochDay = 19_990),
            Reed(id = "b", label = "B", sessionsPlayed = 3, firstUsedEpochDay = 19_990),
            Reed(id = "c", label = "C", sessionsPlayed = 7, firstUsedEpochDay = 19_990),
        )
        assertEquals("b", advisor.nextReed(reeds, today)?.id)
    }

    @Test
    fun `ties break to the older reed then id, deterministically`() {
        val reeds = listOf(
            Reed(id = "z", label = "Z", sessionsPlayed = 5, firstUsedEpochDay = 19_995),
            Reed(id = "y", label = "Y", sessionsPlayed = 5, firstUsedEpochDay = 19_980), // oldest
            Reed(id = "x", label = "X", sessionsPlayed = 5, firstUsedEpochDay = 19_995),
        )
        assertEquals("y", advisor.nextReed(reeds, today)?.id)
        // Same call twice → identical answer.
        assertEquals(advisor.nextReed(reeds, today), advisor.nextReed(reeds, today))
    }

    @Test
    fun `never-used reeds sort last by age but still eligible`() {
        val reeds = listOf(
            Reed(id = "new", label = "New", sessionsPlayed = 0, firstUsedEpochDay = null),
            Reed(id = "used", label = "Used", sessionsPlayed = 0, firstUsedEpochDay = 19_990),
        )
        // Both at 0 sessions; the used one is "older" (non-null day), so it wins.
        assertEquals("used", advisor.nextReed(reeds, today)?.id)
    }

    @Test
    fun `retired and worn-out reeds are excluded from rotation`() {
        val reeds = listOf(
            Reed(id = "retired", label = "R", sessionsPlayed = 1, retired = true),
            Reed(id = "worn", label = "W", sessionsPlayed = 40, firstUsedEpochDay = 19_990),
            Reed(id = "good", label = "G", sessionsPlayed = 20, firstUsedEpochDay = 19_990),
        )
        assertEquals("good", advisor.nextReed(reeds, today)?.id)
    }

    @Test
    fun `no eligible reeds yields null`() {
        val reeds = listOf(
            Reed(id = "r", label = "R", sessionsPlayed = 1, retired = true),
            Reed(id = "w", label = "W", sessionsPlayed = 99),
        )
        assertNull(advisor.nextReed(reeds, today))
        assertNull(advisor.nextReed(emptyList(), today))
    }

    @Test
    fun `retirement flagged by sessions, age, or both`() {
        val reeds = listOf(
            Reed(id = "sessions", label = "S", sessionsPlayed = 45, firstUsedEpochDay = today - 10),
            Reed(id = "age", label = "A", sessionsPlayed = 5, firstUsedEpochDay = today - 100),
            Reed(id = "both", label = "B", sessionsPlayed = 50, firstUsedEpochDay = today - 120),
            Reed(id = "fine", label = "F", sessionsPlayed = 5, firstUsedEpochDay = today - 5),
        )
        val due = advisor.dueForRetirement(reeds, today).associate { it.reedId to it.reason }
        assertEquals(RetirementReason.SESSIONS, due["sessions"])
        assertEquals(RetirementReason.AGE, due["age"])
        assertEquals(RetirementReason.BOTH, due["both"])
        assertTrue("fine reed should not be flagged", !due.containsKey("fine"))
    }

    @Test
    fun `already-retired reeds are not re-flagged`() {
        val reeds = listOf(
            Reed(id = "old", label = "O", sessionsPlayed = 99, firstUsedEpochDay = today - 200, retired = true),
        )
        assertTrue(advisor.dueForRetirement(reeds, today).isEmpty())
    }

    @Test
    fun `days in service never goes negative for a future first-use`() {
        val reed = Reed(id = "r", label = "R", firstUsedEpochDay = today + 5)
        assertEquals(0L, reed.daysInService(today))
    }

    @Test
    fun `negative sessions rejected`() {
        try {
            Reed(id = "r", label = "R", sessionsPlayed = -1)
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
    }
}
