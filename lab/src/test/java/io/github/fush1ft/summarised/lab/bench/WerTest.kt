package io.github.fush1ft.summarised.lab.bench

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WerTest {
    @Test
    fun identicalTextIsZero() {
        assertThat(Wer.rate("Dobrý den, vítejte!", "dobrý den vítejte")).isEqualTo(0.0)
    }

    @Test
    fun diacriticsCount() {
        assertThat(Wer.compute("byl tam pes", "byl tam pés")).isEqualTo(1 to 3)
    }

    @Test
    fun insertionsAndDeletions() {
        assertThat(Wer.compute("a b c d", "a c d e")).isEqualTo(2 to 4)
    }
}
