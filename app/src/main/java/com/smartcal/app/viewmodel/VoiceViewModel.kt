package com.smartcal.app.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcal.app.data.ClaudeApiClient
import com.smartcal.app.data.DateParser
import com.smartcal.app.data.EventRepository
import com.smartcal.app.data.FinanceRepository
import com.smartcal.app.data.VoiceCommandHandler
import com.smartcal.app.data.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter as DTF
import java.util.Locale
import javax.inject.Inject

data class VoiceUiState(
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val transcript: String = "",
    val lastConfirmation: String = "",
    val conversationHistory: List<VoiceMessage> = emptyList(),
    val error: String? = null,
    val isAwake: Boolean = false
)

data class VoiceMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@HiltViewModel
class VoiceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geminiClient: ClaudeApiClient,
    private val eventRepository: EventRepository,
    private val financeRepository: FinanceRepository,
    private val voiceCommandHandler: VoiceCommandHandler
) : ViewModel() {

    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private var todayEventsSnapshot: List<CalEvent> = emptyList()
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    companion object {
        const val WAKE_WORD = "lemon"
    }

    init {
        initTts()
        viewModelScope.launch {
            eventRepository.getEventsForDay(LocalDate.now()).collect { events ->
                todayEventsSnapshot = events
            }
        }
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("pl", "PL"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.getDefault()
                }
                ttsReady = true
            }
        }
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.update { it.copy(error = "Rozpoznawanie mowy niedostępne") }
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { _state.update { it.copy(isListening = true, transcript = "", error = null) } }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { _state.update { it.copy(isListening = false) } }
            override fun onPartialResults(b: Bundle?) {
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                _state.update { it.copy(transcript = t) }
            }
            override fun onResults(b: Bundle?) {
                val text = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                _state.update { it.copy(isListening = false, transcript = text) }
                handleSpeechResult(text)
            }
            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH      -> "Nie rozumiem — spróbuj jeszcze raz"
                    SpeechRecognizer.ERROR_NETWORK       -> "Błąd sieci — sprawdź internet"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nic nie usłyszałem — spróbuj ponownie"
                    else -> "Błąd mikrofonu — spróbuj ponownie"
                }
                _state.update { it.copy(isListening = false, error = msg) }
            }
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pl-PL")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-US"))
        }
        speechRecognizer?.startListening(intent)
    }

    private fun handleSpeechResult(text: String) {
        val lower = text.lowercase().trim()
        if (lower.contains(WAKE_WORD)) {
            _state.update { it.copy(isAwake = true) }
            val g = "Tu Lemon, co tam?"
            addToHistory(g, isUser = false); speak(g); return
        }
        if (!_state.value.isAwake) {
            val hint = "Powiedz Lemon żeby zacząć."
            addToHistory(hint, isUser = false); speak(hint); return
        }
        addToHistory(text, isUser = true)
        _state.update { it.copy(isProcessing = true) }
        processCommand(text)
    }

    private fun processCommand(text: String) = viewModelScope.launch {
        // First try keyword-based finance detection (fast, no API needed)
        val financeResult = voiceCommandHandler.handleFinance(text)
        if (financeResult != null) {
            addToHistory(financeResult, isUser = false)
            speak(financeResult)
            _state.update { it.copy(isProcessing = false, lastConfirmation = financeResult) }
            return@launch
        }

        // Otherwise send to Gemini for calendar commands
        runCatching { geminiClient.parseVoiceCommand(text, todayEventsSnapshot) }
            .onSuccess { result ->
                val reply = when (result.intent) {
                    "ADD_EVENT" -> {
                        if (result.eventTitle != null && result.eventTime != null) {
                            runCatching {
                                val parts = result.eventTime.split(":")
                                val h = parts[0].toInt(); val m = parts[1].toInt()
                                val cat = runCatching { EventCategory.valueOf(result.eventCategory ?: "PERSONAL") }
                                    .getOrDefault(EventCategory.PERSONAL)

                                // Try DateParser on original speech first (catches "w środę", "10 maja" etc.)
                                // then fall back to Gemini's eventDate field
                                val date = DateParser.parse(text)
                                    ?: runCatching {
                                        if (result.eventDate?.contains("-") == true)
                                            LocalDate.parse(result.eventDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                        else LocalDate.now()
                                    }.getOrDefault(LocalDate.now())

                                eventRepository.addEvent(CalEvent(
                                    title = result.eventTitle, category = cat,
                                    startTime = date.atTime(h, m), endTime = date.atTime(h, m).plusHours(1)
                                ))
                                val dateLabel = DateParser.formatPolish(date)
                                "${result.eventTitle} dodano na $dateLabel o %02d:%02d!".format(h, m)
                            }.getOrElse { "Nie udało się dodać wydarzenia." }
                        } else result.clarificationQuestion ?: "O której godzinie?"
                    }
                    "DELETE_EVENT" -> {
                        val target = result.targetEventTitle
                        if (target.isNullOrBlank()) {
                            "Które wydarzenie usunąć? Powiedz np: \"usuń siłownię\""
                        } else {
                            val allEvents = eventRepository.getAllEvents().first()
                            val match = allEvents.firstOrNull {
                                it.title.contains(target, ignoreCase = true) ||
                                target.contains(it.title, ignoreCase = true)
                            }
                            if (match != null) {
                                eventRepository.deleteEvent(match)
                                val dateLabel = DateParser.formatPolish(match.startTime.toLocalDate())
                                val time = match.startTime.format(DTF.ofPattern("HH:mm"))
                                "Usunięto: ${match.title} ($dateLabel o $time) ✓"
                            } else {
                                "Nie znalazłem \"$target\" w kalendarzu. Sprawdź nazwę."
                            }
                        }
                    }
                    "PLAN_DAY", "QUERY" -> result.aiResponse ?: result.confirmation
                    else -> result.clarificationQuestion ?: result.confirmation
                }
                addToHistory(reply, isUser = false); speak(reply)
                _state.update { it.copy(isProcessing = false, lastConfirmation = reply) }
            }
            .onFailure {
                val msg = "Przepraszam, coś poszło nie tak. Sprawdź internet."
                addToHistory(msg, isUser = false); speak(msg)
                _state.update { it.copy(isProcessing = false) }
            }
    }

    fun sendTextCommand(text: String) {
        if (text.isBlank()) return
        _state.update { it.copy(isProcessing = true, transcript = text, isAwake = true) }
        addToHistory(text, isUser = true)
        processCommand(text)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _state.update { it.copy(isListening = false) }
    }

    private fun addToHistory(text: String, isUser: Boolean) {
        _state.update { s ->
            s.copy(conversationHistory = (s.conversationHistory + VoiceMessage(text, isUser)).takeLast(30))
        }
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utt_${System.currentTimeMillis()}")
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
        tts?.shutdown()
    }
}

// Extension helper
private fun String.containsAny(vararg words: String) = words.any { this.contains(it) }
