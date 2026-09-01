package dev.baseline.ledger

import dev.baseline.contracts.EvidenceRecord
import dev.baseline.contracts.RecordType

sealed interface AppendResult {
    data object Appended : AppendResult
    data object AlreadyPresent : AppendResult
}

/** Deterministic Community ledger contract. Persistent adapters must preserve these semantics. */
interface EvidenceLedger {
    fun append(record: EvidenceRecord): AppendResult
    fun get(id: String): EvidenceRecord?
    fun history(subjectId: String): List<EvidenceRecord>
    fun active(subjectId: String): List<EvidenceRecord>
}

class InMemoryEvidenceLedger : EvidenceLedger {
    private val records = linkedMapOf<String, EvidenceRecord>()

    @Synchronized
    override fun append(record: EvidenceRecord): AppendResult {
        val existing = records[record.id]
        if (existing != null) {
            require(existing.contentHash == record.contentHash && existing == record) {
                "Stable id ${record.id} already identifies different content"
            }
            return AppendResult.AlreadyPresent
        }
        record.derivedFromIds.forEach { require(records.containsKey(it)) { "Missing evidence $it" } }
        record.supersedesId?.let { require(records.containsKey(it)) { "Missing superseded record $it" } }
        record.retractsId?.let { target ->
            require(records.containsKey(target)) { "Missing retraction target $target" }
            require(records[target]?.recordType != RecordType.RETRACTION) { "Cannot retract a retraction" }
        }
        records[record.id] = record
        return AppendResult.Appended
    }

    @Synchronized override fun get(id: String): EvidenceRecord? = records[id]

    @Synchronized
    override fun history(subjectId: String): List<EvidenceRecord> =
        records.values.filter { it.subjectId == subjectId }

    @Synchronized
    override fun active(subjectId: String): List<EvidenceRecord> {
        val subjectRecords = history(subjectId)
        val retracted = subjectRecords.mapNotNull { it.retractsId }.toSet()
        val superseded = subjectRecords.mapNotNull { it.supersedesId }.toSet()
        return subjectRecords.filter {
            it.recordType != RecordType.RETRACTION && it.id !in retracted && it.id !in superseded
        }
    }
}
