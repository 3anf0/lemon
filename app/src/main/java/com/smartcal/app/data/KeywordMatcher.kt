package com.smartcal.app.data

/**
 * Robust Polish keyword matcher.
 * Handles all common word endings/inflections so Lemon doesn't get confused
 * when the user changes the suffix of a word.
 *
 * Strategy: strip to a common stem and match that instead of exact strings.
 * e.g. "siłownia", "siłownię", "siłowni", "siłowniach" → stem "siłown"
 */
object KeywordMatcher {

    // ── Income triggers ───────────────────────────────────────────────────
    val INCOME_STEMS = listOf(
        "zarob",       // zarobiłem, zarobił, zarobiłam, zarobiona
        "przychod",    // przychód, przychodu, przychodem
        "przychód",
        "wpłyn",       // wpłynęło, wpływa
        "wplyw",       // wpływ
        "dostał",      // dostałem, dostała
        "dostalem",
        "wyplat",      // wypłata, wypłaty, wypłatę
        "wypłat",
        "premi",       // premia, premię, premii
        "sprzedał",    // sprzedałem, sprzedała
        "sprzedaem",
        "zarobilem",
        "zarobil"
    )

    // ── Expense triggers ──────────────────────────────────────────────────
    val EXPENSE_STEMS = listOf(
        "wydał",       // wydałem, wydała, wydałeś
        "wydaem",
        "wydal",
        "wydatek",     // wydatek, wydatku
        "kupił",       // kupiłem, kupiła, kupiłeś
        "kupilem",
        "kupil",
        "zapłacił",    // zapłaciłem, zapłaciła
        "zaplacil",
        "zaplacilem",
        "kosztował",   // kosztowało, kosztuje
        "kosztow",
        "przegrał",    // przegrałem, przegrała
        "przegraem",
        "przegral",
        "stracił",     // straciłem, straciła
        "stracilem",
        "stracil",
        "tankowal",    // tankowałem, tankowała
        "tankował",
        "tankowaem",
        "spent", "bought", "lost", "paid"
    )

    // ── Category stems ────────────────────────────────────────────────────

    val FUEL_STEMS = listOf(
        "paliw",       // paliwo, paliwa, paliwem
        "tankow",      // tankowanie, tankowałem
        "benzyn",      // benzyna, benzyny, benzynie
        "diesel",
        "lpg",
        "stacj",       // stacja, stacji, stację
        "orlen",
        "lotos",
        "shell",
        "fuel", "petrol", "refuel"
    )

    val GAMBLING_STEMS = listOf(
        "hazard",
        "kasyn",       // kasyno, kasynie, kasynem
        "casino",
        "zakład",      // zakład, zakładzie, zakładem
        "zaklad",
        "bukmach",     // bukmacher, bukmachera
        "lotter",      // lottery, loteria
        "lotto",
        "poker",
        "przegrał",    // also in expense — same stem
        "przegral",
        "ruletk",      // ruletka, ruletki
        "jednorekib",  // jednoręki bandyta
        "slot",
        "bet", "gambl"
    )

    val FOOD_STEMS = listOf(
        "jedzen",      // jedzenie, jedzenia
        "obiad",       // obiad, obiadu, obiadem
        "kolacj",      // kolacja, kolacji, kolację
        "śniadan",     // śniadanie, śniadania
        "sniadan",
        "sklep",       // sklep, sklepu
        "biedronk",    // biedronka, biedronki
        "lidl",
        "kaufland",
        "netto",
        "aldi",
        "restauracj",  // restauracja, restauracji
        "fast food",
        "pizz",        // pizza, pizzy, pizzę
        "burger",
        "kawiarni",    // kawiarnia, kawiarni
        "kaw",         // kawa, kawy, kawę (careful — short stem)
        "obiad",
        "lunch",
        "dinner",
        "grocery", "food", "supermarket",
        "zakup"        // zakupy, zakupów, zakupach
    )

    val OTHER_STEMS = listOf(
        "rachun",      // rachunek, rachunku, rachunkiem
        "czynsz",      // czynsz, czynszu
        "prąd",        // prąd, prądu
        "prad",
        "woda",        // woda, wody
        "internet",
        "subskrypcj",  // subskrypcja, subskrypcji
        "ubezpieczen", // ubezpieczenie, ubezpieczenia
        "podatek",     // podatek, podatku
        "inne",        // inne, inny, innym
        "inny",
        "różne",
        "rozne",
        "misc", "other", "bill", "rent", "utility"
    )

    // ── Event category stems ──────────────────────────────────────────────

    val HEALTH_STEMS = listOf(
        "siłown",      // siłownia, siłownię, siłowni, siłowniach
        "silown",
        "gym",
        "sport",
        "trening",     // trening, treningu, treningiem
        "fitnes",      // fitness
        "bieg",        // bieganie, biegu, biegiem
        "joggin",
        "pływan",      // pływanie
        "rower",       // rower, roweru
        "yoga",
        "pilates",
        "zdrowi"       // zdrowie, zdrowia
    )

    val WORK_STEMS = listOf(
        "spotkani",    // spotkanie, spotkania, spotkaniu
        "meeting",
        "praca",       // praca, pracy, pracę
        "pracy",
        "biuro",       // biuro, biurze, biura
        "konferencj",  // konferencja, konferencji
        "prezentacj",  // prezentacja, prezentacji
        "projekt",     // projekt, projektu
        "klient",      // klient, klienta, kliencie
        "szef",        // szef, szefa, szefem
        "work", "office", "job", "meeting", "conference"
    )

    val SOCIAL_STEMS = listOf(
        "kolacj",
        "obiad",
        "urodzin",     // urodziny, urodzinach
        "imprez",      // impreza, imprezy, imprezę
        "party",
        "friend",
        "przyjaciel",
        "rodzin",      // rodzina, rodziny, rodzinie
        "wyjści",      // wyjście, wyjścia
        "kino",
        "teatr",
        "koncert"
    )

    val DOCTOR_STEMS = listOf(
        "lekarz",      // lekarz, lekarza, lekarzu
        "doktor",      // doktor, doktora
        "dentysta",    // dentysta, dentysty
        "dentyst",
        "doctor",
        "kontroln",    // kontrolna, kontrolne
        "wizyt",       // wizyta, wizyty, wizytę
        "szpital",
        "przychodn",   // przychodnia, przychodni
        "badani"       // badanie, badania
    )

    // ── Title mapping ─────────────────────────────────────────────────────

    val TITLE_MAP = listOf(
        Pair(listOf("kasyn","casino","poker","ruletk","slot"), "Kasyno"),
        Pair(listOf("biedronk"), "Biedronka"),
        Pair(listOf("lidl"), "Lidl"),
        Pair(listOf("kaufland"), "Kaufland"),
        Pair(listOf("netto"), "Netto"),
        Pair(listOf("aldi"), "Aldi"),
        Pair(listOf("orlen"), "Orlen"),
        Pair(listOf("shell"), "Shell"),
        Pair(listOf("tankow","paliw","benzyn"), "Tankowanie"),
        Pair(listOf("obiad","lunch"), "Obiad"),
        Pair(listOf("kolacj","dinner"), "Kolacja"),
        Pair(listOf("śniadan","sniadan","breakfast"), "Śniadanie"),
        Pair(listOf("wyplat","wypłat"), "Wypłata"),
        Pair(listOf("premi"), "Premia"),
        Pair(listOf("zakup","sklep","grocery"), "Zakupy"),
        Pair(listOf("rachun"), "Rachunek"),
        Pair(listOf("czynsz"), "Czynsz"),
        Pair(listOf("internet"), "Internet"),
        Pair(listOf("subskrypcj"), "Subskrypcja"),
        Pair(listOf("siłown","silown","gym"), "Siłownia"),
        Pair(listOf("spotkani","meeting"), "Spotkanie"),
        Pair(listOf("trening","training"), "Trening"),
        Pair(listOf("lekarz","doktor","doctor"), "Lekarz"),
        Pair(listOf("dentyst"), "Dentysta"),
        Pair(listOf("praca","work","biuro","office"), "Praca"),
    )

    // ── Core match function ───────────────────────────────────────────────

    /** Returns true if the input string contains any of the given stems */
    fun String.matchesAny(stems: List<String>): Boolean =
        stems.any { stem -> this.contains(stem, ignoreCase = true) }

    /** Find best title from speech */
    fun findTitle(s: String): String? {
        for ((stems, title) in TITLE_MAP) {
            if (s.matchesAny(stems)) return title
        }
        return null
    }
}
