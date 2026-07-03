package xyz.mdhv.bocal.hyle

/**
 * Hyle palette as ARGB longs (0xAARRGGBB). AMOLED-black field, violet north
 * star, cyan counterpart — chosen because violet/cyan sit where deuteranopic
 * vision discriminates well AND differ strongly in luminance.
 */
object HylePalette {
    // Field / surfaces.
    const val FIELD_VOID = 0xFF000000L        // pure black — canvas only
    const val SURFACE_RAISED = 0xFF121212L    // UI surfaces, never pure black
    const val INK_PURE = 0xFFECE8E4L          // warm off-white
    const val GREY = 0xFFAFAFAFL

    // Accents.
    const val VIOLET = 0xFF8E7BFFL
    const val VIOLET_BRIGHT = 0xFFA593FFL
    const val VIOLET_DEEP = 0xFF7867E6L
    const val CYAN = 0xFF08FED5L

    // Provenance glow: warm = on-device, cyan = cloud (Bocal is entirely warm).
    const val PROVENANCE_ON_DEVICE = 0xFFFFD2B0L
    const val PROVENANCE_CLOUD = 0xFF35E0FFL

    /**
     * Wong colorblind-safe categorical ramp for charts — NEVER a red/green
     * ramp (brief §5). Order is the canonical Wong ordering.
     */
    val WONG_CATEGORICAL = longArrayOf(
        0xFF0072B2, // blue
        0xFFE69F00, // orange
        0xFF56B4E9, // sky blue
        0xFF009E73, // bluish green
        0xFFF0E442, // yellow
        0xFFD55E00, // vermillion
        0xFFCC79A7, // reddish purple
    )
}

/** Motion tokens: weight, not bounce. */
object HyleMotion {
    const val DURATION_INSTANT_MS = 120
    const val DURATION_CALM_MS = 300
    const val DURATION_PANE_MS = 420

    /** cubic-bezier(0.4, 0, 0.2, 1) */
    val EASING_STANDARD = doubleArrayOf(0.4, 0.0, 0.2, 1.0)
}
