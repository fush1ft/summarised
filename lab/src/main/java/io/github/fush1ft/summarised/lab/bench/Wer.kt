package io.github.fush1ft.summarised.lab.bench

import java.text.Normalizer

/** Word error rate, after case/punctuation normalisation. Diacritics are kept: they matter in Czech. */
object Wer {
    fun words(text: String): List<String> =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFC)
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

    /** Returns (errors, referenceWords). */
    fun compute(reference: String, hypothesis: String): Pair<Int, Int> {
        val r = words(reference)
        val h = words(hypothesis)
        val d = Array(r.size + 1) { IntArray(h.size + 1) }
        for (i in 0..r.size) d[i][0] = i
        for (j in 0..h.size) d[0][j] = j
        for (i in 1..r.size) {
            for (j in 1..h.size) {
                val cost = if (r[i - 1] == h[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            }
        }
        return d[r.size][h.size] to r.size
    }

    fun rate(reference: String, hypothesis: String): Double {
        val (e, n) = compute(reference, hypothesis)
        return if (n == 0) 0.0 else e.toDouble() / n
    }
}
