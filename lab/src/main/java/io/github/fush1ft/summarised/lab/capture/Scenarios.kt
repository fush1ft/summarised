package io.github.fush1ft.summarised.lab.capture

import io.github.fush1ft.summarised.lab.log.Results
import org.json.JSONObject

/** The power-button tests, written for someone who has never heard of a "foreground service". */
object Scenarios {
    data class Scenario(val id: String, val title: String, val steps: String, val expect: String)

    val all = listOf(
        Scenario(
            "A", "Odemčený telefon",
            "Obrazovka svítí a telefon je odemčený. Podrž tlačítko zapínání, pak ho podrž ještě třikrát.",
            "První podržení spustí nahrávání (dvě krátká cvaknutí), každé další přidá záložku (jedno ťuknutí).",
        ),
        Scenario(
            "B", "Zamčený telefon",
            "Zamkni telefon, ale nech obrazovku svítit. Zopakuj totéž.",
            "Na zamčené obrazovce se dole ukáže karta a vibrace jsou stejné jako v testu A.",
        ),
        Scenario(
            "C", "Naslepo v kapse",
            "Zhasni obrazovku a dej telefon do kapsy. Spusť nahrávání a přidej tři záložky jen podle vibrací.",
            "Každé podržení ucítíš a v aplikaci pak uvidíš tři záložky.",
        ),
        Scenario(
            "D", "Ovládání z notifikace",
            "Během nahrávání se podívej na zamčenou obrazovku. V notifikaci zkus Záložku a pak Stop.",
            "Obě tlačítka fungují bez odemčení.",
        ),
        Scenario(
            "E", "Dlouhé nahrávání",
            "Spusť nahrávání, zhasni obrazovku a nech telefon aspoň 30 minut ležet. Pak nahrávání zastav.",
            "Nahrávání vydrží a v kartě Stav se neobjeví varování o tichu.",
        ),
    )

    /** Latest verdict per scenario: true = works, false = doesn't. */
    fun verdicts(): Map<String, Boolean> =
        Results.all.value.filter { it.optString("kind") == "scenario" }
            .associate { it.optString("id") to it.optBoolean("ok") }

    fun record(id: String, ok: Boolean, startMode: String) {
        Results.add("scenario", JSONObject().put("id", id).put("ok", ok).put("start_mode", startMode))
    }
}
