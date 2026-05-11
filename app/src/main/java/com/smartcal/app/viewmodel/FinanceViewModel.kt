package com.smartcal.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcal.app.data.FinanceRepository
import com.smartcal.app.data.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class FinanceUiState(
    val allTransactions: List<Transaction> = emptyList(),
    val yesterdaySummary: PeriodSummary = PeriodSummary("Wczoraj", 0.0, 0.0),
    val lastMonthSummary: PeriodSummary = PeriodSummary("Poprzedni miesiąc", 0.0, 0.0),
    val currentMonthSummary: PeriodSummary = PeriodSummary("Ten miesiąc", 0.0, 0.0),
    val isAddingTransaction: Boolean = false
)

@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val repo: FinanceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FinanceUiState())
    val state: StateFlow<FinanceUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getAllTransactions().collect { all ->
                val today     = LocalDate.now()
                val yesterday = today.minusDays(1)
                val thisMonth = YearMonth.now()
                val lastMonth = thisMonth.minusMonths(1)

                _state.update { it.copy(
                    allTransactions      = all,
                    yesterdaySummary     = summarise("Wczoraj", all, yesterday, yesterday),
                    currentMonthSummary  = summarise("Ten miesiąc", all,
                        thisMonth.atDay(1), thisMonth.atEndOfMonth()),
                    lastMonthSummary     = summarise("Poprzedni miesiąc", all,
                        lastMonth.atDay(1), lastMonth.atEndOfMonth())
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
}
