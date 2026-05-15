package com.smartcal.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smartcal.app.data.local.EventDao
import com.smartcal.app.data.model.CalEvent
import com.smartcal.app.data.model.EventCategory
import com.smartcal.app.data.model.EventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val dao: EventDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    private val uid get() = auth.currentUser?.uid

    fun getAllEvents(): Flow<List<CalEvent>> =
        dao.getAllEvents().map { list -> list.map { it.toDomain() } }

    fun getEventsForDay(date: LocalDate): Flow<List<CalEvent>> {
        val zone = ZoneId.systemDefault()
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.getEventsInRange(from, to).map { list -> list.map { it.toDomain() } }
    }

    suspend fun addEvent(event: CalEvent): Long {
        val entity = event.toEntity()
        val localId = dao.insertEvent(entity)
        uid?.let { userId ->
            val doc = firestore.collection("users/$userId/events").document()
            val data = mapOf(
                "title" to entity.title,
                "startEpoch" to entity.startEpoch,
                "endEpoch" to entity.endEpoch,
                "category" to entity.category,
                "isAiSuggested" to entity.isAiSuggested,
                "notes" to entity.notes,
                "localId" to localId
            )
            runCatching { doc.set(data).await() }
            dao.updateFirestoreId(localId, doc.id)
        }
        return localId
    }

    suspend fun deleteEvent(event: CalEvent) {
        uid?.let { userId ->
            val firestoreId = dao.getFirestoreId(event.id)
            if (firestoreId.isNotBlank()) {
                runCatching {
                    firestore.document("users/$userId/events/$firestoreId").delete().await()
                }
            }
        }
        dao.deleteById(event.id)
    }

    suspend fun updateEvent(event: CalEvent) =
        dao.updateEvent(event.toEntity())

    suspend fun clearLocalData() = dao.deleteAll()

    suspend fun syncFromCloud() {
        val userId = uid ?: return
        runCatching {
            val snap = firestore.collection("users/$userId/events").get().await()
            dao.deleteAll()
            snap.documents.forEach { doc ->
                val entity = EventEntity(
                    firestoreId   = doc.id,
                    title         = doc.getString("title") ?: return@forEach,
                    startEpoch    = doc.getLong("startEpoch") ?: return@forEach,
                    endEpoch      = doc.getLong("endEpoch") ?: return@forEach,
                    category      = doc.getString("category") ?: "PERSONAL",
                    isAiSuggested = doc.getBoolean("isAiSuggested") ?: false,
                    notes         = doc.getString("notes") ?: ""
                )
                dao.insertEvent(entity)
            }
        }
    }
}

// ── Extension mappers ──────────────────────────────────────────────────────

fun EventEntity.toDomain(): CalEvent {
    val zone = ZoneId.systemDefault()
    return CalEvent(
        id = id,
        title = title,
        startTime = Instant.ofEpochMilli(startEpoch).atZone(zone).toLocalDateTime(),
        endTime = Instant.ofEpochMilli(endEpoch).atZone(zone).toLocalDateTime(),
        category = runCatching { EventCategory.valueOf(category) }.getOrDefault(EventCategory.PERSONAL),
        isAiSuggested = isAiSuggested,
        notes = notes
    )
}

fun CalEvent.toEntity(): EventEntity {
    val zone = ZoneId.systemDefault()
    return EventEntity(
        id = id,
        title = title,
        startEpoch = startTime.atZone(zone).toInstant().toEpochMilli(),
        endEpoch = endTime.atZone(zone).toInstant().toEpochMilli(),
        category = category.name,
        isAiSuggested = isAiSuggested,
        notes = notes
    )
}
