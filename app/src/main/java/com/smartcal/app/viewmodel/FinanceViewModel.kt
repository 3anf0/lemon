package com.smartcal.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcal.app.data.FinanceRepository
import com.smartcal.app.data.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

private const val RECENT_DAYS = 90L

private val MONTHS_PL = arrayOf(
    "sty", "lut", "mar", "kwi", "maj", "cze",
    "lip", "sie", "wrz", "paź", "lis", "gru"
)

private fun buildWeekLabel(monday: LocalDate, sunday: LocalDate, isCurrent: Boolean): String {
    val range = if (monday.month == sunday.month)
        "${monday.dayOfMonth}–${sunday.dayOfMonth} ${MONTHS_PL[sunday.monthValue - 1]}"
    else
        "${monday.dayOfMonth} ${MONTHS_PL[monday.monthValue - 1]} – ${sunday.dayOfMonth} ${MONTHS_PL[sunday.monthValue - 1]}"
    return if (isCurrent) "Ten tydzień · $range" else range
}

data class WeekGroup(
    val label: String,
    val weekStart: LocalDate,
    val transactions: List<Transaction>,
    val weekTotal: Double
)

data class FinanceUiState(
    val allTransactions: List<Transaction> = emptyList(),
    val yesterdaySummary: PeriodSummary = PeriodSummary("Wczoraj", 0.0, 0.0),
    val thisWeekSummary: PeriodSummary = PeriodSummary("Ten tydzień", 0.0, 0.0),
    val currentMonthSummary: PeriodSummary = PeriodSummary("Ten miesiąc", 0.0, 0.0),
    val isAddingTransaction: Boolean = false,
    val showFullHistory: Boolean = false
) {
    val displayedTransactions: List<Transaction>
        get() = if (showFullHistory) allTransactions
                else allTransactions.filter {
                    !it.date.isBefore(LocalDate.now().minusDays(RECENT_DAYS))
                }
    val hasOlderTransactions: Boolean
        get() = allTransactions.size > displayedTransactions.size
    val weekGroups: List<WeekGroup>
        get() {
            val today = LocalDate.now()
            return displayedTransactions
                .groupBy { it.date.with(DayOfWeek.MONDAY) }
                .entries
                .sortedByDescending { it.key }
                .map { (monday, txs) ->
                    val sunday    = monday.plusDays(6)
                    val isCurrent = !today.isBefore(monday) && !today.isAfter(sunday)
                    WeekGroup(
                        label        = buildWeekLabel(monday, sunday, isCurrent),
                        weekStart    = monday,
                        transactions = txs.sortedByDescending { it.date },
                        weekTotal    = txs.sumOf { it.signedAmount }
                    )
                }
        }
}

@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val repo: FinanceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FinanceUiState())
    val state: StateFlow<FinanceUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getAllTransactions().collect { all ->
                val today         = LocalDate.now()
                val yesterday     = today.minusDays(1)
                val thisMonth     = YearMonth.now()
                val thisWeekStart = today.with(DayOfWeek.MONDAY)
                val thisWeekEnd   = thisWeekStart.plusDays(6)

                _state.update { it.copy(
                    allTransactions     = all,
                    yesterdaySummary    = summarise("Wczoraj", all, yesterday, yesterday),
                    currentMonthSummary = summarise("Ten miesiąc", all,
                        thisMonth.atDay(1), thisMonth.atEndOfMonth()),
                    thisWeekSummary     = summarise("Ten tydzień", all, thisWeekStart, thisWeekEnd)
                )}
            }
        }
    }

    private fun summarise(
        label: String,
        all: List<Transaction>,
        from: LocalDate,
        to: LocalDate
    ): PeriodSummary {
        val filtered = all.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        val income   = filtered.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expenses = filtered.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val byCategory = ExpenseCategory.entries.associateWith { cat ->
            filtered.filter { it.category == cat }.sumOf { it.signedAmount }
        }
        return PeriodSummary(label, income, expenses, income - expenses, byCategory)
    }

    fun addTransaction(
        title: String,
        amount: Double,
        type: TransactionType,
        category: ExpenseCategory,
        date: LocalDate = LocalDate.now(),
        note: String = ""
    ) = viewModelScope.launch {
        repo.addTransaction(Transaction(
            title    = title,
            amount   = amount,
            type     = type,
            category = category,
            date     = date,
            note     = note
        ))
    }

    fun deleteTransaction(id: Long) = viewModelScope.launch { repo.deleteTransaction(id) }

    fun setAdding(show: Boolean) = _state.update { it.copy(isAddingTransaction = show) }

    fun toggleFullHistory() = _state.update { it.copy(showFullHistory = !it.showFullHistory) }
}
