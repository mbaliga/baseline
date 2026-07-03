package xyz.mdhv.bocal

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import xyz.mdhv.bocal.haptics.HapticEvent
import xyz.mdhv.bocal.haptics.HapticRenderer
import xyz.mdhv.bocal.haptics.HapticScore
import xyz.mdhv.bocal.haptics.PrimitiveKind

/**
 * Maps the haptics IR onto whatever this device can actually play.
 *
 * Capability rule (Android docs): if a composition contains ANY unsupported
 * primitive the whole composition is silently dropped — so we check
 * areAllPrimitivesSupported first and otherwise fall back to a plain
 * amplitude one-shot derived from the score's strongest event. Envelope
 * events are approximated by the fallback until the PWLE APIs are broadly
 * available.
 */
class AndroidHapticRenderer(context: Context) : HapticRenderer {

    private val vibrator: Vibrator? = context.getSystemService(Vibrator::class.java)

    override fun render(score: HapticScore) {
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return

        val primitives = score.events.filterIsInstance<HapticEvent.Primitive>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && primitives.isNotEmpty()) {
            val ids = primitives.map { primitiveId(it.kind) }
            if (ids.none { it == null } &&
                vibrator.areAllPrimitivesSupported(*ids.filterNotNull().toIntArray())
            ) {
                val composition = VibrationEffect.startComposition()
                for (primitive in primitives) {
                    composition.addPrimitive(
                        primitiveId(primitive.kind)!!,
                        primitive.scale,
                        primitive.delayMs,
                    )
                }
                vibrator.vibrate(composition.compose())
                return
            }
        }

        // Fallback: one short buzz scaled by the strongest event present.
        val strength = score.events.maxOfOrNull { event ->
            when (event) {
                is HapticEvent.Primitive -> event.scale
                is HapticEvent.Envelope -> event.breakpoints.maxOf { it.intensity }
            }
        } ?: return
        if (strength <= 0f) return
        val amplitude =
            if (vibrator.hasAmplitudeControl()) (strength * 255).toInt().coerceIn(1, 255)
            else VibrationEffect.DEFAULT_AMPLITUDE
        vibrator.vibrate(VibrationEffect.createOneShot(30, amplitude))
    }

    override fun cancel() {
        vibrator?.cancel()
    }

    private fun primitiveId(kind: PrimitiveKind): Int? = when (kind) {
        PrimitiveKind.CLICK -> VibrationEffect.Composition.PRIMITIVE_CLICK
        PrimitiveKind.TICK -> VibrationEffect.Composition.PRIMITIVE_TICK
        PrimitiveKind.QUICK_RISE -> VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
        PrimitiveKind.SLOW_RISE -> VibrationEffect.Composition.PRIMITIVE_SLOW_RISE
        PrimitiveKind.QUICK_FALL -> VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
        PrimitiveKind.LOW_TICK ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK
            } else null
        PrimitiveKind.THUD ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibrationEffect.Composition.PRIMITIVE_THUD
            } else null
        PrimitiveKind.SPIN ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibrationEffect.Composition.PRIMITIVE_SPIN
            } else null
    }
}
