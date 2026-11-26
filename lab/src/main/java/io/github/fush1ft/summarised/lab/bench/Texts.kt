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
        v testech. no a teď pozor, tohle bude určitě u zkoušky. v calvinově cyklu, který probíhá ve stromatu
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

    fun bookmarkPrompt(template: Template, transcript: String, atLabel: String): String =
        "Uživatel si během nahrávání (typ: ${template.label.lowercase()}) označil v čase $atLabel záložku, " +
            "protože tam zaznělo něco důležitého. Níže je přepis zhruba dvou minut před záložkou a dvou minut po ní.\n\n" +
            "PŘEPIS:\n$transcript\n\n" +
            "Vrať JSON v tomto tvaru:\n" +
            """{"nadpis": "krátký výstižný nadpis", "shrnuti": "2–3 věty, co je u záložky podstatné", "body": ["klíčový bod", "..."], ${template.extras}}"""

    /** Pulls the JSON object out of a model answer (code fences, thinking blocks, chatter). */
    fun extractJson(answer: String): JSONObject? {
        val cleaned = answer.replace(Regex("(?s)<think>.*?</think>"), "")
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(cleaned.substring(start, end + 1)) }.getOrNull()
    }
}
