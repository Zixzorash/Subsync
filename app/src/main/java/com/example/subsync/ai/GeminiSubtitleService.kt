package com.example.subsync.ai

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
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    /**
     * Uses Gemini 3.5 Flash to analyze subtitle cues against audio speech timestamps
     * and compute millisecond offset, drift factor, and sync explanation.
     */
    suspend fun analyzeAndSynchronize(
        media: MediaItemInfo,
        subtitle: SubtitleFile,
        speechEvents: List<AudioDialogueEvent>
    ): Result<SyncResult> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(IllegalStateException("No valid GEMINI_API_KEY configured in Secrets panel."))
        }

        try {
            val cuesPreview = subtitle.cues.take(8).joinToString("\n") { cue ->
                "- [${cue.startTimeMs}ms - ${cue.endTimeMs}ms]: \"${cue.text}\""
            }

            val speechPreview = speechEvents.take(8).joinToString("\n") { event ->
                "- [${event.approximateTimeMs}ms]: \"${event.recognizedPhrase}\""
            }

            val prompt = """
                You are SubSync AI, an expert audio-visual subtitle synchronization engineer.
                Analyze the following media metadata, dialogue speech events, and desynchronized subtitle cues.
                
                Media:
                - Title: ${media.title}
                - Duration: ${media.durationMs}ms
                - Hash: ${media.openSubtitlesHash}
                
                Detected Speech Audio Events:
                $speechPreview
                
                Subtitle Cues (Currently desynced):
                $cuesPreview
                
                Calculate:
                1. offset_ms: The exact integer millisecond shift to add to subtitle start times (negative if subtitle is too late and needs to shift left, positive if too early).
                2. drift_factor: The speed scaling ratio (1.000 for no drift, 0.95904 for 25fps->23.976fps drift, 1.0427 for 23.976fps->25fps).
                3. confidence_percent: Confidence rating (0 to 100).
                4. explanation: 1-2 sentence explanation of the detected desync pattern (e.g. constant offset, frame rate mismatch, dialogue lead time).
                
                Return ONLY a valid JSON object matching:
                {
                  "offset_ms": -2500,
                  "drift_factor": 1.0,
                  "confidence_percent": 98,
                  "explanation": "Detected a uniform delay of +2,500ms across dialogue cues with 24fps alignment."
                }
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
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
                return@withContext Result.failure(Exception("Gemini API error ${response.code}: $errorBody"))
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
            val conf = parsedJson.optInt("confidence_percent", 90).toFloat() / 100f
            val explanation = parsedJson.optString("explanation", "AI analyzed speech cadence and dialogue alignment.")

            Result.success(
                SyncResult(
                    detectedOffsetMs = offsetMs,
                    driftFactor = drift,
                    confidenceScore = conf,
                    method = SyncMethod.AI_GEMINI,
                    explanation = explanation,
                    matchedTitle = media.title,
                    cuesAdjusted = subtitle.cueCount
                )
            )
        } catch (e: Exception) {
            Log.e("GeminiSubtitleService", "Failure in AI synchronization", e)
            Result.failure(e)
        }
    }

    /**
     * Refines subtitle text captions using Gemini (fixes capitalization, punctuation, removes hallucinations).
     */
    suspend fun refineSubtitles(cues: List<SubtitleCue>): Result<List<SubtitleCue>> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(IllegalStateException("No valid GEMINI_API_KEY"))
        }

        try {
            val cuesArray = JSONArray()
            cues.take(15).forEach {
                cuesArray.put(JSONObject().apply {
                    put("id", it.id)
                    put("text", it.text)
                })
            }

            val prompt = """
                Refine the following subtitle dialogue lines for cinema caption readability:
                - Fix capitalization and punctuation
                - Clean up transcription errors
                - Keep cue ids exact
                
                Input:
                $cuesArray
                
                Return JSON array of { "id": number, "text": "cleaned text" }.
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
                })
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()
            val root = JSONObject(responseBody)
            val rawJsonText = root.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()

            val array = JSONArray(rawJsonText)
            val updatedMap = mutableMapOf<Int, String>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                updatedMap[item.getInt("id")] = item.getString("text")
            }

            val refined = cues.map { cue ->
                if (updatedMap.containsKey(cue.id)) {
                    cue.copy(text = updatedMap[cue.id]!!)
                } else {
                    cue
                }
            }
            Result.success(refined)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
