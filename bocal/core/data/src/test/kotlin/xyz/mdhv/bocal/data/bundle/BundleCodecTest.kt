package xyz.mdhv.bocal.data.bundle

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundleCodecTest {

    private fun sampleBundle() = SessionBundle(
        session = PracticeSession(
            id = "s-001",
            startedAtEpochMs = 1_750_000_000_000,
            durationMs = 25 * 60 * 1000,
            instrument = "alto sax",
            a4Hz = 440.0,
            notes = "long tones, F major scale",
        ),
        attempts = listOf(
            NoteAttempt(
                midi = 69, startOffsetMs = 1200, holdMs = 4800,
                meanCents = -6.3, centsStdDev = 4.1, confidence = 0.93,
                meanCentroidHz = 1420.0,
            ),
            NoteAttempt(
                midi = 72, startOffsetMs = 9000, holdMs = 3200,
                meanCents = 2.8, centsStdDev = 7.7, confidence = 0.88,
            ),
        ),
        annotations = listOf(
            Annotation(id = "a-1", timestampMs = 4000, noteIndex = 0, author = "coach", text = "Support!"),
            Annotation(id = "a-2", timestampMs = 0, noteIndex = null, author = "me", text = "reed #3"),
        ),
    )

    @Test
    fun `json round trip is deep equal`() {
        val original = sampleBundle()
        val decoded = BundleCodec.decodeJson(BundleCodec.encodeJson(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `zip round trip preserves bundle and audio bytes`() {
        val audio = mapOf(
            "attempt-0.wav" to byteArrayOf(1, 2, 3, 4, 5),
            "attempt-1.wav" to ByteArray(1000) { (it % 127).toByte() },
        )
        val out = ByteArrayOutputStream()
        BundleCodec.write(sampleBundle(), audio, out)

        val loaded = BundleCodec.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals(sampleBundle().session, loaded.bundle.session)
        assertEquals(sampleBundle().attempts, loaded.bundle.attempts)
        assertEquals(listOf("attempt-0.wav", "attempt-1.wav"), loaded.bundle.audioEntries)
        assertArrayEquals(audio["attempt-0.wav"], loaded.audio["attempt-0.wav"])
        assertArrayEquals(audio["attempt-1.wav"], loaded.audio["attempt-1.wav"])
    }

    @Test
    fun `unknown fields from a newer minor revision are tolerated`() {
        // Same schemaVersion, extra additive field — must decode, not crash.
        val jsonWithExtra = BundleCodec.encodeJson(sampleBundle())
            .replaceFirst("{", """{"someFutureField":{"nested":true},""")
        val decoded = BundleCodec.decodeJson(jsonWithExtra)
        assertEquals(sampleBundle(), decoded)
    }

    @Test
    fun `bundle from a future schema version is rejected clearly`() {
        val future = BundleCodec.encodeJson(sampleBundle())
            .replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":99")
        try {
            BundleCodec.decodeJson(future)
            throw AssertionError("expected UnsupportedSchemaException")
        } catch (e: BundleCodec.UnsupportedSchemaException) {
            assertEquals(99, e.version)
        }
    }

    @Test
    fun `schema version is always written explicitly`() {
        assertTrue(BundleCodec.encodeJson(sampleBundle()).contains("\"schemaVersion\":1"))
    }
}
