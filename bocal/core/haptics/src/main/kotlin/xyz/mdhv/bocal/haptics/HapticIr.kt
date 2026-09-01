package xyz.mdhv.bocal.haptics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The haptics intermediate representation. Two vocabularies, mirroring what
 * Android's vibrator stack actually consumes:
 *
 *  - [HapticEvent.Primitive]: scalable primitives sequenced with delays
 *    (→ VibrationEffect.Composition on capable devices).
 *  - [HapticEvent.Envelope]: breakpoint curves of perceptual intensity and
 *    sharpness over time (→ the PWLE/envelope APIs).
 *
 * The IR is versioned JSON so authored scores travel between apps (the
 * haptics workbench authors; Bocal renders).
 */
@Serializable
data class HapticScore(
    val schemaVersion: Int = SCHEMA_VERSION,
    val events: List<HapticEvent>,
) {
    companion object {
        const val SCHEMA_VERSION = 1

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            classDiscriminator = "type"
        }

        fun fromJson(text: String): HapticScore = json.decodeFromString(serializer(), text)
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    init {
        validate()
    }

    private fun validate() {
        for (event in events) {
            when (event) {
                is HapticEvent.Primitive -> {
                    require(event.scale in 0f..1f) { "primitive scale out of range: ${event.scale}" }
                    require(event.delayMs >= 0)
                }
                is HapticEvent.Envelope -> {
                    require(event.breakpoints.isNotEmpty()) { "empty envelope" }
                    var last = -1
                    for (bp in event.breakpoints) {
                        require(bp.intensity in 0f..1f && bp.sharpness in 0f..1f) {
                            "breakpoint out of range: $bp"
                        }
                        require(bp.timeMs > last) { "breakpoints must be strictly increasing in time" }
                        last = bp.timeMs
                    }
                }
            }
        }
    }
}

@Serializable
sealed interface HapticEvent {

    @Serializable
    @SerialName("primitive")
    data class Primitive(
        val kind: PrimitiveKind,
        /** Perceptual strength 0..1. */
        val scale: Float = 1f,
        /** Pause before this primitive, relative to the previous event's end. */
        val delayMs: Int = 0,
    ) : HapticEvent

    @Serializable
    @SerialName("envelope")
    data class Envelope(
        val breakpoints: List<Breakpoint>,
    ) : HapticEvent
}

/** One control point: perceptual intensity + sharpness at a time offset. */
@Serializable
data class Breakpoint(
    val timeMs: Int,
    val intensity: Float,
    val sharpness: Float,
)

/** Mirrors VibrationEffect.Composition's primitive set. */
enum class PrimitiveKind { CLICK, TICK, LOW_TICK, SLOW_RISE, QUICK_FALL, QUICK_RISE, THUD, SPIN }

/**
 * The render seam. The Android implementation must check
 * arePrimitivesSupported()/areEnvelopeEffectsSupported() and degrade —
 * an unsupported primitive silently kills a whole Composition otherwise.
 */
interface HapticRenderer {
    fun render(score: HapticScore)
    fun cancel()
}
