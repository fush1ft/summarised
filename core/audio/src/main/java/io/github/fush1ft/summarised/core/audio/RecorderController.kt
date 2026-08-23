package io.github.fush1ft.summarised.core.audio

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the recording session for the whole app.
 *
 * A singleton rather than something scoped to the screen or the service: the
 * recording must survive the user leaving the record screen, and the service and
 * the UI both need to see the same state. The service keeps the process alive and
 * shows the notification; the actual capture lives here.
 */
@Singleton
class RecorderController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val engine = AudioRecorderEngine()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null

    private val _state = MutableStateFlow(RecorderState())
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    /** Directory holding recorded audio, inside app-private storage. */
    private val recordingsDir: File
        get() = File(context.filesDir, "recordings").apply { mkdirs() }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (_state.value.isActive) return

        val file = File(recordingsDir, "rec_${timestamp()}.wav")
        try {
            engine.start(file)
        } catch (e: IllegalStateException) {
            _state.value = RecorderState(errorMessage = e.message ?: "Could not start recording")
            return
        } catch (e: SecurityException) {
            _state.value = RecorderState(errorMessage = "Microphone permission was denied")
            return
        }

        _state.value = RecorderState(
            status = RecorderStatus.RECORDING,
            outputFile = file,
        )
        captureJob = scope.launch { captureLoop() }
    }

    fun pause() {
        _state.update { current ->
            if (current.status == RecorderStatus.RECORDING) {
                current.copy(status = RecorderStatus.PAUSED)
            } else {
                current
            }
        }
    }

    fun resume() {
        _state.update { current ->
            if (current.status == RecorderStatus.PAUSED) {
                current.copy(status = RecorderStatus.RECORDING)
            } else {
                current
            }
        }
    }

    /**
     * Ends the session and returns what was recorded, or null if nothing usable
     * was captured. The caller is responsible for persisting it.
     */
    fun stop(): FinishedRecording? {
        val current = _state.value
        if (!current.isActive) return null

        captureJob?.cancel()
        captureJob = null

        val bytes = engine.bytesWritten
        engine.stop()
        _state.value = RecorderState()

        val file = current.outputFile ?: return null
        val durationMs = RecordingFormat.durationMsForBytes(bytes)

        // A recording with no audio in it is not worth a row in the library.
        if (durationMs < MIN_USABLE_DURATION_MS) {
            file.delete()
            return null
        }
        return FinishedRecording(file = file, durationMs = durationMs)
    }

    /** Discards the session and deletes the partial file. */
    fun cancel() {
        captureJob?.cancel()
        captureJob = null
        val file = _state.value.outputFile
        engine.stop()
        file?.delete()
        _state.value = RecorderState()
    }

    private suspend fun captureLoop() {
        while (scope.isActive) {
            val paused = _state.value.status == RecorderStatus.PAUSED
            val amplitude = engine.readChunk(discard = paused) ?: continue

            _state.update { current ->
                if (!current.isActive) return@update current
                val amplitudes = (current.amplitudes + amplitude)
                    .takeLast(RecorderState.AMPLITUDE_WINDOW)
                current.copy(
                    // Elapsed time comes from bytes actually written, so a paused
                    // stretch does not count towards the duration and the timer can
                    // never drift away from the length of the audio on disk.
                    elapsedMs = RecordingFormat.durationMsForBytes(engine.bytesWritten),
                    amplitudes = amplitudes,
                )
            }
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private companion object {
        const val MIN_USABLE_DURATION_MS = 500L
    }
}

data class FinishedRecording(
    val file: File,
    val durationMs: Long,
)
