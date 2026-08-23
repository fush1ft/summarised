package io.github.fush1ft.summarised.feature.record

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.fush1ft.summarised.core.audio.RecorderController
import io.github.fush1ft.summarised.core.audio.RecorderState
import io.github.fush1ft.summarised.core.audio.service.RecordingService
import io.github.fush1ft.summarised.core.data.repository.RecordingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One-off events the screen shows and then forgets. */
enum class RecordEvent { SAVED, TOO_SHORT }

@HiltViewModel
class RecordViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recorder: RecorderController,
    private val repository: RecordingRepository,
) : ViewModel() {

    val recorderState: StateFlow<RecorderState> = recorder.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecorderState())

    private val _event = MutableStateFlow<RecordEvent?>(null)
    val event: StateFlow<RecordEvent?> = _event.asStateFlow()

    /**
     * Starts capture, provided the microphone permission is actually held.
     *
     * The screen already gates on the permission before offering the button, but
     * the check is repeated here rather than assumed: a permission can be revoked
     * while the app sits in the background, and the failure mode without this is a
     * SecurityException rather than a message the user can act on.
     *
     * Suppressed because lint does not carry the guard across the module boundary
     * into RecorderController's @RequiresPermission. The check below is real, and
     * RecorderController catches SecurityException on top of it.
     */
    @SuppressLint("MissingPermission")
    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        recorder.start()
        // Only raise the foreground service once capture is actually running. The
        // service stops itself when it observes an idle recorder, so starting it
        // first would have it shut down immediately.
        if (recorder.state.value.isActive) {
            RecordingService.start(context)
        }
    }

    fun pause() = recorder.pause()

    fun resume() = recorder.resume()

    fun stopAndSave() {
        val finished = recorder.stop()
        if (finished == null) {
            _event.value = RecordEvent.TOO_SHORT
            return
        }
        viewModelScope.launch {
            repository.saveRecording(finished.file, finished.durationMs)
            _event.value = RecordEvent.SAVED
        }
    }

    fun discard() = recorder.cancel()

    fun consumeEvent() {
        _event.value = null
    }

    fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
