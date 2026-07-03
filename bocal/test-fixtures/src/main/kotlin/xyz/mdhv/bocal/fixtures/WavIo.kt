package xyz.mdhv.bocal.fixtures

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal 16-bit PCM mono WAV writer, for exporting synthetic fixtures as
 * golden recordings (Tier 2) and for opt-in session recordings later.
 */
object WavIo {

    fun writeMono16(samples: FloatArray, sampleRate: Int, out: OutputStream) {
        val dataSize = samples.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)                    // fmt chunk size
        header.putShort(1)                   // PCM
        header.putShort(1)                   // mono
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2)        // byte rate
        header.putShort(2)                   // block align
        header.putShort(16)                  // bits per sample
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)
        out.write(header.array())

        val body = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) {
            val clamped = s.coerceIn(-1f, 1f)
            body.putShort((clamped * Short.MAX_VALUE).toInt().toShort())
        }
        out.write(body.array())
    }
}
