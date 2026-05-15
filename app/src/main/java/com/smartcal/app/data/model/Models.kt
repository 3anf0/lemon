package com.smartcal.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// ── Calendar models ───────────────────────────────────────────────────────

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firestoreId: String = "",
    val title: String,
    val startEpoch: Long,
    val endEpoch: Long,
    val category: String = "PERSONAL",
    val isAiSuggested: Boolean = false,
    val notes: String = ""
)

data class CalEvent(
    val id: Long = 0,
    val title: String,
    val startTime: java.time.LocalDateTime,
    val endTime: java.time.LocalDateTime,
    val category: EventCategory = EventCategory.PERSONAL,
    val isAiSuggested: Boolean = false,
    val notes: String = ""
)

enum class EventCategory(val label: String, val emoji: String) {
    WORK("Praca", "💼"),
    HEALTH("Zdrowie", "💪"),
    PERSONAL("Osobiste", "🏠"),
    HABIT("Nawyk", "🔄"),
    SOCIAL("Towarzyskie", "👥")
}

// ── Finance models ────────────────────────────────────────────────────────

enum class TransactionType { INCOME, EXPENSE }

enum class ExpenseCategory(val label: String, val emoji: String) {
    WORK("Praca / Przychód", "💰"),
    FOOD("Jedzenie", "🍔"),
    FUEL("Paliwo", "⛽"),
    GAMBLING("Hazard", "🎰"),
    OTHER("Inne", "📦")
}

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firestoreId: String = "",
    val title: String,
    val amount: Double,             // always positive; type determines +/-
    val type: String,               // "INCOME" or "EXPENSE"
    val category: String,           // ExpenseCategory name
    val dateEpoch: Long,            // day epoch (midnight)
    val note: String = ""
)

data class Transaction(
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val type: TransactionType,
    val category: ExpenseCategory,
    val date: java.time.LocalDate,
    val note: String = ""
) {
    val signedAmount: Double get() = if (type == TransactionType.INCOME) amount else -amount
}

data class PeriodSummary(
    val label: String,
    val income: Double,
    val expenses: Double,
    val balance: Double = income - expenses,
    val byCategory: Map<ExpenseCategory, Double> = emptyMap()
)

// ── AI models ─────────────────────────────────────────────────────────────

@Serializable
data class VoiceCommandResult(
    val intent: String,
    val eventTitle: String? = null,
    val eventTime: String? = null,
    val eventDate: String? = null,
    val eventCategory: String? = null,
    val targetEventTitle: String? = null,
    val confirmation: String,
    val needsClarification: Boolean = false,
    val clarificationQuestion: String? = null,
    val aiResponse: String? = null
)
