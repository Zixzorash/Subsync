package com.example.subsync.model

import java.util.Locale

/**
 * Represents a single subtitle caption entry.
 */
data class SubtitleCue(
    val id: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
    val style: String = "Default"
) {
    val durationMs: Long get() = endTimeMs - startTimeMs

    fun formatStartTime(): String = formatTimestamp(startTimeMs)
    fun formatEndTime(): String = formatTimestamp(endTimeMs)

    companion object {
        fun formatTimestamp(ms: Long): String {
            val totalSeconds = ms / 1000
            val millis = ms % 1000
            val seconds = totalSeconds % 60
            val minutes = (totalSeconds / 60) % 60
            val hours = totalSeconds / 3600
            return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
        }
    }
}

enum class SubtitleFormat(val extension: String, val displayName: String) {
    SRT(".srt", "SubRip (.srt)"),
    VTT(".vtt", "WebVTT (.vtt)"),
    ASS(".ass", "Advanced SubStation Alpha (.ass)")
}

data class SubtitleFile(
    val title: String,
    val cues: List<SubtitleCue>,
    val format: SubtitleFormat = SubtitleFormat.SRT,
    val language: String = "English",
    val appliedOffsetMs: Long = 0L,
    val frameRate: Double = 23.976,
    val rawHeader: String? = null
) {
    val durationMs: Long get() = cues.maxOfOrNull { it.endTimeMs } ?: 0L
    val cueCount: Int get() = cues.size
}

/**
 * Information extracted from an MP3, MP4, or video file.
 */
data class MediaItemInfo(
    val title: String,
    val uriString: String? = null,
    val isSample: Boolean = false,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val openSubtitlesHash: String = "",
    val sha256Hash: String = "",
    val audioChannels: Int = 2,
    val sampleRateHz: Int = 44100,
    val bitrateKbps: Int = 192,
    val mimeType: String = "audio/mpeg",
    val isVideo: Boolean = false
) {
    val formattedDuration: String
        get() {
            val totalSec = durationMs / 1000
            val hr = totalSec / 3600
            val min = (totalSec % 3600) / 60
            val sec = totalSec % 60
            return if (hr > 0) {
                String.format(Locale.US, "%02d:%02d:%02d", hr, min, sec)
            } else {
                String.format(Locale.US, "%02d:%02d", min, sec)
            }
        }

    val formattedSize: String
        get() {
            val mb = fileSizeBytes.toDouble() / (1024 * 1024)
            return String.format(Locale.US, "%.1f MB", mb)
        }
}

data class AdjustedCueDetail(
    val cueId: Int,
    val originalStartMs: Long,
    val originalEndMs: Long,
    val adjustedStartMs: Long,
    val adjustedEndMs: Long,
    val deltaMs: Long,
    val text: String,
    val reason: String
) {
    fun formatOriginalStart(): String = SubtitleCue.formatTimestamp(originalStartMs)
    fun formatAdjustedStart(): String = SubtitleCue.formatTimestamp(adjustedStartMs)
}

enum class SyncMethod(val displayName: String) {
    AUDIO_HASH("MovieHash Registry"),
    FUZZY_SPEECH("Fuzzy Speech Match"),
    AI_GEMINI("Gemini AI Auto-Sync"),
    SELECTIVE_CUE("Selective AI Alignment"),
    ONLINE_DATABASE("Online Database Sync"),
    MANUAL("Manual Calibration")
}

data class SyncResult(
    val detectedOffsetMs: Long,
    val driftFactor: Double = 1.0,
    val confidenceScore: Float,
    val method: SyncMethod,
    val explanation: String,
    val matchedTitle: String? = null,
    val totalCues: Int = 0,
    val cuesAdjusted: Int = 0,
    val adjustedCueDetails: List<AdjustedCueDetail> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class AudioDialogueEvent(
    val approximateTimeMs: Long,
    val recognizedPhrase: String,
    val audioEnergy: Float = 0.8f
)

enum class OnlineSubtitleSource(val displayName: String, val badgeColorHex: Long) {
    OPEN_SUBTITLES("OpenSubtitles.org", 0xFF00E5FF),
    SUBSCENE("Subscene", 0xFF818CF8),
    SUBTITLE_CAT("SubtitleCat", 0xFF10B981)
}

enum class HashMatchType(val label: String) {
    EXACT_MOVIE_HASH("MovieHash 100% Match"),
    EXACT_SHA256("SHA-256 Verified"),
    FUZZY_FILENAME("Filename & Release Match")
}

data class OnlineSubtitleMatch(
    val id: String,
    val source: OnlineSubtitleSource,
    val releaseTitle: String,
    val language: String,
    val format: SubtitleFormat,
    val rating: Float,
    val downloadCount: Int,
    val matchType: HashMatchType,
    val uploader: String = "Anonymous",
    val contentSnippet: String,
    val downloadableContent: String,
    val recommendedOffsetMs: Long = 0L,
    val fps: Double = 23.976
)
