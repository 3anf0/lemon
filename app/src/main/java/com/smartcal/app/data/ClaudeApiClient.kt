package com.smartcal.app.data

import android.util.Log
import com.smartcal.app.BuildConfig
import com.smartcal.app.data.model.CalEvent
import com.smartcal.app.data.model.VoiceCommandResult
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClaudeApiClient @Inject constructor(private val httpClient: OkHttpClient) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val baseUrl
        get() = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${BuildConfig.GEMINI_API_KEY}"

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    suspend fun parseVoiceCommand(
        userSpeech: String,
        todayEvents: List<CalEvent>
    ): VoiceCommandResult {
        val now = LocalDateTime.now()
        val todayStr = now.toLocalDate().toString()
        val tomorrowStr = now.toLocalDate().plusDays(1).toString()
        val eventsStr = if (todayEvents.isEmpty()) "brak"
        else todayEvents.joinToString(", ") { "${it.title} o ${it.startTime.format(timeFmt)}" }

        // Very simple, explicit prompt — no ambiguity for Gemini
        val prompt = """
Respond ONLY with a single JSON object. No markdown. No explanation. Just the raw JSON.

Today: $todayStr
Tomorrow: $tomorrowStr
User's events today: $eventsStr
User said: "$userSpeech"

Classify the user's message into one of these intents and fill in the fields:

If they want to ADD an event (e.g. "dodaj", "add", "wpisz", "zaplanuj X o Y"):
{"intent":"ADD_EVENT","eventTitle":"<name>","eventTime":"<HH:mm>","eventDate":"<yyyy-MM-dd>","eventCategory":"<WORK|HEALTH|PERSONAL|HABIT|SOCIAL>","targetEventTitle":null,"confirmation":"<Polish confirmation>","needsClarification":false,"clarificationQuestion":null,"aiResponse":null}

If they want to DELETE an event:
{"intent":"DELETE_EVENT","eventTitle":null,"eventTime":null,"eventDate":null,"eventCategory":null,"targetEventTitle":"<name to delete>","confirmation":"<Polish confirmation>","needsClarification":false,"clarificationQuestion":null,"aiResponse":null}

If they want to see their schedule (e.g. "co mam", "plan", "schedule", "what"):
{"intent":"QUERY","eventTitle":null,"eventTime":null,"eventDate":null,"eventCategory":null,"targetEventTitle":null,"confirmation":"Twój plan:","needsClarification":false,"clarificationQuestion":null,"aiResponse":"<list today events in Polish>"}

If they want help planning the day (e.g. "zaplanuj dzień", "plan my day"):
{"intent":"PLAN_DAY","eventTitle":null,"eventTime":null,"eventDate":null,"eventCategory":null,"targetEventTitle":null,"confirmation":"Oto plan!","needsClarification":false,"clarificationQuestion":null,"aiResponse":"<helpful day plan in Polish>"}

Otherwise:
{"intent":"UNKNOWN","eventTitle":null,"eventTime":null,"eventDate":null,"eventCategory":null,"targetEventTitle":null,"confirmation":"Nie zrozumiałem.","needsClarification":true,"clarificationQuestion":"Możesz powtórzyć?","aiResponse":null}

Rules:
- Times like "6", "18", "6pm", "18:00", "szósta", "osiemnasta" all mean "18:00"
- "jutro" or "tomorrow" means date = $tomorrowStr
- "dziś" or "today" or no date = $todayStr
- "siłownia" or "gym" → eventCategory = HEALTH
- "spotkanie" or "meeting" or "praca" → eventCategory = WORK
- Always respond in Polish in confirmation and aiResponse fields
- Output ONLY the JSON, nothing else
        """.trimIndent()

        val requestBody = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt) }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.0)  // deterministic
                put("maxOutputTokens", 300)
                put("responseMimeType", "application/json")  // force JSON mode
            }
        }.toString()

        return try {
            val response = httpClient.newCall(
                Request.Builder()
                    .url(baseUrl)
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()

            val body = response.body?.string() ?: throw Exception("Empty response")
            Log.d("Gemini", "Raw: $body")

            // Detect API errors (bad key, quota etc.)
            if (body.contains("\"error\"") && !body.contains("\"candidates\"")) {
                val errMsg = runCatching {
                    json.parseToJsonElement(body)
                        .jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                }.getOrNull() ?: "API error"
                Log.e("Gemini", "API Error: $errMsg")
                return VoiceCommandResult(
                    intent = "UNKNOWN",
                    confirmation = "Błąd API: $errMsg",
                    needsClarification = false,
                    clarificationQuestion = null
                )
            }

            val rawText = json.parseToJsonElement(body)
                .jsonObject["candidates"]
                ?.jsonArray?.getOrNull(0)
                ?.jsonObject?.get("content")
                ?.jsonObject?.get("parts")
                ?.jsonArray?.getOrNull(0)
                ?.jsonObject?.get("text")
                ?.jsonPrimitive?.content
                ?: throw Exception("Cannot read Gemini response text")

            Log.d("Gemini", "Text: $rawText")

            // Strip any accidental markdown
            val clean = rawText.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()

            // Find the JSON object even if there's stray text around it
            val jsonStart = clean.indexOf('{')
            val jsonEnd = clean.lastIndexOf('}')
            val jsonStr = if (jsonStart >= 0 && jsonEnd > jsonStart)
                clean.substring(jsonStart, jsonEnd + 1)
            else clean

            Log.d("Gemini", "Parsed: $jsonStr")
            json.decodeFromString<VoiceCommandResult>(jsonStr)

        } catch (e: Exception) {
            Log.e("Gemini", "Error: ${e.message}", e)
            // Try to give a useful fallback based on keywords in the speech
            keywordFallback(userSpeech, todayStr)
        }
    }

    /**
     * Offline keyword fallback — works with no internet or when Gemini fails.
     * Handles the most common commands without any AI.
     */
    private fun keywordFallback(speech: String, todayStr: String): VoiceCommandResult {
        val s = speech.lowercase()

        // Detect ADD intent
        val isAdd = s.contains("dodaj") || s.contains("add") || s.contains("wpisz") ||
                s.contains("zaplanuj") || s.contains("umów") || s.contains("zapisz")

        // Detect time — look for patterns like "18", "o 18", "6pm", "9:00"
        val timeRegex = Regex("""(?:o\s+)?(\d{1,2})(?::(\d{2}))?(?:\s*(?:pm|AM|PM))?""")
        val timeMatch = timeRegex.find(s)
        var hour = -1
        var minute = 0
        if (timeMatch != null) {
            hour = timeMatch.groupValues[1].toIntOrNull() ?: -1
            minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
            if (hour in 1..6 && s.contains("pm")) hour += 12
            if (hour == 12 && s.contains("am")) hour = 0
        }

        // Map common words to hours
        if (hour == -1) {
            when {
                s.contains("rano") || s.contains("morning") -> { hour = 8; minute = 0 }
                s.contains("południe") || s.contains("noon") -> { hour = 12; minute = 0 }
                s.contains("wieczór") || s.contains("evening") -> { hour = 19; minute = 0 }
                s.contains("noc") || s.contains("night") -> { hour = 22; minute = 0 }
            }
        }

        // Detect date
        val date = when {
            s.contains("jutro") || s.contains("tomorrow") ->
                java.time.LocalDate.now().plusDays(1).toString()
            else -> todayStr
        }

        // Detect category
        val category = when {
            s.contains("siłowni") || s.contains("gym") || s.contains("sport") ||
            s.contains("bieg") || s.contains("trening") || s.contains("fitness") -> "HEALTH"
            s.contains("spotkani") || s.contains("meeting") || s.contains("praca") ||
            s.contains("work") || s.contains("biuro") || s.contains("szef") -> "WORK"
            s.contains("kolacja") || s.contains("obiad") || s.contains("śniadanie") ||
            s.contains("lunch") || s.contains("dinner") -> "SOCIAL"
            else -> "PERSONAL"
        }

        // Try to extract event name — remove common words to get the event title
        val stopWords = listOf("dodaj","add","wpisz","zaplanuj","umów","zapisz",
            "o","na","jutro","dziś","dzisiaj","tomorrow","today","rano","wieczór",
            "spotkanie","siłownię","siłownia","trening")
        var title = s
        stopWords.forEach { title = title.replace(it, " ") }
        // Remove time pattern
        title = title.replace(timeRegex, " ").trim()
        // Clean up extra spaces
        title = title.split(" ").filter { it.length > 2 }.joinToString(" ").trim()

        // Detect QUERY intent
        val isQuery = s.contains("co mam") || s.contains("plan") || s.contains("schedule") ||
                s.contains("harmonogram") || s.contains("lista") || s.contains("pokaż")

        // Detect PLAN_DAY intent
        val isPlanDay = s.contains("zaplanuj dzień") || s.contains("plan my day") ||
                s.contains("zaplanuj mój") || s.contains("co powinienem")

        return when {
            isPlanDay -> VoiceCommandResult(
                intent = "PLAN_DAY",
                confirmation = "Oto Twój plan na dziś!",
                aiResponse = "Nie mam połączenia z AI, ale pamiętaj aby zaplanować przerwy, ważne zadania rano i aktywność fizyczną!",
                needsClarification = false
            )
            isQuery -> VoiceCommandResult(
                intent = "QUERY",
                confirmation = "Sprawdzam Twój plan!",
                aiResponse = "Sprawdź zakładkę Kalendarz aby zobaczyć swoje wydarzenia.",
                needsClarification = false
            )
            isAdd && hour >= 0 -> {
                val eventTitle = when {
                    s.contains("siłowni") || s.contains("gym") -> "Siłownia"
                    s.contains("spotkani") || s.contains("meeting") -> "Spotkanie"
                    s.contains("trening") || s.contains("training") -> "Trening"
                    s.contains("kolacja") || s.contains("dinner") -> "Kolacja"
                    s.contains("obiad") || s.contains("lunch") -> "Obiad"
                    s.contains("śniadanie") || s.contains("breakfast") -> "Śniadanie"
                    s.contains("lekarz") || s.contains("doctor") -> "Lekarz"
                    title.isNotBlank() -> title.replaceFirstChar { it.uppercase() }
                    else -> "Wydarzenie"
                }
                VoiceCommandResult(
                    intent = "ADD_EVENT",
                    eventTitle = eventTitle,
                    eventTime = "%02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59)),
                    eventDate = date,
                    eventCategory = category,
                    confirmation = "Dodano: $eventTitle o %02d:%02d!".format(hour, minute),
                    needsClarification = false
                )
            }
            isAdd -> VoiceCommandResult(
                intent = "UNKNOWN",
                confirmation = "O której godzinie?",
                needsClarification = true,
                clarificationQuestion = "Podaj godzinę — na przykład: dodaj siłownię o 18"
            )
            else -> VoiceCommandResult(
                intent = "UNKNOWN",
                confirmation = "Spróbuj powiedzieć np: \"dodaj siłownię o 18\" lub \"co mam dziś\"",
                needsClarification = true,
                clarificationQuestion = null
            )
        }
    }
}
