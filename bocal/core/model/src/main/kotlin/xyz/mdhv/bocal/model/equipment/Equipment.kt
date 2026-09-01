package xyz.mdhv.bocal.model.equipment

/** What a logged piece of gear is. */
enum class EquipmentKind { INSTRUMENT, REED, MOUTHPIECE, LIGATURE, OTHER }

/**
 * A generic equipment-log entry (instrument, mouthpiece, ligature). Reeds get
 * their own richer [Reed] type because only they carry rotation/wear logic.
 */
data class EquipmentItem(
    val id: String,
    val kind: EquipmentKind,
    val label: String,
    val acquiredEpochDay: Long? = null,
    val notes: String? = null,
    val retired: Boolean = false,
)

/**
 * A single reed, tracked for rotation and lifespan. Reeds are consumables:
 * rotating a set evens out wear, and each reed is retired once it has been
 * played enough sessions or has aged past its useful life (brief MVP §4.5).
 */
data class Reed(
    val id: String,
    val label: String,
    /** Free-text strength, e.g. "3" or "3.5" — kept as text to fit any brand scale. */
    val strength: String? = null,
    val firstUsedEpochDay: Long? = null,
    val sessionsPlayed: Int = 0,
    val retired: Boolean = false,
) {
    init {
        require(sessionsPlayed >= 0) { "sessionsPlayed cannot be negative: $sessionsPlayed" }
    }

    /** Whole days in service as of [todayEpochDay], or null if never played. */
    fun daysInService(todayEpochDay: Long): Long? =
        firstUsedEpochDay?.let { (todayEpochDay - it).coerceAtLeast(0) }
}
