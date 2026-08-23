package io.github.fush1ft.summarised.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.fush1ft.summarised.core.data.model.Recording
import io.github.fush1ft.summarised.core.data.repository.RecordingRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val recordings: List<Recording> = emptyList(),
    val playingId: Long? = null,
    val loading: Boolean = true,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: RecordingRepository,
    private val playback: AudioPlaybackController,
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> =
        combine(repository.observeRecordings(), playback.playingId) { recordings, playingId ->
            LibraryUiState(recordings = recordings, playingId = playingId, loading = false)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun togglePlayback(recording: Recording) =
        playback.toggle(recording.id, recording.filePath)

    fun rename(id: Long, title: String) {
        viewModelScope.launch { repository.rename(id, title) }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            // Stop first: deleting the file out from under a running MediaPlayer
            // leaves it playing from a deleted descriptor.
            if (uiState.value.playingId == id) playback.stop()
            repository.delete(id)
        }
    }

    override fun onCleared() {
        playback.stop()
        super.onCleared()
    }
}
