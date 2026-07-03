package xyz.mdhv.bocal.data.bundle

import kotlinx.serialization.Serializable

/**
 * The versioned interchange payload. Bump [SessionBundle.SCHEMA_VERSION] on
 * breaking shape changes and add a migration in [BundleCodec]. Additive,
 * defaulted fields do NOT need a version bump — decoding tolerates unknown
 * keys so newer bundles open in older apps (minus the new data).
 */
@Serializable
data class SessionBundle(
    val schemaVersion: Int = SCHEMA_VERSION,
    val session: PracticeSession,
    val attempts: List<NoteAttempt> = emptyList(),
    /** Coach-exchange annotations (v3 surface, contract designed now). */
    val annotations: List<Annotation> = emptyList(),
    /** Names of audio entries packed alongside bundle.json in the zip. */
    val audioEntries: List<String> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class PracticeSession(
    val id: String,
    val startedAtEpochMs: Long,
    val durationMs: Long,
    /** Free-form instrument label, e.g. "alto sax". */
    val instrument: String? = null,
    /** A4 reference in Hz the session was tuned against. */
    val a4Hz: Double = 440.0,
    val notes: String? = null,
)

/**
 * One attempted/held note with its intonation summary — the atom the
 * per-note tendency maps and the longitudinal engine aggregate over.
 */
@Serializable
data class NoteAttempt(
    /** Concert-pitch MIDI number of the target note. */
    val midi: Int,
    val startOffsetMs: Long,
    val holdMs: Long,
    /** Signed mean deviation while held; negative = tendency flat. */
    val meanCents: Double,
    /** Standard deviation of cents while held — pitch steadiness. */
    val centsStdDev: Double,
    /** Mean detector confidence over the hold. */
    val confidence: Double,
    /** Tone-brightness summary (mean spectral centroid), if timbre ran. */
    val meanCentroidHz: Double? = null,
)

/**
 * A comment anchored to a moment in the session and optionally to a specific
 * attempt. Author is a free label ("me", "coach") — no accounts, ever.
 */
@Serializable
data class Annotation(
    val id: String,
    val timestampMs: Long,
    /** Index into [SessionBundle.attempts], or null for a session-level note. */
    val noteIndex: Int? = null,
    val author: String,
    val text: String,
)
