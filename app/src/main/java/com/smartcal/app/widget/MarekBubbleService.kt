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
import com.smartcal.app.data.EventRepository
import com.smartcal.app.data.FinanceRepository
import com.smartcal.app.data.VoiceCommandHandler
import com.smartcal.app.data.model.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MarekBubbleService : Service() {

    @Inject lateinit var eventRepository: EventRepository
    @Inject lateinit var financeRepository: FinanceRepository
    @Inject lateinit var voiceCommandHandler: VoiceCommandHandler

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
    private var awaitingFollowUp = false

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val CHANNEL_ID  = "marek_bubble_channel"
        const val NOTIF_ID    = 42
        const val ACTION_STOP = "STOP_BUBBLE"
        const val ACTION_AUTO_LISTEN = "AUTO_LISTEN"
        const val WAKE_WORD   = "lemon"
        const val ACTION_BUBBLE_STARTED = "com.smartcal.app.BUBBLE_STARTED"

        fun start(context: Context) {
            val i = Intent(context, MarekBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(i)
            else context.startService(i)
        }

        fun startAutoListen(context: Context) {
            val i = Intent(context, MarekBubbleService::class.java).apply {
                action = ACTION_AUTO_LISTEN
            }
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
        sendBroadcast(Intent(ACTION_BUBBLE_STARTED))
        // Immediately awake — start listening without requiring wake word
        isAwake = true
        setBubbleAwake(true)
        mainHandler.postDelayed({ startListening() }, 700)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        speechRecognizer?.destroy()
        tts?.shutdown()
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        trashView?.let { runCatching { windowManager.removeView(it) } }
        // Stop wake word service so it can't re-open the bubble after close
        WakeWordService.stop(this)
        sendBroadcast(Intent(MarekTileService.ACTION_BUBBLE_CLOSED))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_AUTO_LISTEN -> {
                isAwake = true
                setBubbleAwake(true)
                mainHandler.postDelayed({ startListening() }, 600)
            }
        }
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
        mainHandler.post { showSpeechText(text) }
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bubble_${System.currentTimeMillis()}")
        }
        mainHandler.postDelayed({ hideSpeechText() }, 4000)
    }

    // speak() with callback fired when TTS finishes (or after timeout if TTS unavailable)
    private fun speakThen(text: String, onDone: () -> Unit) {
        mainHandler.post { showSpeechText(text) }
        val uttId = "bubble_then_${System.currentTimeMillis()}"
        if (ttsReady) {
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    if (id == uttId) {
                        tts?.setOnUtteranceProgressListener(null)
                        mainHandler.post { hideSpeechText(); onDone() }
                    }
                }
                override fun onError(id: String?) {
                    if (id == uttId) {
                        tts?.setOnUtteranceProgressListener(null)
                        mainHandler.post { hideSpeechText(); onDone() }
                    }
                }
            })
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, uttId)
        } else {
            val delay = maxOf(1500L, text.length * 80L)
            mainHandler.postDelayed({ hideSpeechText(); onDone() }, delay)
        }
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

        if (awaitingFollowUp) {
            handleFollowUp(lower)
            return
        }

        if (!isAwake) {
            // Only react to wake word when sleeping (e.g. after "zostań")
            if (lower.contains(WAKE_WORD)) {
                isAwake = true
                setBubbleAwake(true)
                speak("Tu Lemon, co tam?")
                mainHandler.postDelayed({ startListening() }, 2500)
            }
            return
        }

        // Try finance, then calendar — both via shared VoiceCommandHandler
        serviceScope.launch {
            val reply = voiceCommandHandler.handleFinance(text)
                ?: voiceCommandHandler.handleCalendarKeyword(text)
            if (reply != null) {
                mainHandler.post {
                    speakThen(reply) {
                        mainHandler.postDelayed({
                            speakThen("Czy coś jeszcze?") {
                                awaitingFollowUp = true
                                startListening()
                            }
                        }, 300)
                    }
                }
            } else {
                mainHandler.post {
                    speak("Nie rozumiem. Spróbuj np: dodaj siłownię o 18 albo zarobiłem 500")
                    mainHandler.postDelayed({ startListening() }, 3500)
                }
            }
        }
    }

    private fun handleFollowUp(lower: String) {
        awaitingFollowUp = false
        when {
            lower.containsAny("zostań", "nie ale") -> {
                isAwake = false
                setBubbleAwake(false)
                speak("Ok, zostanę w pobliżu.")
            }
            lower.containsAny("nie", "wyłącz", "zamknij", "koniec", "stop", "pa", "na razie") -> {
                speakThen("Dobra, do zobaczenia!") { stopSelf() }
            }
            lower.containsAny("tak", "yes", "słucham", "oczywiście", "jasne") -> {
                speakThen("Słucham!") { startListening() }
            }
            else -> {
                // Nieznana odpowiedź — traktuj jako nową komendę
                handleSpeech(lower)
            }
        }
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
