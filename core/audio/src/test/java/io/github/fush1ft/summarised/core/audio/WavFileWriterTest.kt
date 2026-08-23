package io.github.fush1ft.summarised.core.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavFileWriterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun File.readHeader(): ByteBuffer =
        ByteBuffer.wrap(readBytes(), 0, WavFileWriter.HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)

    private fun File.ascii(offset: Int, length: Int): String =
        String(readBytes(), offset, length, Charsets.US_ASCII)

    @Test
    fun `writes a well formed RIFF WAVE header`() {
        val file = tempFolder.newFile("out.wav")
        WavFileWriter(file).use { it.write(ByteArray(100), 100) }

        assertThat(file.ascii(0, 4)).isEqualTo("RIFF")
        assertThat(file.ascii(8, 4)).isEqualTo("WAVE")
        assertThat(file.ascii(12, 4)).isEqualTo("fmt ")
        assertThat(file.ascii(36, 4)).isEqualTo("data")
    }

    @Test
    fun `header declares the format the ASR models expect`() {
        val file = tempFolder.newFile("format.wav")
        WavFileWriter(file).use { it.write(ByteArray(32), 32) }

        val header = file.readHeader()
        assertThat(header.getShort(20)).isEqualTo(1.toShort())      // PCM
        assertThat(header.getShort(22)).isEqualTo(1.toShort())      // mono
        assertThat(header.getInt(24)).isEqualTo(16_000)             // sample rate
        assertThat(header.getInt(28)).isEqualTo(32_000)             // byte rate
        assertThat(header.getShort(32)).isEqualTo(2.toShort())      // block align
        assertThat(header.getShort(34)).isEqualTo(16.toShort())     // bits per sample
    }

    @Test
    fun `patches both length fields on close`() {
        val file = tempFolder.newFile("sized.wav")
        val payload = 1_024

        WavFileWriter(file).use { writer ->
            writer.write(ByteArray(payload), payload)
            // Sizes are still zero until close, which is the whole point of the design.
            assertThat(file.readHeader().getInt(40)).isEqualTo(0)
        }

        val header = file.readHeader()
        assertThat(header.getInt(40)).isEqualTo(payload)                        // data size
        assertThat(header.getInt(4)).isEqualTo(WavFileWriter.HEADER_SIZE - 8 + payload)
        assertThat(file.length()).isEqualTo((WavFileWriter.HEADER_SIZE + payload).toLong())
    }

    @Test
    fun `writes only the requested number of bytes from the buffer`() {
        val file = tempFolder.newFile("partial.wav")
        val buffer = ByteArray(512) { 7 }

        // Mirrors AudioRecord.read returning fewer bytes than the buffer holds.
        WavFileWriter(file).use { it.write(buffer, 100) }

        assertThat(file.length()).isEqualTo((WavFileWriter.HEADER_SIZE + 100).toLong())
        assertThat(file.readHeader().getInt(40)).isEqualTo(100)
    }

    @Test
    fun `tracks bytes written across multiple chunks`() {
        val file = tempFolder.newFile("chunks.wav")
        WavFileWriter(file).use { writer ->
            repeat(5) { writer.write(ByteArray(200), 200) }
            assertThat(writer.bytesWritten).isEqualTo(1_000)
        }
    }

    @Test
    fun `close is idempotent`() {
        val file = tempFolder.newFile("twice.wav")
        val writer = WavFileWriter(file)
        writer.write(ByteArray(64), 64)
        writer.close()
        writer.close()

        assertThat(file.readHeader().getInt(40)).isEqualTo(64)
    }

    @Test
    fun `repairHeader recovers a recording whose writer never closed`() {
        val file = tempFolder.newFile("crashed.wav")
        val payload = 800

        // Simulate the process dying mid-recording: audio on disk, header still zeroed.
        val writer = WavFileWriter(file)
        writer.write(ByteArray(payload), payload)
        // Deliberately not closed.

        assertThat(file.readHeader().getInt(40)).isEqualTo(0)

        WavFileWriter.repairHeader(file)

        assertThat(file.readHeader().getInt(40)).isEqualTo(payload)
        assertThat(file.readHeader().getInt(4)).isEqualTo(WavFileWriter.HEADER_SIZE - 8 + payload)
    }

    @Test
    fun `repairHeader leaves a header-only file alone`() {
        val file = tempFolder.newFile("empty.wav")
        WavFileWriter(file).close()

        WavFileWriter.repairHeader(file)

        assertThat(file.length()).isEqualTo(WavFileWriter.HEADER_SIZE.toLong())
        assertThat(file.readHeader().getInt(40)).isEqualTo(0)
    }
}
