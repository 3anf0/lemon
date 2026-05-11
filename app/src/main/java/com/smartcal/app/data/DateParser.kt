package com.smartcal.app.data

import java.time.LocalDate
import java.time.Month

/**
 * Parses natural Polish (and English) date expressions into LocalDate.
 * Examples:
 *   "w poniedziałek"   → next Monday
 *   "w niedzielę"      → next Sunday
 *   "10 maja"          → May 10 (this year or next if past)
 *   "jutro"            → tomorrow
 *   "pojutrze"         → day after tomorrow
 *   "w przyszłym tygodniu w środę" → next Wednesday
 */
object DateParser {

    fun parse(text: String): LocalDate? {
        val s = text.lowercase().trim()
        val today = LocalDate.now()

        // ── Relative days ──────────────────────────────────────────────
        if (s.containsAny("dzisiaj", "dziś", "today", "tego dnia"))
            return today
        if (s.containsAny("jutro", "tomorrow"))
            return today.plusDays(1)
        if (s.containsAny("pojutrze", "day after tomorrow"))
            return today.plusDays(2)
        if (s.containsAny("wczoraj", "yesterday"))
            return today.minusDays(1)

        // ── Day of week ───────────────────────────────────────────────
        val nextWeek = s.containsAny("przyszły", "przyszłym", "następny", "następnym",
            "next week", "przyszłą", "następną")

        val dayOfWeekMap = mapOf(
            1 to listOf("poniedziałek","poniedziałku","poniedziałkowi",
                        "poniedzialek","poniedzialku","monday","mon","pon"),
            2 to listOf("wtorek","wtorku","wtorkowi","tuesday","tue","wt"),
            3 to listOf("środa","środę","środy","środzie",
                        "sroda","srode","srody","wednesday","wed","śr","sr"),
            4 to listOf("czwartek","czwartku","czwartkowi","thursday","thu","czw"),
            5 to listOf("piątek","piątku","piątkowi","piatek","piatku","friday","fri","pt"),
            6 to listOf("sobota","sobocie","sobotę","soboty",
                        "sobote","saturday","sat","sob"),
            7 to listOf("niedziela","niedzieli","niedzielę","niedziele",
                        "sunday","sun","nd","niedz")
        )

        for ((dayNum, aliases) in dayOfWeekMap) {
            if (aliases.any { s.contains(it) }) {
                val current = today.dayOfWeek.value   // 1=Mon … 7=Sun
                var daysAhead = dayNum - current
                if (daysAhead <= 0) daysAhead += 7   // always next occurrence
                if (nextWeek && daysAhead < 7) daysAhead += 7
                return today.plusDays(daysAhead.toLong())
            }
        }

        // ── Specific date: "10 maja", "3 marca", "15 listopada" ───────
        val monthMap = mapOf(
            1  to listOf("stycznia","styczeń","styczen","january","jan","sty"),
            2  to listOf("lutego","luty","february","feb","lut"),
            3  to listOf("marca","marzec","march","mar"),
            4  to listOf("kwietnia","kwiecień","kwiecien","april","apr","kwi"),
            5  to listOf("maja","maj","may","maj"),
            6  to listOf("czerwca","czerwiec","june","jun","cze"),
            7  to listOf("lipca","lipiec","july","jul","lip"),
            8  to listOf("sierpnia","sierpień","sierpien","august","aug","sie"),
            9  to listOf("września","wrzesień","wrzesien","september","sep","wrz"),
            10 to listOf("października","październik","pazdziernik","october","oct","paź","paz"),
            11 to listOf("listopada","listopad","november","nov","lis"),
            12 to listOf("grudnia","grudzień","grudzien","december","dec","gru")
        )

        // Find month in text
        var foundMonth: Int? = null
        for ((monthNum, aliases) in monthMap) {
            if (aliases.any { s.contains(it) }) {
                foundMonth = monthNum
                break
            }
        }

        if (foundMonth != null) {
            // Extract day number — first 1-2 digit number in the string
            val dayRegex = Regex("""(\d{1,2})""")
            val dayNum = dayRegex.find(s)?.groupValues?.get(1)?.toIntOrNull()
            if (dayNum != null && dayNum in 1..31) {
                return try {
                    var date = LocalDate.of(today.year, foundMonth, dayNum)
                    // If this date is already past, assume next year
                    if (date.isBefore(today)) date = date.plusYears(1)
                    date
                } catch (e: Exception) { null }
            }
        }

        // ── Numeric date: "10.05", "10/05" ────────────────────────────
        val numericDate = Regex("""(\d{1,2})[./](\d{1,2})(?:[./](\d{2,4}))?""").find(s)
        if (numericDate != null) {
            val day   = numericDate.groupValues[1].toIntOrNull() ?: return null
            val month = numericDate.groupValues[2].toIntOrNull() ?: return null
            val year  = numericDate.groupValues[3].let {
                if (it.isBlank()) today.year
                else if (it.length == 2) 2000 + it.toInt()
                else it.toIntOrNull() ?: today.year
            }
            return try {
                var date = LocalDate.of(year, month, day)
                if (date.isBefore(today) && numericDate.groupValues[3].isBlank())
                    date = date.plusYears(1)
                date
            } catch (e: Exception) { null }
        }

        return null   // couldn't parse
    }

    fun formatPolish(date: LocalDate): String {
        val today    = LocalDate.now()
        val tomorrow = today.plusDays(1)
        return when (date) {
            today    -> "dziś"
            tomorrow -> "jutro"
            else     -> {
                val day   = date.dayOfMonth
                val month = monthGenitive(date.month)
                val dow   = dayOfWeekPolish(date)
                "$dow, $day $month"
            }
        }
    }

    private fun monthGenitive(m: Month) = when (m) {
        Month.JANUARY   -> "stycznia"
        Month.FEBRUARY  -> "lutego"
        Month.MARCH     -> "marca"
        Month.APRIL     -> "kwietnia"
        Month.MAY       -> "maja"
        Month.JUNE      -> "czerwca"
        Month.JULY      -> "lipca"
        Month.AUGUST    -> "sierpnia"
        Month.SEPTEMBER -> "września"
        Month.OCTOBER   -> "października"
        Month.NOVEMBER  -> "listopada"
        Month.DECEMBER  -> "grudnia"
    }

    private fun dayOfWeekPolish(date: LocalDate) = when (date.dayOfWeek.value) {
        1 -> "poniedziałek"
        2 -> "wtorek"
        3 -> "środa"
        4 -> "czwartek"
        5 -> "piątek"
        6 -> "sobota"
        7 -> "niedziela"
        else -> ""
    }

    private fun String.containsAny(vararg words: String) = words.any { this.contains(it) }
}
