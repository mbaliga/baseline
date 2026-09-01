package dev.baseline.contracts

enum class Resolution { RAW_SAMPLE, WINDOW, EVENT, SESSION, DAY, PERIOD }
enum class RecordClass { RAW, DERIVED, INFERRED, DECLARED }
enum class PrivacyClass { PUBLIC, PERSONAL, PRIVATE, MEDICAL }
enum class SourceKind { APP, DEVICE, IMPORT, PERSON, MODEL }
enum class EvidenceGrade { E0, E1, E2, E3, E4, E5 }
enum class RecordType { ENTITY, OBSERVATION, EVENT, STATE_ESTIMATE, CLAIM, RETRACTION }

data class TimeRange(
    val observedAtEpochMs: Long,
    val effectiveFromEpochMs: Long = observedAtEpochMs,
    val effectiveToEpochMs: Long? = null,
    val ingestedAtEpochMs: Long,
    val timezoneId: String,
) {
    init {
        require(timezoneId.isNotBlank())
        require(effectiveToEpochMs == null || effectiveToEpochMs >= effectiveFromEpochMs)
        require(ingestedAtEpochMs >= 0)
    }
}

data class Provenance(
    val sourceId: String,
    val sourceKind: SourceKind,
    val appVersion: String? = null,
    val deviceModel: String? = null,
    val deviceVersion: String? = null,
    val calibrationState: String? = null,
) {
    init { require(sourceId.isNotBlank()) }
}

data class Measurement(
    val definition: String,
    val value: Double? = null,
    val unit: String? = null,
    val quality: Double? = null,
    val uncertainty: Double? = null,
    val missingReason: String? = null,
) {
    init {
        require(definition.isNotBlank())
        require(quality == null || quality in 0.0..1.0)
        require(uncertainty == null || uncertainty >= 0.0)
        require(value != null || missingReason != null)
    }
}

data class EvidenceRecord(
    val id: String,
    val subjectId: String,
    val recordType: RecordType,
    val recordClass: RecordClass,
    val time: TimeRange,
    val resolution: Resolution,
    val provenance: Provenance,
    val privacyClass: PrivacyClass,
    val schemaVersion: Int,
    val contentHash: String,
    val measurement: Measurement? = null,
    val evidenceGrade: EvidenceGrade? = null,
    val derivedFromIds: Set<String> = emptySet(),
    val supersedesId: String? = null,
    val retractsId: String? = null,
) {
    init {
        require(id.isNotBlank() && subjectId.isNotBlank() && contentHash.isNotBlank())
        require(schemaVersion > 0)
        require(recordType != RecordType.CLAIM || evidenceGrade != null)
        require(recordType != RecordType.RETRACTION || retractsId != null)
        require(recordType == RecordType.RETRACTION || retractsId == null)
        require(recordClass != RecordClass.RAW || derivedFromIds.isEmpty())
    }
}

object ResolutionLaw {
    fun canAggregate(from: Resolution, to: Resolution): Boolean = to.ordinal >= from.ordinal

    fun requireAggregation(from: Resolution, to: Resolution) {
        require(canAggregate(from, to)) { "Cannot fabricate ${to.name} detail from ${from.name}" }
    }
}

object ClaimLanguage {
    fun prefix(grade: EvidenceGrade): String = when (grade) {
        EvidenceGrade.E0 -> "Recorded"
        EvidenceGrade.E1 -> "Associated with"
        EvidenceGrade.E2 -> "Still associated after accounting for"
        EvidenceGrade.E3 -> "Helps predict"
        EvidenceGrade.E4 -> "Evidence suggests an effect"
        EvidenceGrade.E5 -> "Likely caused an average change of"
    }
}
