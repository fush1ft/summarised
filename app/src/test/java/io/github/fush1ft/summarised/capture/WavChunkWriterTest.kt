package io.github.fush1ft.summarised.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavChunkWriterTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun headerCarriesTheRealSizeAfterClose() {
        val f = tmp.newFile("a.wav")
        val w = WavChunkWriter(f)
        w.write(ShortArray(16_000), 16_000)
        w.close()
        assertThat(f.length()).isEqualTo(44L + 32_000)
        assertThat(dataSize(f)).isEqualTo(32_000)
        assertThat(w.durationMs()).isEqualTo(1000)
    }

    @Test
    fun repairFixesAHeaderLeftAtZeroByACrash() {
        val f = tmp.newFile("b.wav")
        val w = WavChunkWriter(f)
        w.write(ShortArray(8_000), 8_000) // never closed: header still says 0
        assertThat(dataSize(f)).isEqualTo(0)
        assertThat(WavChunkWriter.repairHeader(f)).isEqualTo(500)
        assertThat(dataSize(f)).isEqualTo(16_000)
    }

    private fun dataSize(f: java.io.File): Int = RandomAccessFile(f, "r").use { raf ->
        val b = ByteArray(4)
        raf.seek(40)
        raf.readFully(b)
        ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int
    }
}
