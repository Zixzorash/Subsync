package com.example.subsync.util

import com.example.subsync.model.SubtitleCue
import com.example.subsync.model.SubtitleFile
import com.example.subsync.model.SubtitleFormat
import java.util.Locale
import java.util.regex.Pattern

object SubtitleParser {

    private val TIME_SRT_PATTERN = Pattern.compile(
        "(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})\\s*-->\\s*(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})"
    )

    // ASS timestamp pattern: e.g. 0:01:23.45 or 00:01:23.45
    private val TIME_ASS_PATTERN = Pattern.compile(
        "(\\d{1,2}):(\\d{2}):(\\d{2})\\.(\\d{1,3})"
    )

    fun detectFormat(rawContent: String): SubtitleFormat {
        val trimmed = rawContent.trimStart()
        return when {
            trimmed.contains("[Events]") || trimmed.contains("[Script Info]") || trimmed.startsWith("Dialogue:") -> {
                SubtitleFormat.ASS
            }
            trimmed.startsWith("WEBVTT") -> {
                SubtitleFormat.VTT
            }
            else -> SubtitleFormat.SRT
        }
    }

    fun parse(rawContent: String, title: String = "Untitled Subtitle"): SubtitleFile {
        val cleanContent = rawContent.replace("\r\n", "\n").replace("\r", "\n")
        val format = detectFormat(cleanContent)

        return when (format) {
            SubtitleFormat.ASS -> parseAss(cleanContent, title)
            SubtitleFormat.VTT -> parseSrtOrVtt(cleanContent, title, isVtt = true)
            SubtitleFormat.SRT -> parseSrtOrVtt(cleanContent, title, isVtt = false)
        }
    }

    private fun parseSrtOrVtt(cleanContent: String, title: String, isVtt: Boolean): SubtitleFile {
        val cues = mutableListOf<SubtitleCue>()
        val blocks = cleanContent.split("\n\n")
        var autoId = 1

        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue

            // Find line with -->
            val timeLineIndex = lines.indexOfFirst { it.contains("-->") }
            if (timeLineIndex == -1) continue

            val timeLine = lines[timeLineIndex]
            val matcher = TIME_SRT_PATTERN.matcher(timeLine)
            if (matcher.find()) {
                val startH = matcher.group(1)?.toLongOrNull() ?: 0L
                val startM = matcher.group(2)?.toLongOrNull() ?: 0L
                val startS = matcher.group(3)?.toLongOrNull() ?: 0L
                val startMs = matcher.group(4)?.toLongOrNull() ?: 0L

                val endH = matcher.group(5)?.toLongOrNull() ?: 0L
                val endM = matcher.group(6)?.toLongOrNull() ?: 0L
                val endS = matcher.group(7)?.toLongOrNull() ?: 0L
                val endMs = matcher.group(8)?.toLongOrNull() ?: 0L

                val startTime = (startH * 3600 + startM * 60 + startS) * 1000 + startMs
                val endTime = (endH * 3600 + endM * 60 + endS) * 1000 + endMs

                val textLines = lines.subList(timeLineIndex + 1, lines.size)
                val text = textLines.joinToString("\n").replace(Regex("<[^>]*>"), "")

                val cueId = if (timeLineIndex > 0) {
                    lines[0].toIntOrNull() ?: autoId
                } else {
                    autoId
                }

                cues.add(SubtitleCue(id = cueId, startTimeMs = startTime, endTimeMs = endTime, text = text))
                autoId++
            }
        }

        return SubtitleFile(
            title = title,
            cues = cues.sortedBy { it.startTimeMs },
            format = if (isVtt) SubtitleFormat.VTT else SubtitleFormat.SRT
        )
    }

    /**
     * Parses Advanced SubStation Alpha (.ass / .ssa) files.
     */
    private fun parseAss(cleanContent: String, title: String): SubtitleFile {
        val cues = mutableListOf<SubtitleCue>()
        val lines = cleanContent.lines()
        var insideEvents = false
        var formatFields: List<String> = listOf("layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text")
        var autoId = 1

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[Events]", ignoreCase = true)) {
                insideEvents = true
                continue
            }
            if (insideEvents && trimmed.startsWith("[")) {
                insideEvents = false
                continue
            }

            if (insideEvents) {
                if (trimmed.startsWith("Format:", ignoreCase = true)) {
                    val rawFormat = trimmed.substringAfter("Format:").trim()
                    formatFields = rawFormat.split(",").map { it.trim().lowercase() }
                    continue
                }

                if (trimmed.startsWith("Dialogue:", ignoreCase = true)) {
                    val rawData = trimmed.substringAfter("Dialogue:").trim()
                    // Split by comma up to formatFields.size - 1 times so Text retains its commas
                    val limit = formatFields.size
                    val parts = rawData.split(",", limit = limit).map { it.trim() }

                    val startIndex = formatFields.indexOf("start")
                    val endIndex = formatFields.indexOf("end")
                    val textIndex = formatFields.indexOf("text")
                    val styleIndex = formatFields.indexOf("style")

                    if (startIndex != -1 && endIndex != -1 && textIndex != -1 && parts.size > textIndex) {
                        val startTimeMs = parseAssTimestamp(parts[startIndex])
                        val endTimeMs = parseAssTimestamp(parts[endIndex])
                        val rawText = parts[textIndex]
                        val style = if (styleIndex != -1 && parts.size > styleIndex) parts[styleIndex] else "Default"

                        // Clean ASS tags: {\an8}, {\b1}, \N -> newline
                        val cleanText = rawText
                            .replace(Regex("\\{[^}]*\\}"), "")
                            .replace("\\N", "\n")
                            .replace("\\n", "\n")
                            .trim()

                        if (cleanText.isNotBlank()) {
                            cues.add(
                                SubtitleCue(
                                    id = autoId,
                                    startTimeMs = startTimeMs,
                                    endTimeMs = endTimeMs,
                                    text = cleanText,
                                    style = style
                                )
                            )
                            autoId++
                        }
                    }
                }
            }
        }

        return SubtitleFile(
            title = title,
            cues = cues.sortedBy { it.startTimeMs },
            format = SubtitleFormat.ASS
        )
    }

    private fun parseAssTimestamp(ts: String): Long {
        val matcher = TIME_ASS_PATTERN.matcher(ts.trim())
        if (matcher.find()) {
            val h = matcher.group(1)?.toLongOrNull() ?: 0L
            val m = matcher.group(2)?.toLongOrNull() ?: 0L
            val s = matcher.group(3)?.toLongOrNull() ?: 0L
            val centiStr = matcher.group(4) ?: "0"
            // Centiseconds (2 digits) -> millis (*10), or milliseconds (3 digits)
            val ms = when (centiStr.length) {
                1 -> (centiStr.toLongOrNull() ?: 0L) * 100
                2 -> (centiStr.toLongOrNull() ?: 0L) * 10
                else -> centiStr.take(3).toLongOrNull() ?: 0L
            }
            return (h * 3600 + m * 60 + s) * 1000 + ms
        }
        return 0L
    }

    fun serialize(subtitleFile: SubtitleFile, format: SubtitleFormat = subtitleFile.format): String {
        return when (format) {
            SubtitleFormat.SRT -> serializeSrt(subtitleFile)
            SubtitleFormat.VTT -> serializeVtt(subtitleFile)
            SubtitleFormat.ASS -> serializeAss(subtitleFile)
        }
    }

    private fun serializeSrt(file: SubtitleFile): String {
        val sb = StringBuilder()
        file.cues.forEachIndexed { index, cue ->
            sb.append(index + 1).append("\n")
            sb.append(formatSrtTime(cue.startTimeMs)).append(" --> ").append(formatSrtTime(cue.endTimeMs)).append("\n")
            sb.append(cue.text).append("\n\n")
        }
        return sb.toString().trimEnd() + "\n"
    }

    private fun serializeVtt(file: SubtitleFile): String {
        val sb = StringBuilder()
        sb.append("WEBVTT - Converted with SubSync AI\n\n")
        file.cues.forEachIndexed { index, cue ->
            sb.append(index + 1).append("\n")
            sb.append(formatVttTime(cue.startTimeMs)).append(" --> ").append(formatVttTime(cue.endTimeMs)).append("\n")
            sb.append(cue.text).append("\n\n")
        }
        return sb.toString().trimEnd() + "\n"
    }

    private fun serializeAss(file: SubtitleFile): String {
        val sb = StringBuilder()
        sb.append("[Script Info]\n")
        sb.append("; Script generated by SubSync AI\n")
        sb.append("Title: ").append(file.title).append("\n")
        sb.append("ScriptType: v4.00+\n")
        sb.append("WrapStyle: 0\n")
        sb.append("ScaledBorderAndShadow: yes\n")
        sb.append("PlayResX: 1920\n")
        sb.append("PlayResY: 1080\n\n")

        sb.append("[V4+ Styles]\n")
        sb.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n")
        sb.append("Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,-1,0,0,0,100,100,0,0,1,2,1,2,10,10,20,1\n\n")

        sb.append("[Events]\n")
        sb.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")
        file.cues.forEach { cue ->
            val formattedStart = formatAssTime(cue.startTimeMs)
            val formattedEnd = formatAssTime(cue.endTimeMs)
            val formattedText = cue.text.replace("\n", "\\N")
            sb.append("Dialogue: 0,")
                .append(formattedStart).append(",")
                .append(formattedEnd).append(",")
                .append(cue.style.ifBlank { "Default" }).append(",,0,0,0,,")
                .append(formattedText).append("\n")
        }
        return sb.toString()
    }

    fun shiftTimestamps(cues: List<SubtitleCue>, offsetMs: Long): List<SubtitleCue> {
        return cues.map { cue ->
            val duration = cue.endTimeMs - cue.startTimeMs
            val newStart = (cue.startTimeMs + offsetMs).coerceAtLeast(0L)
            val newEnd = (newStart + duration).coerceAtLeast(newStart + 200L)
            cue.copy(startTimeMs = newStart, endTimeMs = newEnd)
        }
    }

    fun scaleSpeed(cues: List<SubtitleCue>, factor: Double, anchorMs: Long = 0L): List<SubtitleCue> {
        if (factor <= 0.0) return cues
        return cues.map { cue ->
            val relStart = (cue.startTimeMs - anchorMs) * factor
            val relEnd = (cue.endTimeMs - anchorMs) * factor
            val newStart = (anchorMs + relStart).toLong().coerceAtLeast(0L)
            val newEnd = (anchorMs + relEnd).toLong().coerceAtLeast(newStart + 200L)
            cue.copy(startTimeMs = newStart, endTimeMs = newEnd)
        }
    }

    data class TimingRepairStats(
        val invertedFixed: Int,
        val overlapsResolved: Int,
        val clampedNegative: Int
    )

    fun repairTimingErrors(cues: List<SubtitleCue>, minGapMs: Long = 50L): Pair<List<SubtitleCue>, TimingRepairStats> {
        var invertedCount = 0
        var clampedCount = 0
        var overlapCount = 0

        // 1. Sanitize individual cues: clamp negative starts and repair inverted end times
        val sanitized = cues.map { cue ->
            var start = cue.startTimeMs
            var end = cue.endTimeMs

            if (start < 0L) {
                start = 0L
                clampedCount++
            }

            if (end <= start) {
                invertedCount++
                val phonemeEst = AcousticAlignmentEngine.estimateSyllablesAndPhonemes(cue.text)
                end = start + phonemeEst.expectedDurationMs
            }

            if (start != cue.startTimeMs || end != cue.endTimeMs) {
                cue.copy(startTimeMs = start, endTimeMs = end)
            } else {
                cue
            }
        }.sortedBy { it.startTimeMs }

        // 2. Resolve sequential overlaps
        val result = mutableListOf<SubtitleCue>()
        for (i in sanitized.indices) {
            val current = sanitized[i]
            if (i > 0) {
                val prev = result[i - 1]
                if (prev.endTimeMs + minGapMs > current.startTimeMs) {
                    overlapCount++
                    val adjustedPrevEnd = (current.startTimeMs - minGapMs).coerceAtLeast(prev.startTimeMs + 200L)
                    result[i - 1] = prev.copy(endTimeMs = adjustedPrevEnd)
                }
            }
            result.add(current)
        }

        return Pair(result, TimingRepairStats(invertedCount, overlapCount, clampedCount))
    }

    fun fixOverlaps(cues: List<SubtitleCue>, minGapMs: Long = 50L): List<SubtitleCue> {
        return repairTimingErrors(cues, minGapMs).first
    }

    fun formatSrtTime(ms: Long): String {
        val totalSec = ms / 1000
        val millis = ms % 1000
        val sec = totalSec % 60
        val min = (totalSec / 60) % 60
        val hr = totalSec / 3600
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hr, min, sec, millis)
    }

    fun formatVttTime(ms: Long): String {
        val totalSec = ms / 1000
        val millis = ms % 1000
        val sec = totalSec % 60
        val min = (totalSec / 60) % 60
        val hr = totalSec / 3600
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", hr, min, sec, millis)
    }

    fun formatAssTime(ms: Long): String {
        val totalSec = ms / 1000
        val centis = (ms % 1000) / 10
        val sec = totalSec % 60
        val min = (totalSec / 60) % 60
        val hr = totalSec / 3600
        return String.format(Locale.US, "%d:%02d:%02d.%02d", hr, min, sec, centis)
    }
}
