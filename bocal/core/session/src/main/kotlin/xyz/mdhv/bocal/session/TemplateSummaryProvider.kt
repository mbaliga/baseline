package xyz.mdhv.bocal.session

import kotlin.math.abs
import kotlin.math.roundToInt
import xyz.mdhv.bocal.model.SessionSummaryInput
import xyz.mdhv.bocal.model.SummaryProvider
import xyz.mdhv.bocal.model.TrendDirection

/**
 * The shipped default [SummaryProvider]: fully offline, deterministic,
 * template-based. No INTERNET permission, no dependency on Urbana or any other
 * project — this is what keeps the "everything stays on your phone" promise
 * intact while still giving the player a plain-language read on their session
 * (strategic analysis §4). An Urbana-backed provider can replace it later
 * behind the same interface.
 *
 * Output is short and encouraging without being dishonest: it praises real
 * improvement, names a concrete thing to work on, and never manufactures a
 * positive trend the numbers don't support.
 */
class TemplateSummaryProvider : SummaryProvider {

    override fun summarize(input: SessionSummaryInput): String {
        if (input.attemptCount == 0) {
            return "No sustained notes this session — give a note a few seconds of steady air and Bocal will start tracking your intonation."
        }

        val sentences = buildList {
            add(openingLine(input))
            biasLine(input)?.let(::add)
            trendLine(input)?.let(::add)
            streakLine(input)?.let(::add)
        }
        return sentences.joinToString(" ")
    }

    private fun openingLine(input: SessionSummaryInput): String {
        val inTune = input.percentInTune.roundToInt()
        val holds = input.attemptCount
        val notes = if (holds == 1) "1 sustained note" else "$holds sustained notes"
        val hold = input.meanHoldSeconds
        val holdText = if (hold >= 0.05) ", held ${formatSeconds(hold)} on average" else ""
        return "You worked $notes$holdText, in tune $inTune% of the time."
    }

    private fun biasLine(input: SessionSummaryInput): String? {
        // Only call out a bias worth acting on.
        if (abs(input.intonationBiasCents) < 3.0) {
            return "Your centering was even — no consistent sharp or flat lean."
        }
        val direction = if (input.intonationBiasCents > 0) "sharp" else "flat"
        val worst = if (input.intonationBiasCents > 0) input.sharpestNote else input.flattestNote
        val magnitude = abs(input.intonationBiasCents).roundToInt()
        val where = worst?.let { " — most on $it" } ?: ""
        return "You leaned $direction by about $magnitude cents on average$where."
    }

    private fun trendLine(input: SessionSummaryInput): String? = when {
        input.isPersonalBest -> "That's your most accurate session yet."
        input.trendDirection == TrendDirection.IMPROVING ->
            "Your accuracy is trending up across recent sessions."
        input.trendDirection == TrendDirection.DECLINING ->
            "Accuracy has dipped lately — worth a slower, listening-focused session."
        else -> null
    }

    private fun streakLine(input: SessionSummaryInput): String? = when {
        input.currentStreakDays >= 2 -> "You're on a ${input.currentStreakDays}-day practice streak."
        else -> null
    }

    private fun formatSeconds(seconds: Double): String {
        val rounded = (seconds * 10).roundToInt() / 10.0
        return if (rounded == rounded.toLong().toDouble()) "${rounded.toLong()}s" else "${rounded}s"
    }
}
