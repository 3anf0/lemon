package com.smartcal.app.data

import com.smartcal.app.data.local.EventDao
import com.smartcal.app.data.model.CalEvent
import com.smartcal.app.data.model.EventCategory
import com.smartcal.app.data.model.EventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(private val dao: EventDao) {

    fun getAllEvents(): Flow<List<CalEvent>> =
        dao.getAllEvents().map { list -> list.map { it.toDomain() } }

    fun getEventsForDay(date: LocalDate): Flow<List<CalEvent>> {
        val zone = ZoneId.systemDefault()
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.getEventsInRange(from, to).map { list -> list.map { it.toDomain() } }
    }

    suspend fun addEvent(event: CalEvent): Long =
        dao.insertEvent(event.toEntity())

    suspend fun deleteEvent(event: CalEvent) =
        dao.deleteById(event.id)

    suspend fun updateEvent(event: CalEvent) =
        dao.updateEvent(event.toEntity())
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
