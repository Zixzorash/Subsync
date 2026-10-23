package com.example.subsync.ai

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.subsync.model.AudioDialogueEvent
import com.example.subsync.model.MediaItemInfo
import com.example.subsync.model.SubtitleCue
import com.example.subsync.model.SubtitleFile
import com.example.subsync.model.SyncMethod
import com.example.subsync.model.SyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiSubtitleService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Resolves the active API key from:
     * 1. User-configured custom key in SharedPreferences
     * 2. BuildConfig.GEMINI_API_KEY
     */
    fun getResolvedApiKey(context: Context): String {
        val prefs = context.getSharedPreferences("subsync_prefs", Context.MODE_PRIVATE)
        val customKey = prefs.getString("user_gemini_api_key", "")?.trim().orEmpty()
        if (customKey.isNotBlank()) {
            return customKey
        }
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        return if (buildKey != "MY_GEMINI_API_KEY") buildKey else ""
    }

    fun saveCustomApiKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences("subsync_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("user_gemini_api_key", key.trim()).apply()
    }

    /**
     * Uses Gemini 3.5 Flash to analyze subtitle cues across the entire media timeline
     * (handling 2+ hours and thousands of cues by multi-segment anchor sampling).
     */
    suspend fun analyzeAndSynchronize(
        context: Context,
        media: MediaItemInfo,
        subtitle: SubtitleFile,
        speechEvents: List<AudioDialogueEvent>
    ): Result<Pair<Long, Double>> = withContext(Dispatchers.IO) {
        val apiKey = getResolvedApiKey(context)
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No valid GEMINI_API_KEY provided."))
        }

        try {
            // Sample anchor cues distributed across the timeline (beginning, 20%, 40%, 60%, 80%, end)
            val cueCount = subtitle.cues.size
            val sampledCues = if (cueCount <= 12) {
                subtitle.cues
            } else {
                val step = cueCount / 10
                (0 until 10).mapNotNull { idx -> subtitle.cues.getOrNull(idx * step) }
            }

            val cuesPreview = sampledCues.joinToString("\n") { cue ->
                "- Cue #${cue.id} [${cue.formatStartTime()} (${cue.startTimeMs}ms)]: \"${cue.text.take(45)}\""
            }

            // Sample voice events matching sampled cue timeframes
            val speechPreview = sampledCues.mapNotNull { cue ->
                speechEvents.minByOrNull { kotlin.math.abs(it.approximateTimeMs - cue.startTimeMs) }
            }.distinct().take(10).joinToString("\n") { event ->
                "- Speech onset @ ${event.approximateTimeMs}ms (Energy: ${event.audioEnergy})"
            }

            val durationMinutes = media.durationMs / 60000L

            val prompt = """
                You are SubSync AI, an expert audio-visual subtitle synchronization engineer.
                Analyze the following media metadata, dialogue speech events, and subtitle cues across a ${durationMinutes}-minute media file with ${cueCount} total cues.
                
                Media:
                - Title: ${media.title}
                - Total Duration: ${media.durationMs}ms (${media.formattedDuration})
                - Hash: ${media.openSubtitlesHash}
                
                Detected Speech Audio Onsets (Across timeline):
                $speechPreview
                
                Sampled Subtitle Cues (Desynchronized):
                $cuesPreview
                
                Tasks:
                1. Calculate the baseline timing offset in milliseconds (offset_ms): positive if subtitle needs to be shifted later, negative if earlier.
                2. Calculate the frame rate drift factor (drift_factor): 1.0 for no progressive drift, or e.g. 0.95904 for PAL 25fps to Film 23.976fps drift across ${durationMinutes} minutes.
                3. Provide 1-sentence technical explanation.
                
                Return ONLY a valid JSON object matching:
                {
                  "offset_ms": -2500,
                  "drift_factor": 1.0,
                  "confidence_percent": 96,
                  "explanation": "Detected uniform latency of -2500ms between audio speech onsets and subtitle cues."
                }
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.1)
                })
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                Log.e("GeminiSubtitleService", "API error: ${response.code} $errorBody")
                return@withContext Result.failure(Exception("Gemini error ${response.code}: $errorBody"))
            }

            val responseBody = response.body?.string().orEmpty()
            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawJsonText = parts?.optJSONObject(0)?.optString("text").orEmpty()

            val parsedJson = JSONObject(rawJsonText)
            val offsetMs = parsedJson.optLong("offset_ms", 0L)
            val drift = parsedJson.optDouble("drift_factor", 1.0)

            Result.success(Pair(offsetMs, drift))
        } catch (e: Exception) {
            Log.e("GeminiSubtitleService", "Failure in AI synchronization", e)
            Result.failure(e)
        }
    }
}
