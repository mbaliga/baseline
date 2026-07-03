package xyz.mdhv.bocal.model.equipment

/** Why a reed is being flagged for retirement. */
enum class RetirementReason { SESSIONS, AGE, BOTH }

data class ReedRetirement(val reedId: String, val reason: RetirementReason)

/**
 * Advises which reed to play next and which are worn out. Deterministic and
 * pure so it's headless-testable. Thresholds are conservative defaults; the
 * equipment feature can expose them per player later.
 */
class ReedRotationAdvisor(
    /** Retire a reed after it's been played this many sessions. */
    private val retireAfterSessions: Int = 40,
    /** Retire a reed this many days after first use. */
    private val retireAfterDays: Int = 90,
) {
    init {
        require(retireAfterSessions > 0 && retireAfterDays > 0)
    }

    /**
     * The reed to play next to even out wear: among non-retired reeds, the one
     * played fewest sessions. Ties broken deterministically — oldest first use
     * (bring the aging reed back in), then id — so the recommendation never
     * flickers between equal candidates.
     */
    fun nextReed(reeds: List<Reed>, todayEpochDay: Long): Reed? {
        val eligible = reeds.filter { !it.retired && !isWornOut(it, todayEpochDay) }
        return eligible.minWithOrNull(
            compareBy<Reed> { it.sessionsPlayed }
                .thenBy { it.firstUsedEpochDay ?: Long.MAX_VALUE }
                .thenBy { it.id },
        )
    }

    /** Non-retired reeds that have crossed a wear threshold, with the reason. */
    fun dueForRetirement(reeds: List<Reed>, todayEpochDay: Long): List<ReedRetirement> =
        reeds.asSequence()
            .filter { !it.retired }
            .mapNotNull { reed ->
                val bySessions = reed.sessionsPlayed >= retireAfterSessions
                val byAge = (reed.daysInService(todayEpochDay) ?: 0) >= retireAfterDays
                val reason = when {
                    bySessions && byAge -> RetirementReason.BOTH
                    bySessions -> RetirementReason.SESSIONS
                    byAge -> RetirementReason.AGE
                    else -> null
                }
                reason?.let { ReedRetirement(reed.id, it) }
            }
            .toList()

    private fun isWornOut(reed: Reed, todayEpochDay: Long): Boolean =
        reed.sessionsPlayed >= retireAfterSessions ||
            (reed.daysInService(todayEpochDay) ?: 0) >= retireAfterDays
}
