package io.github.fush1ft.summarised.ui

import android.media.MediaPlayer
import io.github.fush1ft.summarised.core.database.AudioChunkEntity
import java.io.File

/**
 * Plays a recording stored as consecutive chunk files as if it were one file:
 * positions are in recording time, and playback rolls over to the next chunk.
 */
class ChunkPlayer {
    private var chunks: List<AudioChunkEntity> = emptyList()
    private var player: MediaPlayer? = null
    private var current = -1
    var isPlaying = false
        private set

    fun setChunks(list: List<AudioChunkEntity>) {
        chunks = list.filter { File(it.path).exists() }
    }

    val totalMs: Long get() = chunks.sumOf { it.durationMs }

    /** Current position in the whole recording. */
    fun positionMs(): Long {
        val c = chunks.getOrNull(current) ?: return 0
        return c.startMs + (runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)).toLong()
    }

    fun playFrom(ms: Long) {
        if (chunks.isEmpty()) return
        val target = ms.coerceIn(0, maxOf(0, totalMs - 1))
        val index = chunks.indexOfLast { it.startMs <= target }.coerceAtLeast(0)
        open(index, target - chunks[index].startMs)
    }

    fun toggle() {
        val p = player
        when {
            p == null -> playFrom(0)
            isPlaying -> {
                p.pause()
                isPlaying = false
            }
            else -> {
                p.start()
                isPlaying = true
            }
        }
    }

    private fun open(index: Int, offsetMs: Long) {
        release()
        val chunk = chunks.getOrNull(index) ?: return
        val p = MediaPlayer()
        try {
            p.setDataSource(chunk.path)
            p.prepare()
            if (offsetMs > 0) p.seekTo(offsetMs, MediaPlayer.SEEK_CLOSEST)
            p.setOnCompletionListener {
                if (index + 1 < chunks.size) open(index + 1, 0) else {
                    isPlaying = false
                }
            }
            p.start()
            player = p
            current = index
            isPlaying = true
        } catch (e: Exception) {
            android.util.Log.w("summarised", "playback failed: ${e.message}")
            p.release()
        }
    }

    fun release() {
        player?.release()
        player = null
        isPlaying = false
    }
}
