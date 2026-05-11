package com.smartcal.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcal.app.data.EventRepository
import com.smartcal.app.data.EventReminderManager
import com.smartcal.app.data.model.CalEvent
import com.smartcal.app.data.model.EventCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

data class CalendarUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val eventsForDay: List<CalEvent> = emptyList(),
    val allEvents: List<CalEvent> = emptyList(),
    val isAddingEvent: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val eventRepo: EventRepository,
    private val reminderManager: EventReminderManager
) : ViewModel() {

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            eventRepo.getAllEvents().collect { events ->
                _state.update { it.copy(allEvents = events) }
                refreshDay()
            }
        }
    }

    fun selectDate(date: LocalDate) {
        _state.update { it.copy(selectedDate = date) }
        refreshDay()
    }

    private fun refreshDay() {
        val selected = _state.value.selectedDate
        val dayEvents = _state.value.allEvents.filter {
            it.startTime.toLocalDate() == selected
        }.sortedBy { it.startTime }
        _state.update { it.copy(eventsForDay = dayEvents) }
    }

    fun addEvent(
        title: String,
        hour: Int,
        minute: Int,
        durationMinutes: Int = 60,
        category: EventCategory = EventCategory.PERSONAL,
        notes: String = "",
        date: LocalDate? = null,
        isAiSuggested: Boolean = false
    ) = viewModelScope.launch {
        val eventDate = date ?: _state.value.selectedDate
        val start = LocalDateTime.of(eventDate, LocalTime.of(hour, minute))
        val end = start.plusMinutes(durationMinutes.toLong())
        val event = CalEvent(
            title = title,
            startTime = start,
            endTime = end,
            category = category,
            notes = notes,
            isAiSuggested = isAiSuggested
        )
        val newId = eventRepo.addEvent(event)
        reminderManager.schedule(event.copy(id = newId))
    }

    fun deleteEvent(event: CalEvent) = viewModelScope.launch {
        reminderManager.cancel(event)
        eventRepo.deleteEvent(event)
    }

    fun setAddingEvent(show: Boolean) = _state.update { it.copy(isAddingEvent = show) }
}
