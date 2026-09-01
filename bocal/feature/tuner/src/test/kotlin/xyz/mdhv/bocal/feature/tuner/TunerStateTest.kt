package xyz.mdhv.bocal.feature.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import xyz.mdhv.bocal.hyle.ShapeToken

class TunerStateTest {

    @Test
    fun `status mapping honors the in-tune window`() {
        assertEquals(TunerStatus.LISTENING, statusFor(0.0, isVoiced = false))
        assertEquals(TunerStatus.IN_TUNE, statusFor(0.0, isVoiced = true))
        assertEquals(TunerStatus.IN_TUNE, statusFor(5.0, isVoiced = true))
        assertEquals(TunerStatus.IN_TUNE, statusFor(-5.0, isVoiced = true))
        assertEquals(TunerStatus.SHARP, statusFor(5.1, isVoiced = true))
        assertEquals(TunerStatus.FLAT, statusFor(-5.1, isVoiced = true))
    }

    @Test
    fun `every status carries a distinct shape - never hue-only state`() {
        val shapes = TunerStatus.entries.map { it.encoding.shape }
        assertEquals(shapes.size, shapes.toSet().size)
        assertNotEquals(
            "sharp and flat must differ in shape",
            TunerStatus.SHARP.encoding.shape,
            TunerStatus.FLAT.encoding.shape,
        )
        assertEquals(ShapeToken.TRIANGLE_UP, TunerStatus.SHARP.encoding.shape)
        assertEquals(ShapeToken.TRIANGLE_DOWN, TunerStatus.FLAT.encoding.shape)
    }
}
