package dev.baseline.ledger

import dev.baseline.contracts.*
import org.junit.Assert.assertEquals
import org.junit.Test

class EvidenceLedgerTest {
    private fun record(
        id: String,
        hash: String = id,
        type: RecordType = RecordType.OBSERVATION,
        retracts: String? = null,
    ) = EvidenceRecord(
        id = id,
        subjectId = "person",
        recordType = type,
        recordClass = if (type == RecordType.RETRACTION) RecordClass.DECLARED else RecordClass.RAW,
        time = TimeRange(1, ingestedAtEpochMs = 2, timezoneId = "UTC"),
        resolution = Resolution.EVENT,
        provenance = Provenance("test", SourceKind.APP),
        privacyClass = PrivacyClass.PRIVATE,
        schemaVersion = 1,
        contentHash = hash,
        measurement = if (type == RecordType.RETRACTION) null else Measurement("score", 9.0, "points"),
        retractsId = retracts,
    )

    @Test fun importsAreIdempotentAndRetractionsAreAppendOnly() {
        val ledger = InMemoryEvidenceLedger()
        assertEquals(AppendResult.Appended, ledger.append(record("shot-1")))
        assertEquals(AppendResult.AlreadyPresent, ledger.append(record("shot-1")))
        ledger.append(record("retract-1", type = RecordType.RETRACTION, retracts = "shot-1"))
        assertEquals(2, ledger.history("person").size)
        assertEquals(0, ledger.active("person").size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun stableIdCannotSilentlyOverwrite() {
        val ledger = InMemoryEvidenceLedger()
        ledger.append(record("shot-1"))
        ledger.append(record("shot-1", hash = "different"))
    }
}
