package xyz.mdhv.bocal.hyle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateEncodingTest {

    @Test
    fun `no tuner state leans on a red or green hue`() {
        for (encoding in TunerEncodings.ALL) {
            assertFalse(
                "${encoding.label} uses a red/green-risky hue",
                ColorblindGuard.isRedGreenRisky(encoding.argb),
            )
        }
    }

    @Test
    fun `every tuner state carries shape, icon and label redundancy`() {
        for (encoding in TunerEncodings.ALL) {
            assertTrue(encoding.label.isNotBlank())
            // Off-pitch states must carry a directional icon, not rely on hue.
            if (encoding == TunerEncodings.SHARP || encoding == TunerEncodings.FLAT) {
                assertTrue(encoding.icon != IconToken.NONE)
            }
        }
    }

    @Test
    fun `no two tuner states are distinguished by hue alone`() {
        val all = TunerEncodings.ALL
        for (i in all.indices) {
            for (j in i + 1 until all.size) {
                val a = all[i]
                val b = all[j]
                val nonHueDifference =
                    a.shape != b.shape || a.icon != b.icon || a.luminance != b.luminance
                assertTrue(
                    "${a.label} vs ${b.label} differ only by hue",
                    nonHueDifference,
                )
            }
        }
    }

    @Test
    fun `guard actually catches red and green`() {
        assertTrue(ColorblindGuard.isRedGreenRisky(0xFFFF0000)) // pure red
        assertTrue(ColorblindGuard.isRedGreenRisky(0xFF00C000)) // green
        assertTrue(ColorblindGuard.isRedGreenRisky(0xFF5BBF7A)) // "success" green
        assertFalse(ColorblindGuard.isRedGreenRisky(HylePalette.VIOLET))
        assertFalse(ColorblindGuard.isRedGreenRisky(HylePalette.CYAN))
        assertFalse(ColorblindGuard.isRedGreenRisky(HylePalette.GREY))
        assertFalse(ColorblindGuard.isRedGreenRisky(0xFF0072B2)) // Wong blue
    }

    @Test
    fun `wong ramp itself passes the guard except its warning oranges`() {
        // The Wong ramp is colorblind-safe as a SET (distinguishable), and the
        // guard is about state meaning, not chart categories. Sanity: blue and
        // sky blue pass; vermillion is allowed in charts but would be flagged
        // as a state hue.
        assertFalse(ColorblindGuard.isRedGreenRisky(0xFF0072B2))
        assertFalse(ColorblindGuard.isRedGreenRisky(0xFF56B4E9))
        assertTrue(ColorblindGuard.isRedGreenRisky(0xFFD55E00))
    }
}
