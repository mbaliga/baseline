package xyz.mdhv.bocal.feature.tuner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.mdhv.bocal.audio.AudioEngine
import xyz.mdhv.bocal.haptics.HapticRenderer
import xyz.mdhv.bocal.haptics.TunerHapticPlanner
import xyz.mdhv.bocal.model.NoteMath
import xyz.mdhv.bocal.model.Transposition
import xyz.mdhv.bocal.model.Tuning
import xyz.mdhv.bocal.pitch.PitchDetector

class TunerViewModel(
    private val engine: AudioEngine,
    private val detector: PitchDetector,
    private val hapticRenderer: HapticRenderer,
    private val planner: TunerHapticPlanner = TunerHapticPlanner(lockEnterCents = IN_TUNE_CENTS),
    private val tuning: Tuning = Tuning(),
    private val transposition: Transposition = Transposition.CONCERT,
    /** Monotonic clock, injected for tests (SystemClock in production). */
    private val monotonicMs: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(TunerUiState())
    val uiState: StateFlow<TunerUiState> = _uiState.asStateFlow()

    // Conflated: the analyzer always works on the freshest frame; audio
    // callbacks never block on a slow analysis pass.
    private val frames = Channel<FloatArray>(Channel.CONFLATED)
    private var analysisJob: Job? = null

    fun start() {
        if (engine.isRunning) return
        analysisJob = viewModelScope.launch(Dispatchers.Default) {
            for (frame in frames) analyze(frame)
        }
        engine.start { frame -> frames.trySend(frame) }
    }

    fun stop() {
        engine.stop()
        analysisJob?.cancel()
        analysisJob = null
        planner.reset()
        _uiState.value = TunerUiState()
    }

    private fun analyze(frame: FloatArray) {
        val estimate = detector.analyze(frame, engine.sampleRate)
        val state = if (!estimate.isVoiced) {
            TunerUiState(status = TunerStatus.LISTENING)
        } else {
            val resolved = NoteMath.resolve(estimate.frequencyHz, tuning)
            val written = NoteMath.writtenMidi(resolved.midi, transposition)
            TunerUiState(
                status = statusFor(resolved.cents, isVoiced = true),
                noteLabel = "${NoteMath.noteName(written)}${NoteMath.octave(written)}",
                cents = resolved.cents,
                frequencyHz = estimate.frequencyHz,
                confidence = estimate.confidence,
            )
        }
        _uiState.value = state

        planner.onReading(
            cents = state.cents,
            isVoiced = estimate.isVoiced,
            nowMs = monotonicMs(),
        )?.let(hapticRenderer::render)
    }

    override fun onCleared() {
        engine.stop()
    }

    class Factory(
        private val engine: AudioEngine,
        private val detector: PitchDetector,
        private val hapticRenderer: HapticRenderer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TunerViewModel(engine, detector, hapticRenderer) as T
    }
}
