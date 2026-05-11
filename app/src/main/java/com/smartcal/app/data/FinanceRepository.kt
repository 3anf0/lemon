package com.smartcal.app.data

import com.smartcal.app.data.local.TransactionDao
import com.smartcal.app.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinanceRepository @Inject constructor(private val dao: TransactionDao) {

    private val zone = ZoneId.systemDefault()

    fun getAllTransactions(): Flow<List<Transaction>> =
        dao.getAllTransactions().map { list -> list.map { it.toDomain() } }

    fun getTransactionsForRange(from: LocalDate, to: LocalDate): Flow<List<Transaction>> {
        val fromEpoch = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val toEpoch   = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.getTransactionsInRange(fromEpoch, toEpoch).map { list -> list.map { it.toDomain() } }
    }

    suspend fun addTransaction(tx: Transaction): Long = dao.insert(tx.toEntity())
    suspend fun deleteTransaction(id: Long) = dao.deleteById(id)

    // ── helpers ──────────────────────────────────────────────────────────

    private fun TransactionEntity.toDomain(): Transaction {
        val date = java.time.Instant.ofEpochMilli(dateEpoch).atZone(zone).toLocalDate()
        return Transaction(
            id       = id,
            title    = title,
            amount   = amount,
            type     = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE),
            category = runCatching { ExpenseCategory.valueOf(category) }.getOrDefault(ExpenseCategory.FOOD),
            date     = date,
            note     = note
        )
    }

    private fun Transaction.toEntity(): TransactionEntity {
        val epoch = date.atStartOfDay(zone).toInstant().toEpochMilli()
        return TransactionEntity(
            id        = id,
            title     = title,
            amount    = amount,
            type      = type.name,
            category  = category.name,
            dateEpoch = epoch,
            note      = note
        )
    }
}
