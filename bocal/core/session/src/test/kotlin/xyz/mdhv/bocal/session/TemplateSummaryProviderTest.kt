package xyz.mdhv.bocal.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mdhv.bocal.model.SessionSummaryInput
import xyz.mdhv.bocal.model.TrendDirection

class TemplateSummaryProviderTest {

    private val provider = TemplateSummaryProvider()

    private fun input(
        attemptCount: Int = 5,
        meanAbsCents: Double = 4.0,
        percentInTune: Double = 80.0,
        meanHoldSeconds: Double = 3.0,
        bias: Double = 0.0,
        trend: TrendDirection? = null,
        personalBest: Boolean = false,
        streak: Int = 0,
        sharpest: String? = null,
        flattest: String? = null,
    ) = SessionSummaryInput(
        attemptCount = attemptCount, meanAbsCents = meanAbsCents, percentInTune = percentInTune,
        meanHoldSeconds = meanHoldSeconds, intonationBiasCents = bias, trendDirection = trend,
        isPersonalBest = personalBest, currentStreakDays = streak, sharpestNote = sharpest,
        flattestNote = flattest,
    )

    @Test
    fun `empty session gets a gentle prompt, not a fake stat`() {
        val text = provider.summarize(input(attemptCount = 0))
        assertTrue(text.contains("No sustained notes"))
        assertFalse(text.contains("%"))
    }

    @Test
    fun `normal session states note count and in-tune percent`() {
        val text = provider.summarize(input(attemptCount = 5, percentInTune = 80.0))
        assertTrue(text, text.contains("5 sustained notes"))
        assertTrue(text, text.contains("80%"))
    }

    @Test
    fun `singular note phrasing`() {
        assertTrue(provider.summarize(input(attemptCount = 1)).contains("1 sustained note"))
    }

    @Test
    fun `sharp lean is named with the worst note`() {
        val text = provider.summarize(input(bias = 9.0, sharpest = "F#5"))
        assertTrue(text, text.contains("sharp"))
        assertTrue(text, text.contains("F#5"))
        assertFalse(text.contains("flat"))
    }

    @Test
    fun `flat lean is named`() {
        val text = provider.summarize(input(bias = -7.0, flattest = "C4"))
        assertTrue(text, text.contains("flat"))
        assertTrue(text, text.contains("C4"))
    }

    @Test
    fun `small bias reads as even, not sharp or flat`() {
        val text = provider.summarize(input(bias = 1.0))
        assertTrue(text, text.contains("even"))
        assertFalse(text.contains("leaned sharp"))
        assertFalse(text.contains("leaned flat"))
    }

    @Test
    fun `personal best is celebrated over a generic trend line`() {
        val text = provider.summarize(input(personalBest = true, trend = TrendDirection.IMPROVING))
        assertTrue(text, text.contains("most accurate session yet"))
    }

    @Test
    fun `improving and declining trends are surfaced honestly`() {
        assertTrue(provider.summarize(input(trend = TrendDirection.IMPROVING)).contains("trending up"))
        assertTrue(provider.summarize(input(trend = TrendDirection.DECLINING)).contains("dipped"))
        // Stable trend adds no trend sentence.
        assertFalse(provider.summarize(input(trend = TrendDirection.STABLE)).contains("trending"))
    }

    @Test
    fun `streaks of two or more are mentioned, ones are not`() {
        assertTrue(provider.summarize(input(streak = 5)).contains("5-day practice streak"))
        assertFalse(provider.summarize(input(streak = 1)).contains("streak"))
    }

    @Test
    fun `summary never leans on color words - colorblind-irrelevant text`() {
        val text = provider.summarize(
            input(bias = 9.0, trend = TrendDirection.IMPROVING, streak = 3, sharpest = "F#5"),
        ).lowercase()
        for (word in listOf("green", "red", "colour", "color")) {
            assertFalse("summary should not reference '$word'", text.contains(word))
        }
    }

    @Test
    fun `summary is deterministic`() {
        val i = input(bias = 6.0, trend = TrendDirection.IMPROVING, streak = 4, sharpest = "A4")
        assertEquals(provider.summarize(i), provider.summarize(i))
    }
}
