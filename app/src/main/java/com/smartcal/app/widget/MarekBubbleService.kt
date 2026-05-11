package com.smartcal.app.widget

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.DisplayMetrics
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.core.app.NotificationCompat
import com.smartcal.app.MainActivity
import com.smartcal.app.widget.MarekTileService
import com.smartcal.app.R
import com.smartcal.app.data.DateParser
import com.smartcal.app.data.KeywordMatcher
import com.smartcal.app.data.KeywordMatcher.matchesAny
import com.smartcal.app.data.EventRepository
import com.smartcal.app.data.FinanceRepository
import com.smartcal.app.data.model.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MarekBubbleService : Service() {

    @Inject lateinit var eventRepository: EventRepository
    @Inject lateinit var financeRepository: FinanceRepository

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var trashView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var initialX = 0; private var initialY = 0
    private var initialTouchX = 0f; private var initialTouchY = 0f
    private var isDragging = false
    private var trashVisible = false
    private var screenWidth = 0; private var screenHeight = 0

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var isListening = false
    private var isAwake = false

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val CHANNEL_ID  = "marek_bubble_channel"
        const val NOTIF_ID    = 42
        const val ACTION_STOP = "STOP_BUBBLE"
        const val WAKE_WORD   = "lemon"

        fun start(context: Context) {
            val i = Intent(context, MarekBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) =
            context.stopService(Intent(context, MarekBubbleService::class.java))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        getScreenSize()
        initTts()
        initSpeech()
        showBubble()
        showTrash()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        speechRecognizer?.destroy()
        tts?.shutdown()
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        trashView?.let { runCatching { windowManager.removeView(it) } }
        // Tell the tile that bubble is gone so it resets its state
        sendBroadcast(Intent(MarekTileService.ACTION_BUBBLE_CLOSED))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    // ── Screen size ───────────────────────────────────────────────────────

    private fun getScreenSize() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth  = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    // Trash center is always bottom-center of screen
    private val trashCenterX get() = screenWidth / 2f
    private val trashCenterY get() = screenHeight - dpToPx(80)

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt().toFloat()

    // ── TTS ───────────────────────────────────────────────────────────────

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("pl", "PL"))
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED)
                    tts?.language = Locale.getDefault()
                ttsReady = true
            }
        }
    }

    private fun speak(text: String) {
        // Show what Marek is saying in the speech bubble
        mainHandler.post { showSpeechText(text) }
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bubble_${System.currentTimeMillis()}")
        }
        // Auto-hide text after 4 seconds
        mainHandler.postDelayed({ hideSpeechText() }, 4000)
    }

    // ── Speech ────────────────────────────────────────────────────────────

    private fun initSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        runCatching { speechRecognizer?.destroy() }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { setListeningUi(true) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onError(error: Int) {
                isListening = false
                setListeningUi(false)
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                setListeningUi(false)
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                handleSpeech(text)
            }
        })
    }

    private fun startListening() {
        if (isListening) return
        // Recreate recognizer if it was destroyed by a previous error
        if (speechRecognizer == null) initSpeech()
        isListening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pl-PL")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-US"))
        }
        speechRecognizer?.startListening(intent)
    }

    // ── Command handling — saves directly, no app open ───────────────────

    private fun handleSpeech(text: String) {
        val lower = text.lowercase()

        // Wake word check
        if (lower.contains(WAKE_WORD)) {
            isAwake = true
            setBubbleAwake(true)
            speak("Tu Lemon, co tam?")
            mainHandler.postDelayed({ startListening() }, 2500)
            return
        }

        if (!isAwake) {
            speak("Powiedz Lemon żeby zacząć")
            return
        }

        // Try finance command first (keyword-based, no API needed)
        val financeHandled = tryHandleFinance(lower)
        if (financeHandled) return

        // Try calendar command
        val calendarHandled = tryHandleCalendar(lower)
        if (calendarHandled) return

        // Unknown — give hint and listen again
        speak("Nie rozumiem. Spróbuj np: dodaj siłownię o 18 albo zarobiłem 500")
        mainHandler.postDelayed({ startListening() }, 3000)
    }

    // ── Finance — saves directly to Room ─────────────────────────────────

    private fun tryHandleFinance(s: String): Boolean {
        val isIncome  = s.matchesAny(KeywordMatcher.INCOME_STEMS)
        val isExpense = s.matchesAny(KeywordMatcher.EXPENSE_STEMS)
        if (!isIncome && !isExpense) return false

        val amountRegex = Regex("""(\d+(?:[.,]\d{1,2})?)""")
        val amount = amountRegex.find(s)
            ?.groupValues?.get(1)
            ?.replace(",", ".")
            ?.toDoubleOrNull() ?: return false

        val category = when {
            s.matchesAny(KeywordMatcher.FUEL_STEMS)     -> ExpenseCategory.FUEL
            s.matchesAny(KeywordMatcher.GAMBLING_STEMS) -> ExpenseCategory.GAMBLING
            s.matchesAny(KeywordMatcher.FOOD_STEMS)     -> ExpenseCategory.FOOD
            s.matchesAny(KeywordMatcher.OTHER_STEMS)    -> ExpenseCategory.OTHER
            else -> if (isIncome) ExpenseCategory.WORK else ExpenseCategory.OTHER
        }

        val title = KeywordMatcher.findTitle(s)
            ?: if (isIncome) "Przychód" else "Wydatek"

        val type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE
        val date = DateParser.parse(s) ?: LocalDate.now()

        serviceScope.launch {
            financeRepository.addTransaction(Transaction(
                title    = title,
                amount   = amount,
                type     = type,
                category = category,
                date     = date
            ))
            val sign     = if (type == TransactionType.INCOME) "plus" else "minus"
            val typeWord = if (type == TransactionType.INCOME) "Przychód" else "Wydatek"
            mainHandler.post {
                speak("$typeWord zapisany! $sign ${"%.0f".format(amount)} złotych. $title.")
            }
        }
        return true
    }

    private fun tryHandleCalendar(s: String): Boolean {
        val isAdd = s.matchesAny(listOf("dodaj","add","wpisz","zaplanuj","umów","zapisz","ustaw","dodajże"))
        if (!isAdd) return false

        val timeRegex = Regex("""(?:o\s+)?(\d{1,2})(?::(\d{2}))?""")
        val timeMatch = timeRegex.find(s) ?: return false
        var hour   = timeMatch.groupValues[1].toIntOrNull() ?: return false
        val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
        if (s.contains("pm") && hour < 12) hour += 12
        if (hour !in 0..23) return false

        val category = when {
            s.matchesAny(KeywordMatcher.HEALTH_STEMS)  -> EventCategory.HEALTH
            s.matchesAny(KeywordMatcher.WORK_STEMS)    -> EventCategory.WORK
            s.matchesAny(KeywordMatcher.SOCIAL_STEMS)  -> EventCategory.SOCIAL
            s.matchesAny(KeywordMatcher.DOCTOR_STEMS)  -> EventCategory.PERSONAL
            else                                       -> EventCategory.PERSONAL
        }

        val eventTitle = KeywordMatcher.findTitle(s) ?: "Wydarzenie"
        val date  = DateParser.parse(s) ?: LocalDate.now()
        val start = date.atTime(hour, minute)
        val end   = start.plusHours(1)

        serviceScope.launch {
            eventRepository.addEvent(CalEvent(
                title     = eventTitle,
                startTime = start,
                endTime   = end,
                category  = category
            ))
            val dateLabel = DateParser.formatPolish(date)
            mainHandler.post {
                speak("$eventTitle dodano na $dateLabel o %02d:%02d!".format(hour, minute))
            }
        }
        return true
    }

    // ── Bubble UI ─────────────────────────────────────────────────────────

    private fun showBubble() {
        bubbleView = LayoutInflater.from(this).inflate(R.layout.bubble_marek, null)

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 300
        }

        bubbleView!!.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX      = bubbleParams?.x ?: 0
                    initialY      = bubbleParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging    = false
                    showTrashZone()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) isDragging = true
                    if (isDragging && bubbleParams != null) {
                        bubbleParams!!.x = initialX + dx
                        bubbleParams!!.y = initialY + dy
                        runCatching { windowManager.updateViewLayout(view, bubbleParams) }
                        highlightTrash(isNearTrash(event.rawX, event.rawY))
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        if (isNearTrash(event.rawX, event.rawY)) {
                            hideTrashZone()
                            stopSelf()   // broadcast sent in onDestroy
                        } else {
                            hideTrashZone()
                        }
                    } else {
                        hideTrashZone()
                        startListening()
                    }
                    true
                }
                else -> false
            }
        }

        // Wrap in try/catch — if overlay permission revoked mid-session this would crash
        runCatching {
            windowManager.addView(bubbleView, bubbleParams)
        }.onFailure {
            stopSelf()  // can't show window, close gracefully
        }
    }

    // ── Trash zone ────────────────────────────────────────────────────────

    private fun showTrash() {
        trashView = LayoutInflater.from(this).inflate(R.layout.bubble_trash, null)

        val trashParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }

        trashView!!.visibility = View.GONE
        runCatching { windowManager.addView(trashView, trashParams) }
    }

    private fun isNearTrash(rawX: Float, rawY: Float): Boolean {
        // Trash is always at bottom-center — compare with screen coordinates
        return Math.abs(rawX - trashCenterX) < 120 &&
               Math.abs(rawY - trashCenterY) < 120
    }

    private fun showTrashZone() {
        if (trashVisible) return
        trashVisible = true
        trashView?.visibility = View.VISIBLE
    }

    private fun hideTrashZone() {
        if (!trashVisible) return
        trashVisible = false
        trashView?.visibility = View.GONE
        highlightTrash(false)
    }

    private fun highlightTrash(on: Boolean) {
        trashView?.findViewById<TextView>(R.id.trash_icon)
            ?.setBackgroundResource(
                if (on) R.drawable.trash_background_active
                else    R.drawable.trash_background
            )
    }

    // ── Bubble visual state ───────────────────────────────────────────────

    private fun setListeningUi(listening: Boolean) {
        mainHandler.post {
            bubbleView?.findViewById<View>(R.id.pulse_ring)?.visibility =
                if (listening) View.VISIBLE else View.GONE
            // Show "listening..." text in speech bubble while mic is active
            if (listening) showSpeechText("🎙️ Słucham...") else hideSpeechText()
        }
    }

    private fun showSpeechText(text: String) {
        val bubble = bubbleView?.findViewById<android.view.View>(R.id.speech_bubble) ?: return
        val tv     = bubbleView?.findViewById<android.widget.TextView>(R.id.speech_text) ?: return
        tv.text    = text
        bubble.visibility = View.VISIBLE
    }

    private fun hideSpeechText() {
        bubbleView?.findViewById<android.view.View>(R.id.speech_bubble)?.visibility = View.GONE
    }

    private fun setBubbleAwake(awake: Boolean) {
        mainHandler.post {
            bubbleView?.findViewById<LinearLayout>(R.id.bubble_circle)
                ?.setBackgroundResource(
                    if (awake) R.drawable.bubble_background_awake
                    else       R.drawable.bubble_background
                )
        }
    }

    // ── Notification ──────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val stopPending = PendingIntent.getService(
            this, 0,
            Intent(this, MarekBubbleService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPending = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).apply {
                putExtra("start_destination", "voice")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Lemon działa w tle")
            .setContentText("Naciśnij dymek aby mówić · Przeciągnij na 🗑️ aby zamknąć")
            .setSmallIcon(R.drawable.ic_tile_marek)
            .setContentIntent(openPending)
            .addAction(0, "Zamknij", stopPending)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Lemon — dymek czatu",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Dymek asystenta Lemon"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}

private fun String.containsAny(vararg words: String) = words.any { this.contains(it) }
