package xyz.mdhv.bocal.model

/**
 * A single pitch measurement for one analysis frame.
 *
 * @param frequencyHz estimated fundamental frequency; meaningless when [isVoiced] is false
 * @param confidence 0.0–1.0; engines map their internal quality measure onto this
 * @param isVoiced whether the frame contains a periodic (pitched) signal at all
 */
data class PitchEstimate(
    val frequencyHz: Double,
    val confidence: Double,
    val isVoiced: Boolean,
) {
    companion object {
        val UNVOICED = PitchEstimate(0.0, 0.0, isVoiced = false)
    }
}
