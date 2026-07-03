package xyz.mdhv.bocal.feature.tuner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.bocal.hyle.HylePalette
import xyz.mdhv.bocal.hyle.IconToken

private val FieldBlack = Color(HylePalette.FIELD_VOID)
private val Ink = Color(HylePalette.INK_PURE)
private val Grey = Color(HylePalette.GREY)

private const val METER_RANGE_CENTS = 50.0

/**
 * The tuner surface. State is never hue-only: the indicator's SHAPE (ring vs
 * up/down triangle), the icon glyph, the text label, and luminance all move
 * together with the color (hard rule: colorblind-safe, brief §0).
 */
@Composable
fun TunerScreen(viewModel: TunerViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TunerContent(state)
}

@Composable
fun TunerContent(state: TunerUiState) {
    val encoding = state.status.encoding
    val accent = Color(encoding.argb)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FieldBlack)
            .padding(24.dp)
            .semantics {
                contentDescription = buildString {
                    append(state.noteLabel)
                    append(", ")
                    append(encoding.label)
                    if (state.status != TunerStatus.LISTENING) {
                        append(", ${"%.0f".format(state.cents)} cents")
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Big note label — the primary read.
        Text(
            text = state.noteLabel,
            color = if (state.status == TunerStatus.LISTENING) Grey else Ink,
            fontSize = 96.sp,
            fontWeight = FontWeight.Light,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (state.status == TunerStatus.LISTENING) {
                "—"
            } else {
                "%.1f Hz   %+.0f¢".format(state.frequencyHz, state.cents)
            },
            color = Grey,
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(Modifier.height(40.dp))

        CentsMeter(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .testTag("cents-meter"),
        )

        Spacer(Modifier.height(40.dp))

        StatusChip(state)
    }
}

/** Icon glyphs are text so they inherit accessibility scaling. */
private fun glyphFor(icon: IconToken): String = when (icon) {
    IconToken.LOCK -> "◉"
    IconToken.ARROW_UP -> "↑"
    IconToken.ARROW_DOWN -> "↓"
    IconToken.PULSE -> "…"
    IconToken.NONE -> ""
}

@Composable
private fun StatusChip(state: TunerUiState) {
    val encoding = state.status.encoding
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = glyphFor(encoding.icon),
            color = Color(encoding.argb),
            fontSize = 20.sp,
        )
        if (encoding.icon != IconToken.NONE) Spacer(Modifier.width(8.dp))
        Text(
            text = encoding.label,
            color = Color(encoding.argb),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun CentsMeter(state: TunerUiState, modifier: Modifier = Modifier) {
    val encoding = state.status.encoding
    val accent = Color(encoding.argb)

    Canvas(modifier = modifier) {
        val midY = size.height / 2f
        val trackPad = 12.dp.toPx()

        // Track with tick marks every 10 cents.
        drawLine(
            color = Grey.copy(alpha = 0.35f),
            start = Offset(trackPad, midY),
            end = Offset(size.width - trackPad, midY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        for (tick in -40..40 step 10) {
            if (tick == 0) continue
            val x = xForCents(tick.toDouble(), trackPad)
            drawLine(
                color = Grey.copy(alpha = if (tick % 20 == 0) 0.5f else 0.25f),
                start = Offset(x, midY - 6.dp.toPx()),
                end = Offset(x, midY + 6.dp.toPx()),
                strokeWidth = 1.5f.dp.toPx(),
            )
        }

        // Center target: always-visible ring outline at 0¢.
        val centerX = xForCents(0.0, trackPad)
        drawCircle(
            color = Color(HylePalette.VIOLET).copy(alpha = 0.6f),
            radius = 14.dp.toPx(),
            center = Offset(centerX, midY),
            style = Stroke(width = 2.dp.toPx()),
        )

        if (state.status == TunerStatus.LISTENING) return@Canvas

        val x = xForCents(state.cents.coerceIn(-METER_RANGE_CENTS, METER_RANGE_CENTS), trackPad)
        when (state.status) {
            // In tune: the ring fills — position AND shape AND brightness agree.
            TunerStatus.IN_TUNE -> {
                drawCircle(color = accent, radius = 12.dp.toPx(), center = Offset(centerX, midY))
                drawCircle(
                    color = accent.copy(alpha = 0.35f),
                    radius = 20.dp.toPx(),
                    center = Offset(centerX, midY),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
            // Sharp: upward triangle right of center; flat: downward, left.
            TunerStatus.SHARP -> drawTriangle(x, midY, 12.dp.toPx(), pointingUp = true, color = accent)
            TunerStatus.FLAT -> drawTriangle(x, midY, 12.dp.toPx(), pointingUp = false, color = accent)
            TunerStatus.LISTENING -> Unit
        }
    }
}

private fun DrawScope.xForCents(cents: Double, pad: Float): Float {
    val usable = size.width - 2 * pad
    return (pad + usable * ((cents + METER_RANGE_CENTS) / (2 * METER_RANGE_CENTS))).toFloat()
}

private fun DrawScope.drawTriangle(
    cx: Float,
    cy: Float,
    radius: Float,
    pointingUp: Boolean,
    color: Color,
) {
    val dir = if (pointingUp) -1f else 1f
    val path = Path().apply {
        moveTo(cx, cy + dir * radius)
        lineTo(cx - radius, cy - dir * radius * 0.8f)
        lineTo(cx + radius, cy - dir * radius * 0.8f)
        close()
    }
    drawPath(path, color)
}
