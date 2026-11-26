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
    fun compute(reference: String, hypothesis: String): Pair<Int, Int> = distance(words(reference), words(hypothesis))

    fun rate(reference: String, hypothesis: String): Double {
        val (e, n) = compute(reference, hypothesis)
        return if (n == 0) 0.0 else e.toDouble() / n
    }

    /**
     * Numbers are read out as words ("sedm až dvanáct") but recognisers write
     * digits ("7 až 12"), which plain WER counts as errors. This variant drops
     * numbers, number words and units from both sides before comparing.
     */
    fun rateWithoutNumbers(reference: String, hypothesis: String): Double {
        val r = words(reference).filterNot(::isNumeric)
        val h = words(hypothesis).filterNot(::isNumeric)
        val (e, n) = distance(r, h)
        return if (n == 0) 0.0 else e.toDouble() / n
    }

    private val numberWords = setOf(
        "nula", "jeden", "jedna", "jedno", "dva", "dvě", "dvou", "tři", "čtyři", "pět", "šest", "sedm", "osm", "devět",
        "deset", "jedenáct", "dvanáct", "třináct", "čtrnáct", "patnáct", "šestnáct", "sedmnáct", "osmnáct",
        "devatenáct", "dvacet", "třicet", "čtyřicet", "padesát", "šedesát", "sedmdesát", "osmdesát", "devadesát",
        "sto", "sta", "set", "tisíc", "tisíce", "milion", "milionů", "půl",
        "první", "prvního", "druhý", "druhého", "třetí", "pátého", "dvanáctého", "patnáctého",
        "procent", "procenta", "korun", "kč", "cm", "centimetrů", "centimetry", "metrů", "metru", "metry", "m",
        "stupňů", "stupně", "stupeň", "c",
    )

    private fun isNumeric(w: String) = w.any { it.isDigit() } || w in numberWords

    private fun distance(r: List<String>, h: List<String>): Pair<Int, Int> {
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
}
