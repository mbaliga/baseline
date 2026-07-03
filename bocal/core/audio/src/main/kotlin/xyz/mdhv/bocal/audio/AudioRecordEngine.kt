package xyz.mdhv.bocal.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process

/**
 * AudioRecord-based capture: float PCM, mono, UNPROCESSED source (no AGC or
 * noise suppression mangling the tone), reading on a dedicated
 * URGENT_AUDIO-priority thread into a [FrameHopper].
 */
class AudioRecordEngine(
    requestedSampleRate: Int = 48_000,
    override val frameSize: Int = 4096,
    private val hopSize: Int = 480, // 10 ms @ 48 kHz → 100 pitch updates/sec
) : AudioEngine {

    override val sampleRate: Int = requestedSampleRate

    @Volatile
    override var isRunning: Boolean = false
        private set

    private var record: AudioRecord? = null
    private var thread: Thread? = null

    @SuppressLint("MissingPermission") // caller gates on RECORD_AUDIO; start() documents the throw
    override fun start(onFrame: (FloatArray) -> Unit) {
        check(!isRunning) { "already running" }

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT,
        )
        require(minBuffer > 0) { "unsupported capture config at $sampleRate Hz" }

        val audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.UNPROCESSED)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuffer * 4)
            .build()
        check(audioRecord.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord init failed" }

        record = audioRecord
        isRunning = true
        val hopper = FrameHopper(frameSize, hopSize)
        val readBuffer = FloatArray(hopSize)

        thread = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            audioRecord.startRecording()
            try {
                while (isRunning) {
                    val n = audioRecord.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
                    if (n > 0) hopper.push(readBuffer, n, onFrame)
                }
            } finally {
                audioRecord.stop()
                audioRecord.release()
            }
        }.apply {
            name = "bocal-audio-capture"
            start()
        }
    }

    override fun stop() {
        isRunning = false
        thread?.join(1000)
        thread = null
        record = null
    }
}
