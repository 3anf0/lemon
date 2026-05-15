package com.smartcal.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smartcal.app.data.local.TransactionDao
import com.smartcal.app.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinanceRepository @Inject constructor(
    private val dao: TransactionDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    private val zone = ZoneId.systemDefault()
    private val uid get() = auth.currentUser?.uid

    fun getAllTransactions(): Flow<List<Transaction>> =
        dao.getAllTransactions().map { list -> list.map { it.toDomain() } }

    fun getTransactionsForRange(from: LocalDate, to: LocalDate): Flow<List<Transaction>> {
        val fromEpoch = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val toEpoch   = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.getTransactionsInRange(fromEpoch, toEpoch).map { list -> list.map { it.toDomain() } }
    }

    suspend fun addTransaction(tx: Transaction): Long {
        val entity = tx.toEntity()
        val localId = dao.insert(entity)
        uid?.let { userId ->
            val doc = firestore.collection("users/$userId/transactions").document()
            val data = mapOf(
                "title"     to entity.title,
                "amount"    to entity.amount,
                "type"      to entity.type,
                "category"  to entity.category,
                "dateEpoch" to entity.dateEpoch,
                "note"      to entity.note,
                "localId"   to localId
            )
            runCatching { doc.set(data).await() }
            dao.updateFirestoreId(localId, doc.id)
        }
        return localId
    }

    suspend fun deleteTransaction(id: Long) {
        uid?.let { userId ->
            val firestoreId = dao.getFirestoreId(id)
            if (firestoreId.isNotBlank()) {
                runCatching {
                    firestore.document("users/$userId/transactions/$firestoreId").delete().await()
                }
            }
        }
        dao.deleteById(id)
    }

    suspend fun clearLocalData() = dao.deleteAll()

    suspend fun syncFromCloud() {
        val userId = uid ?: return
        runCatching {
            val snap = firestore.collection("users/$userId/transactions").get().await()
            dao.deleteAll()
            snap.documents.forEach { doc ->
                val entity = TransactionEntity(
                    firestoreId = doc.id,
                    title       = doc.getString("title") ?: return@forEach,
                    amount      = doc.getDouble("amount") ?: return@forEach,
                    type        = doc.getString("type") ?: "EXPENSE",
                    category    = doc.getString("category") ?: "OTHER",
                    dateEpoch   = doc.getLong("dateEpoch") ?: return@forEach,
                    note        = doc.getString("note") ?: ""
                )
                dao.insert(entity)
            }
        }
    }

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
