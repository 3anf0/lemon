package com.smartcal.app.data

import com.smartcal.app.data.KeywordMatcher.matchesAny
import com.smartcal.app.data.model.*
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared handler for keyword-based voice commands.
 * Used by both VoiceViewModel (in-app) and MarekBubbleService (floating bubble).
 */
@Singleton
class VoiceCommandHandler @Inject constructor(
    private val financeRepository: FinanceRepository,
    private val eventRepository: EventRepository,
    private val reminderManager: EventReminderManager
) {

    // ── Finance ───────────────────────────────────────────────────────────

    /**
     * Detects and saves a finance transaction from speech.
     * Returns a Polish confirmation string, or null if not a finance command.
     */
    suspend fun handleFinance(text: String): String? {
        val s = text.lowercase()
        val isIncome  = s.matchesAny(KeywordMatcher.INCOME_STEMS)
        val isExpense = s.matchesAny(KeywordMatcher.EXPENSE_STEMS)
        if (!isIncome && !isExpense) return null

        // Wyłap "dodaj opis ..." lub samo "opis ..." i oddziel od komendy
        val noteRegex = Regex("""(?:dodaj\s+)?opis\s+(.+)$""")
        val noteMatch = noteRegex.find(s)
        val note = noteMatch?.groupValues?.get(1)?.trim() ?: ""
        // Parsuj kategorię/tytuł/kwotę tylko z części PRZED opisem
        val cmd = if (noteMatch != null) s.substring(0, noteMatch.range.first).trim() else s

        val amount = Regex("""(\d+(?:[.,]\d{1,2})?)""")
            .find(cmd)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
            ?: return null
        if (amount <= 0) return null

        val category = when {
            cmd.matchesAny(KeywordMatcher.FUEL_STEMS)     -> ExpenseCategory.FUEL
            cmd.matchesAny(KeywordMatcher.GAMBLING_STEMS) -> ExpenseCategory.GAMBLING
            cmd.matchesAny(KeywordMatcher.FOOD_STEMS)     -> ExpenseCategory.FOOD
            cmd.matchesAny(KeywordMatcher.OTHER_STEMS)    -> ExpenseCategory.OTHER
            else -> if (isIncome) ExpenseCategory.WORK else ExpenseCategory.OTHER
        }
        val title = KeywordMatcher.findTitle(cmd) ?: if (isIncome) "Przychód" else "Wydatek"
        val type  = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE
        val date  = DateParser.parse(text) ?: LocalDate.now()

        financeRepository.addTransaction(Transaction(
            title    = title,
            amount   = amount,
            type     = type,
            category = category,
            date     = date,
            note     = note
        ))

        val sign     = if (type == TransactionType.INCOME) "+" else "-"
        val typeWord = if (type == TransactionType.INCOME) "Przychód" else "Wydatek"
        val notePart = if (note.isNotBlank()) " · $note" else ""
        return "Zapisano! $typeWord $sign%.2f zł — $title (${category.label})$notePart ✓".format(amount)
    }

    // ── Calendar (keyword-only, no AI — used by bubble service) ──────────

    /**
     * Detects and saves a calendar event from speech using keyword matching only.
     * Returns a Polish confirmation string, or null if not a calendar add command.
     */
    suspend fun handleCalendarKeyword(text: String): String? {
        val s = text.lowercase()
        val isAdd = s.matchesAny(listOf("dodaj","add","wpisz","zaplanuj","umów","zapisz","ustaw","dodajże"))
        if (!isAdd) return null

        val timeRegex = Regex("""(?:o\s+)?(\d{1,2})(?::(\d{2}))?""")
        val timeMatch = timeRegex.find(s) ?: return null
        var hour   = timeMatch.groupValues[1].toIntOrNull() ?: return null
        val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
        if (s.contains("pm") && hour < 12) hour += 12
        if (hour !in 0..23) return null

        val category = when {
            s.matchesAny(KeywordMatcher.HEALTH_STEMS)  -> EventCategory.HEALTH
            s.matchesAny(KeywordMatcher.WORK_STEMS)    -> EventCategory.WORK
            s.matchesAny(KeywordMatcher.SOCIAL_STEMS)  -> EventCategory.SOCIAL
            s.matchesAny(KeywordMatcher.DOCTOR_STEMS)  -> EventCategory.PERSONAL
            else                                       -> EventCategory.PERSONAL
        }

        val eventTitle = KeywordMatcher.findTitle(s) ?: "Wydarzenie"
        val date  = DateParser.parse(text) ?: LocalDate.now()
        val start = date.atTime(hour, minute)

        val event = CalEvent(
            title     = eventTitle,
            startTime = start,
            endTime   = start.plusHours(1),
            category  = category
        )
        val newId = eventRepository.addEvent(event)
        reminderManager.schedule(event.copy(id = newId))

        val dateLabel = DateParser.formatPolish(date)
        return "$eventTitle dodano na $dateLabel o %02d:%02d!".format(hour, minute)
    }
}
