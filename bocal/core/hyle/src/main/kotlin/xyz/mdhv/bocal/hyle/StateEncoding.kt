package xyz.mdhv.bocal.hyle

/**
 * The redundant-encoding rule, made unrepresentable to break: a UI state can
 * only be styled through a [StateEncoding], and a StateEncoding cannot exist
 * without a shape, an icon, a luminance tier, and a text label alongside its
 * hue. No surface can encode state by hue alone (brief §5, hard rule #2).
 */
data class StateEncoding(
    /** ARGB hue for the state — never red/green-only meaning. */
    val argb: Long,
    val shape: ShapeToken,
    val icon: IconToken,
    val luminance: LuminanceTier,
    /** Short human label, always rendered or exposed via accessibility semantics. */
    val label: String,
)

enum class ShapeToken { RING, TRIANGLE_UP, TRIANGLE_DOWN, DIAMOND, DOT, BAR }

enum class IconToken { LOCK, ARROW_UP, ARROW_DOWN, PULSE, NONE }

enum class LuminanceTier { BRIGHT, MID, DIM }

/**
 * Canonical tuner state encodings. In-tune is NOT green: it is the bright
 * violet ring with a lock glyph. Sharp/flat share the cyan family but are
 * separated by shape, icon, label, and vertical position in the meter.
 */
object TunerEncodings {
    val IN_TUNE = StateEncoding(
        argb = HylePalette.VIOLET_BRIGHT,
        shape = ShapeToken.RING,
        icon = IconToken.LOCK,
        luminance = LuminanceTier.BRIGHT,
        label = "In tune",
    )
    val SHARP = StateEncoding(
        argb = HylePalette.CYAN,
        shape = ShapeToken.TRIANGLE_UP,
        icon = IconToken.ARROW_UP,
        luminance = LuminanceTier.MID,
        label = "Sharp",
    )
    val FLAT = StateEncoding(
        argb = HylePalette.CYAN,
        shape = ShapeToken.TRIANGLE_DOWN,
        icon = IconToken.ARROW_DOWN,
        luminance = LuminanceTier.MID,
        label = "Flat",
    )
    val LISTENING = StateEncoding(
        argb = HylePalette.GREY,
        shape = ShapeToken.DOT,
        icon = IconToken.NONE,
        luminance = LuminanceTier.DIM,
        label = "Listening",
    )

    val ALL = listOf(IN_TUNE, SHARP, FLAT, LISTENING)
}

/**
 * Guard used by tests (and lint later): flags hues whose red/green channel
 * dominance makes them risky as sole meaning carriers for deuteranopia.
 */
object ColorblindGuard {

    fun isRedGreenRisky(argb: Long): Boolean {
        val r = (argb shr 16 and 0xFF).toInt()
        val g = (argb shr 8 and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        if (max == 0 || max - min < 40) return false // near-neutral: fine
        val hue = hueDegrees(r, g, b)
        val saturated = (max - min).toDouble() / max > 0.35
        // Red→red-orange band and yellow-green→green band (70–160°).
        return saturated && (hue <= 30.0 || hue >= 335.0 || hue in 70.0..160.0)
    }

    private fun hueDegrees(r: Int, g: Int, b: Int): Double {
        val max = maxOf(r, g, b).toDouble()
        val min = minOf(r, g, b).toDouble()
        val delta = max - min
        if (delta == 0.0) return 0.0
        val h = when (max) {
            r.toDouble() -> 60.0 * (((g - b) / delta) % 6.0)
            g.toDouble() -> 60.0 * (((b - r) / delta) + 2.0)
            else -> 60.0 * (((r - g) / delta) + 4.0)
        }
        return if (h < 0) h + 360.0 else h
    }
}
