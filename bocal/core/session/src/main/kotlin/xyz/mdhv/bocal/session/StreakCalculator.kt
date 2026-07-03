package xyz.mdhv.bocal.session

/**
 * The state of a practice streak under a humane, "never miss twice" rule.
 *
 * @param current length of the streak ending at the most recent practice day
 * @param longest longest streak ever achieved under the same rule
 * @param practicedToday whether [today] itself has a practice day
 * @param graceDaysUsed single-day gaps forgiven inside the current streak
 * @param atRisk true when yesterday was missed and the streak survives only on
 *   grace — practicing today keeps it, missing today too breaks it
 */
data class StreakState(
    val current: Int,
    val longest: Int,
    val practicedToday: Boolean,
    val graceDaysUsed: Int,
    val atRisk: Boolean,
)

/**
 * Computes practice streaks that tolerate a single missed day.
 *
 * Streak research is consistent that rigid all-or-nothing streaks backfire —
 * one slip triggers loss-aversion and people quit (brief caveats). So this
 * uses "never miss twice": a streak continues across a single missed day and
 * breaks only on two consecutive missed days. Deterministic and pure.
 */
class StreakCalculator {

    /**
     * @param practiceDays epoch-day values on which the user practiced (any
     *   order, duplicates ignored)
     * @param today epoch-day to evaluate the streak as of
     */
    fun compute(practiceDays: Collection<Long>, today: Long): StreakState {
        val days = practiceDays.filter { it <= today }.toSortedSet().toLongArray()
        if (days.isEmpty()) {
            return StreakState(0, 0, practicedToday = false, graceDaysUsed = 0, atRisk = false)
        }

        // Longest run anywhere in history, tolerating single-day (<=2 apart) gaps.
        var longest = 1
        var runLen = 1
        for (i in 1 until days.size) {
            runLen = if (days[i] - days[i - 1] <= 2) runLen + 1 else 1
            if (runLen > longest) longest = runLen
        }

        // Current run: walk back from the most recent practice day.
        val lastDay = days.last()
        var currentLen = 1
        var graceUsed = 0
        for (i in days.size - 1 downTo 1) {
            val gap = days[i] - days[i - 1]
            when {
                gap <= 1 -> currentLen++            // consecutive day
                gap == 2L -> { currentLen++; graceUsed++ } // one forgiven miss
                else -> break                        // two+ missed days: run ends here
            }
        }

        val gapToToday = today - lastDay
        return when {
            // Last practice 2+ full days ago (missed yesterday AND the day before):
            // the streak is broken as of today.
            gapToToday >= 3 -> StreakState(0, longest, practicedToday = false, graceDaysUsed = 0, atRisk = false)
            // Missed exactly yesterday: alive on grace, one more miss breaks it.
            // Count that trailing forgiven day so graceDaysUsed agrees with
            // atRisk and stays continuous once today is practiced (the missed
            // day then becomes an ordinary interior gap already tallied).
            gapToToday == 2L -> StreakState(currentLen, longest, practicedToday = false, graceDaysUsed = graceUsed + 1, atRisk = true)
            // Practiced today, or yesterday with today still open: intact.
            else -> StreakState(
                current = currentLen,
                longest = longest,
                practicedToday = gapToToday == 0L,
                graceDaysUsed = graceUsed,
                atRisk = false,
            )
        }
    }
}
