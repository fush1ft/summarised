package io.github.fush1ft.summarised.lab.bench

import org.json.JSONObject

/** Czech material for the benchmarks: passages to read aloud, and transcripts to summarise. */
object Texts {

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
    }

    data class Passage(val id: String, val title: String, val template: Template, val text: String)

    /** Read these aloud at a normal pace; each takes roughly a minute. */
    val passages = listOf(
        Passage(
            "lecture", "Přednáška: derivace", Template.LECTURE,
            "Dnes se podíváme na derivace funkcí jedné proměnné. Derivace v bodě nám říká, jak rychle se " +
                "funkce v tomto bodě mění. Geometricky je to směrnice tečny ke grafu funkce. Pokud je derivace " +
                "kladná, funkce roste, a pokud je záporná, funkce klesá. Mezi základní pravidla patří derivace " +
                "součtu, součinu a podílu. Derivace součinu dvou funkcí se rovná derivaci první funkce krát druhá " +
                "funkce plus první funkce krát derivace druhé funkce. U složené funkce použijeme řetízkové " +
                "pravidlo: derivujeme vnější funkci a výsledek vynásobíme derivací vnitřní funkce. Na příští " +
                "cvičení si připravte příklady sedm až dvanáct ze sbírky a zopakujte si limity, protože bez nich " +
                "derivaci pořádně nepochopíte.",
        ),
        Passage(
            "meeting", "Schůzka: nový e-shop", Template.MEETING,
            "Tak jo, pojďme začít. Prvním bodem je spuštění nového e-shopu. Petra potvrdila, že grafika bude " +
                "hotová do patnáctého března, ale vývoj platební brány se zpozdí asi o týden. Martin navrhuje, " +
                "abychom spuštění posunuli na prvního dubna, a všichni s tím souhlasí. Druhý bod je rozpočet na " +
                "marketing. Máme k dispozici sto dvacet tisíc korun, z toho polovinu dáme do reklamy na " +
                "sociálních sítích. Jana připraví přehled kampaní do příští středy. Zůstává otevřená otázka, " +
                "jestli budeme spolupracovat s externí agenturou, nebo si to uděláme sami. To probereme na další " +
                "schůzce. Úkoly tedy jsou: Martin zkontroluje smlouvu s dodavatelem, Petra pošle finální grafiku " +
                "a Jana připraví plán kampaní.",
        ),
        Passage(
            "field", "Terén: kontrola stavby", Template.FIELD,
            "Jsem na stavbě rodinného domu v Kunraticích, je pondělí, devět hodin ráno. Kontroluji základovou " +
                "desku. Rozměry odpovídají projektu, deska má jedenáct metrů na osm a půl metru. Na severní " +
                "straně je ale prasklina dlouhá zhruba čtyřicet centimetrů, potřebuju ji vyfotit a ukázat " +
                "statikovi. Vlhkost betonu je dvanáct procent, to je ještě moc na pokládku podlahy. " +
                "Hydroizolace je položená správně, přesahy jsou minimálně patnáct centimetrů. U východního rohu " +
                "chybí dvě kotvy pro obvodovou zeď. Úkol pro stavbyvedoucího: doplnit kotvy do středy a znovu " +
                "změřit vlhkost za týden. Teplota vzduchu je osm stupňů, takže betonovat se dnes nebude.",
        ),
    )

    /**
     * About four minutes of a lecture as a speech recogniser would write it:
     * no paragraphs, fillers, a few recognition slips. The bookmark sits in the
     * middle, where the lecturer says what will be in the exam.
     */
    val lectureTranscript = """
        takže dobrý den, dneska navážeme na minulou hodinu, kdy jsme se bavili o buňce a o organelách, a dneska
        se podíváme na fotosyntézu. fotosyntéza je vlastně proces, při kterém rostliny, řasy a některé bakterie
        přeměňují světelnou energii na energii chemickou. probíhá v chloroplastech, konkrétně v tylakoidech a ve
        stromatu. ehm, celou fotosyntézu můžeme rozdělit na dvě fáze, na světelnou fázi a na temnostní fázi, které
        se dneska spíš říká sekundární fáze nebo calvinův cyklus. ve světelné fázi se na membráně tylakoidů
        zachytí světlo pomocí chlorofylu, dochází k fotolýze vody, to znamená, že se voda rozkládá a uvolňuje se
        kyslík, a vznikají dvě důležité látky, ATP a NADPH. tyhle dvě látky jsou takové baterky, které se pak
        využijí v druhé fázi. takže kyslík, který dýcháme, pochází z vody, ne z oxidu uhličitého, to je častá chyba
        v testech. [ZÁLOŽKA] no a teď pozor, tohle bude určitě u zkoušky. v calvinově cyklu, který probíhá ve stromatu
        chloroplastu, se váže oxid uhličitý na ribulóza bisfosfát, a to pomocí enzymu rubisko. rubisko je
        mimochodem nejrozšířenější bílkovina na zemi. z tohohle vzniká nestabilní meziprodukt, který se rozpadne
        na dvě molekuly kyseliny fosfoglycerové, a ty se pak za spotřeby ATP a NADPH redukují na glyceraldehyd
        tři fosfát. a z něj si rostlina dál vyrábí glukózu, škrob a další látky. celková rovnice je šest CO2 plus
        šest H2O, za přítomnosti světla, dává jednu glukózu a šest O2. tu rovnici chci, abyste uměli napsat
        z hlavy, i s tím, odkud pochází kyslík. ehm, ještě jedna věc, existují rostliny C4 a CAM, třeba kukuřice
        nebo kaktusy, které mají ten mechanismus upravený, aby šetřily vodou a aby rubisko pracovalo efektivněji
        v horku. to si projdeme podrobně příště. na příští týden si prosím přečtěte kapitolu šest v učebnici a
        zkuste si nakreslit schéma chloroplastu s popisem, kde probíhá která fáze. jo a připomínám, že test
        z buňky bude dvanáctého, takže to nenechávejte na poslední chvíli.
    """.trimIndent().replace("\n", " ")

    const val LECTURE_BOOKMARK = "2:00"

    const val SYSTEM_PROMPT =
        "Jsi asistent, který zpracovává strojové přepisy mluveného slova v češtině. " +
            "Přepis může obsahovat chyby rozpoznávání a vycpávková slova; opravuj je jen tam, kde je význam jasný, " +
            "a nic si nevymýšlej. Odpovídáš vždy česky a pouze platným JSON objektem bez dalšího textu."

    const val MARKER = "[ZÁLOŽKA]"

    /** Puts the bookmark marker at the sentence boundary nearest the middle (for texts without one). */
    fun withMarker(text: String): String {
        if (text.contains(MARKER)) return text
        val mid = text.length / 2
        val boundaries = Regex("[.!?] ").findAll(text).map { it.range.last }.toList()
        val at = boundaries.minByOrNull { kotlin.math.abs(it - mid) } ?: return "$MARKER $text"
        return text.substring(0, at + 1) + "$MARKER " + text.substring(at + 1)
    }

    fun bookmarkPrompt(template: Template, transcript: String, atLabel: String): String =
        "Uživatel si během nahrávání (typ: ${template.label.lowercase()}) v čase $atLabel stiskl záložku, " +
            "protože tam zaznělo něco důležitého. Místo stisku je v přepisu označené $MARKER. " +
            "Soustřeď se hlavně na to, co zaznělo zhruba minutu před značkou a minutu po ní. " +
            "Zbytek přepisu ber jen jako kontext.\n\n" +
            "PŘEPIS:\n${withMarker(transcript)}\n\n" +
            "Buď stručný: nadpis do 8 slov, shrnutí 2–3 věty, nejvýš 5 bodů a nejvýš 3 položky v každém dalším seznamu. " +
            "Nepoužívej Markdown ani bloky kódu, vrať jen samotný JSON v tomto tvaru:\n" +
            """{"nadpis": "krátký výstižný nadpis", "shrnuti": "co je u záložky podstatné", "body": ["klíčový bod", "..."], ${template.extras}}"""

    /** Pulls the JSON object out of a model answer (code fences, thinking blocks, chatter). */
    fun extractJson(answer: String): JSONObject? = parseJson(answer)?.first

    /** Like [extractJson]; the flag says whether a cut-off answer had to be closed to parse. */
    fun parseJson(answer: String): Pair<JSONObject, Boolean>? {
        val cleaned = answer.replace(Regex("(?s)<think>.*?</think>"), "").replace("```json", "").replace("```", "")
        val start = cleaned.indexOf('{')
        if (start < 0) return null
        val end = cleaned.lastIndexOf('}')
        if (end > start) runCatching { return JSONObject(cleaned.substring(start, end + 1)) to false }
        return repairTruncated(cleaned.substring(start))?.let { it to true }
    }

    /**
     * Closes a JSON object that was cut off by the token limit: tries ever shorter
     * prefixes that end on a complete value and appends the missing brackets.
     */
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
            val closed = prefix + stack.reversed().joinToString("")
            runCatching { return JSONObject(closed) }
        }
        return null
    }
}
