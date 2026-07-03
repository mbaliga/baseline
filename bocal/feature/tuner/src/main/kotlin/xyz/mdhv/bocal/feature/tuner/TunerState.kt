package xyz.mdhv.bocal.feature.tuner

import xyz.mdhv.bocal.hyle.StateEncoding
import xyz.mdhv.bocal.hyle.TunerEncodings

enum class TunerStatus {
    LISTENING, IN_TUNE, SHARP, FLAT;

    val encoding: StateEncoding
        get() = when (this) {
            LISTENING -> TunerEncodings.LISTENING
            IN_TUNE -> TunerEncodings.IN_TUNE
            SHARP -> TunerEncodings.SHARP
            FLAT -> TunerEncodings.FLAT
        }
}

data class TunerUiState(
    val status: TunerStatus = TunerStatus.LISTENING,
    /** Written-pitch label for the selected transposition, e.g. "F#5". */
    val noteLabel: String = "—",
    val cents: Double = 0.0,
    val frequencyHz: Double = 0.0,
    val confidence: Double = 0.0,
)

/** In-tune window; matches the haptic planner's lock-enter threshold. */
const val IN_TUNE_CENTS = 5.0

fun statusFor(cents: Double, isVoiced: Boolean): TunerStatus = when {
    !isVoiced -> TunerStatus.LISTENING
    kotlin.math.abs(cents) <= IN_TUNE_CENTS -> TunerStatus.IN_TUNE
    cents > 0 -> TunerStatus.SHARP
    else -> TunerStatus.FLAT
}
