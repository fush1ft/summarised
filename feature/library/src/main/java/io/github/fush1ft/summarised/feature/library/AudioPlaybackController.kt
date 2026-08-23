package io.github.fush1ft.summarised.feature.library

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays back saved recordings, one at a time.
 *
 * Deliberately minimal: this is preview playback inside the library list, not a
 * media session. Starting a second recording stops the first, which is what a list
 * of play buttons should do.
 */
@Singleton
class AudioPlaybackController @Inject constructor() {

    private var player: MediaPlayer? = null

    private val _playingId = MutableStateFlow<Long?>(null)
    val playingId: StateFlow<Long?> = _playingId.asStateFlow()

    fun toggle(recordingId: Long, filePath: String) {
        if (_playingId.value == recordingId) {
            stop()
            return
        }
        stop()
        player = MediaPlayer().apply {
            setOnCompletionListener { stop() }
            setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            runCatching {
                setDataSource(filePath)
                prepare()
                start()
            }.onFailure {
                stop()
                return
            }
        }
        _playingId.value = recordingId
    }

    fun stop() {
        player?.let { active ->
            runCatching { active.stop() }
            active.release()
        }
        player = null
        _playingId.value = null
    }
}
