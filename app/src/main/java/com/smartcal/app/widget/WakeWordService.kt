package com.smartcal.app.widget

import android.app.*
import android.content.*
import android.os.*
import android.speech.*
import androidx.core.app.NotificationCompat
import com.smartcal.app.R

class WakeWordService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var isPaused = false

    companion object {
        const val CHANNEL_ID        = "wake_word_channel"
        const val NOTIF_ID          = 43
        const val ACTION_STOP       = "STOP_WAKE_WORD"
        const val ACTION_WAKE_STOPPED = "com.smartcal.app.WAKE_STOPPED"

        private val WAKE_WORDS = listOf("lemon", "cześć lemon", "hej lemon", "hey lemon")

        fun start(context: Context) {
            val i = Intent(context, WakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) =
            context.stopService(Intent(context, WakeWordService::class.java))
    }

    private val bubbleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MarekBubbleService.ACTION_BUBBLE_STARTED -> pause()
                MarekTileService.ACTION_BUBBLE_CLOSED    -> resume()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())

        val filter = IntentFilter().apply {
            addAction(MarekBubbleService.ACTION_BUBBLE_STARTED)
            addAction(MarekTileService.ACTION_BUBBLE_CLOSED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bubbleReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(bubbleReceiver, filter)
        }

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            initRecognizer()
            mainHandler.postDelayed({ startWakeListening() }, 500)
        } else {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(bubbleReceiver) }
        speechRecognizer?.destroy()
        speechRecognizer = null
        sendBroadcast(Intent(ACTION_WAKE_STOPPED))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    private fun initRecognizer() {
        runCatching { speechRecognizer?.destroy() }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}

            override fun onError(error: Int) {
                isListening = false
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    stopSelf()
                    return
                }
                val delay = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 2500L else 1500L
                if (!isPaused) mainHandler.postDelayed({ startWakeListening() }, delay)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.lowercase()

                if (text != null && WAKE_WORDS.any { text.contains(it) }) {
                    onWakeWordDetected()
                } else if (!isPaused) {
                    mainHandler.postDelayed({ startWakeListening() }, 300)
                }
            }
        })
    }

    private fun startWakeListening() {
        if (isListening || isPaused || speechRecognizer == null) return
        isListening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pl-PL")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-US"))
        }
        runCatching { speechRecognizer?.startListening(intent) }.onFailure {
            isListening = false
            mainHandler.postDelayed({ startWakeListening() }, 2000)
        }
    }

    private fun onWakeWordDetected() {
        pause()
        MarekBubbleService.startAutoListen(this)
    }

    private fun pause() {
        isPaused = true
        mainHandler.removeCallbacksAndMessages(null)
        if (isListening) {
            runCatching { speechRecognizer?.stopListening() }
            isListening = false
        }
    }

    private fun resume() {
        isPaused = false
        mainHandler.postDelayed({ startWakeListening() }, 800)
    }

    private fun buildNotification(): Notification {
        val stopPending = PendingIntent.getService(
            this, 0,
            Intent(this, WakeWordService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Lemon nasłuchuje")
            .setContentText("Powiedz \"cześć lemon\" aby aktywować")
            .setSmallIcon(R.drawable.ic_tile_marek)
            .addAction(0, "Wyłącz", stopPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lemon — nasłuchiwanie",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Nasłuchuje słowa kluczowego \"lemon\""
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
