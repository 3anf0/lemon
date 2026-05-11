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

    @Delete
    suspend fun deleteEvent(event: EventEntity)

    @Update
    suspend fun updateEvent(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: Long)
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

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

// ── Database ──────────────────────────────────────────────────────────────

@Database(
    entities = [EventEntity::class, TransactionEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun transactionDao(): TransactionDao
}
