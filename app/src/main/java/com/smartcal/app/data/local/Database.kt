package com.smartcal.app.data.local

import androidx.room.*
import com.smartcal.app.data.model.EventEntity
import com.smartcal.app.data.model.TransactionEntity
import kotlinx.coroutines.flow.Flow

// ── Event DAO ─────────────────────────────────────────────────────────────

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY startEpoch ASC")
    fun getAllEvents(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE startEpoch >= :from AND startEpoch < :to ORDER BY startEpoch ASC")
    fun getEventsInRange(from: Long, to: Long): Flow<List<EventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<EventEntity>)

    @Delete
    suspend fun deleteEvent(event: EventEntity)

    @Update
    suspend fun updateEvent(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM events")
    suspend fun deleteAll()

    @Query("UPDATE events SET firestoreId = :fid WHERE id = :id")
    suspend fun updateFirestoreId(id: Long, fid: String)

    @Query("SELECT firestoreId FROM events WHERE id = :id")
    suspend fun getFirestoreId(id: Long): String

    @Query("SELECT * FROM events WHERE firestoreId = :fid LIMIT 1")
    suspend fun getByFirestoreId(fid: String): EventEntity?
}

// ── Transaction DAO ───────────────────────────────────────────────────────

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY dateEpoch DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE dateEpoch >= :from AND dateEpoch < :to ORDER BY dateEpoch DESC")
    fun getTransactionsInRange(from: Long, to: Long): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tx: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(txs: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("UPDATE transactions SET firestoreId = :fid WHERE id = :id")
    suspend fun updateFirestoreId(id: Long, fid: String)

    @Query("SELECT firestoreId FROM transactions WHERE id = :id")
    suspend fun getFirestoreId(id: Long): String

    @Query("SELECT * FROM transactions WHERE firestoreId = :fid LIMIT 1")
    suspend fun getByFirestoreId(fid: String): TransactionEntity?
}

// ── Database ──────────────────────────────────────────────────────────────

@Database(
    entities = [EventEntity::class, TransactionEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun transactionDao(): TransactionDao
}
