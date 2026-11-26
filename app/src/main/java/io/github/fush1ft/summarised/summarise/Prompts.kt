package io.github.fush1ft.summarised.summarise

import org.json.JSONObject

/** Templates and prompts; the bookmark prompt is the one rated 5/5 in M0. */
object Prompts {
    enum class Template(val label: String, val extras: String) {
        LECTURE(
            "Přednáška",
            """"pojmy": [{"pojem": "...", "definice": "..."}], "otazky_ke_zkousce": ["..."]""",
        ),
        MEETING(
            "Schůzka",
            """"rozhodnuti": ["..."], "ukoly": [{"kdo": "...", "co": "...", "termin": "..."}], "otevrene_otazky": ["..."]""",
        ),
        FIELD(
            "Terén",
            """"cisla": [{"co": "...", "hodnota": "..."}], "zjisteni": ["..."], "ukoly": ["..."]""",
        ),
        ;

        companion object {
            fun of(name: String?) = entries.firstOrNull { it.name == name } ?: LECTURE
        }
    }

    const val MARKER = "[ZÁLOŽKA]"

    const val SYSTEM =
        "Jsi asistent, který zpracovává strojové přepisy mluveného slova v češtině. " +
            "Přepis může obsahovat chyby rozpoznávání a vycpávková slova; opravuj je jen tam, kde je význam jasný, " +
            "a nic si nevymýšlej. Odpovídáš vždy česky a pouze platným JSON objektem bez dalšího textu."

    private const val BREVITY =
        "Buď stručný: nadpis do 8 slov, shrnutí 2–3 věty, nejvýš 5 bodů a nejvýš 3 položky v každém dalším seznamu. " +
            "Nepoužívej Markdown ani bloky kódu, vrať jen samotný JSON v tomto tvaru:\n"

    fun bookmark(template: Template, transcript: String, atLabel: String): String =
        "Uživatel si během nahrávání (typ: ${template.label.lowercase()}) v čase $atLabel stiskl záložku, " +
            "protože tam zaznělo něco důležitého. Místo stisku je v přepisu označené $MARKER. " +
            "Soustřeď se hlavně na to, co zaznělo zhruba minutu před značkou a minutu po ní. " +
            "Zbytek přepisu ber jen jako kontext.\n\n" +
            "PŘEPIS:\n$transcript\n\n" + BREVITY +
            """{"nadpis": "krátký výstižný nadpis", "shrnuti": "co je u záložky podstatné", "body": ["klíčový bod", "..."], ${template.extras}}"""

    /** Map step for long recordings: plain notes, cheaper than JSON. */
    fun section(template: Template, part: Int, parts: Int, transcript: String): String =
        "Toto je část $part z $parts přepisu nahrávky (typ: ${template.label.lowercase()}). " +
            "Napiš k ní česky 4–8 stručných poznámek v odrážkách: co zaznělo, důležité pojmy, čísla, rozhodnutí a úkoly. " +
            "Odpověz jako JSON {\"poznamky\": [\"...\"]}.\n\nPŘEPIS:\n$transcript"

    /** Reduce step (or the only step for short recordings). */
    fun whole(template: Template, material: String, bookmarks: String, isNotes: Boolean): String =
        "Vytvoř shrnutí celé nahrávky (typ: ${template.label.lowercase()}). " +
            (if (isNotes) "Máš k dispozici poznámky z jednotlivých částí nahrávky.\n\nPOZNÁMKY:\n" else "\n\nPŘEPIS:\n") +
            material +
            (if (bookmarks.isNotBlank()) "\n\nMÍSTA, KTERÁ SI UŽIVATEL OZNAČIL JAKO DŮLEŽITÁ (dej jim větší váhu):\n$bookmarks" else "") +
            "\n\nNadpis do 8 slov, shrnutí 3–5 vět, nejvýš 8 bodů a nejvýš 5 položek v každém dalším seznamu. " +
            "Nepoužívej Markdown ani bloky kódu, vrať jen samotný JSON v tomto tvaru:\n" +
            """{"nadpis": "...", "shrnuti": "...", "body": ["..."], ${template.extras}}"""

    /** Pulls the JSON object out of an answer; closes it if the model was cut off. */
    fun parseJson(answer: String): JSONObject? {
        val cleaned = answer.replace(Regex("(?s)<think>.*?</think>"), "").replace("```json", "").replace("```", "")
        val start = cleaned.indexOf('{')
        if (start < 0) return null
        val end = cleaned.lastIndexOf('}')
        if (end > start) runCatching { return JSONObject(cleaned.substring(start, end + 1)) }
        return repairTruncated(cleaned.substring(start))
    }

    private fun repairTruncated(s: String): JSONObject? {
        val cuts = s.indices.filter { s[it] == '}' || s[it] == ']' || s[it] == '"' }.reversed()
        for (cut in cuts.take(400)) {
            val prefix = s.substring(0, cut + 1).trimEnd().trimEnd(',')
            val stack = ArrayDeque<Char>()
            var inString = false
            var escape = false
            for (c in prefix) {
                when {
                    escape -> escape = false
                    c == '\\' && inString -> escape = true
                    c == '"' -> inString = !inString
                    inString -> Unit
                    c == '{' -> stack.addLast('}')
                    c == '[' -> stack.addLast(']')
                    c == '}' || c == ']' -> if (stack.isNotEmpty()) stack.removeLast()
                }
            }
            if (inString) continue
            runCatching { return JSONObject(prefix + stack.reversed().joinToString("")) }
        }
        return null
    }
}
