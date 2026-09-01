package dev.baseline.contracts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceTest {
    @Test fun aggregationMovesOnlyRight() {
        assertTrue(ResolutionLaw.canAggregate(Resolution.RAW_SAMPLE, Resolution.SESSION))
        assertTrue(ResolutionLaw.canAggregate(Resolution.DAY, Resolution.DAY))
        assertFalse(ResolutionLaw.canAggregate(Resolution.DAY, Resolution.EVENT))
    }

    @Test(expected = IllegalArgumentException::class)
    fun claimRequiresGrade() {
        EvidenceRecord(
            id = "claim-1",
            subjectId = "person-1",
            recordType = RecordType.CLAIM,
            recordClass = RecordClass.INFERRED,
            time = TimeRange(1, ingestedAtEpochMs = 2, timezoneId = "UTC"),
            resolution = Resolution.DAY,
            provenance = Provenance("model-1", SourceKind.MODEL),
            privacyClass = PrivacyClass.PRIVATE,
            schemaVersion = 1,
            contentHash = "abc",
        )
    }
}
