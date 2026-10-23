package com.example.subsync.util

import com.example.subsync.model.AdjustedCueDetail
import com.example.subsync.model.AudioDialogueEvent
import com.example.subsync.model.SubtitleCue
import kotlin.math.abs

data class SelectiveSyncOutput(
    val synchronizedCues: List<SubtitleCue>,
    val totalCues: Int,
    val adjustedCount: Int,
    val unchangedCount: Int,
    val adjustedDetails: List<AdjustedCueDetail>,
    val averageDeltaMs: Long
)

object SelectiveCueSyncEngine {

    /**
     * Synchronizes ONLY the cues that are out of sync with the audio speech stream,
     * leaving cues that already match voice activity completely untouched.
     *
     * @param cues All subtitle cues (e.g. 2,400 cues across 2 hours)
     * @param speechEvents Detected audio voice activity events across the timeline
     * @param baselineOffsetMs Global reference offset detected by Gemini AI or Hash
     * @param driftFactor Progressive frame rate drift multiplier
     * @param toleranceMs Threshold within which a cue is considered already synchronized (default 180ms)
     */
    fun synchronizeSelective(
        cues: List<SubtitleCue>,
        speechEvents: List<AudioDialogueEvent>,
        baselineOffsetMs: Long = 0L,
        driftFactor: Double = 1.0,
        toleranceMs: Long = 180L
    ): SelectiveSyncOutput {
        if (cues.isEmpty()) {
            return SelectiveSyncOutput(emptyList(), 0, 0, 0, emptyList(), 0L)
        }

        val speechTimes = speechEvents.map { it.approximateTimeMs }.sorted()
        val adjustedDetails = mutableListOf<AdjustedCueDetail>()
        val resultCues = mutableListOf<SubtitleCue>()
        var sumDeltas = 0L

        for (cue in cues) {
            val originalStart = cue.startTimeMs
            val originalEnd = cue.endTimeMs
            val phonemeEstimate = AcousticAlignmentEngine.estimateSyllablesAndPhonemes(cue.text)
            val duration = if (originalEnd <= originalStart) {
                phonemeEstimate.expectedDurationMs
            } else {
                originalEnd - originalStart
            }

            // Find nearest speech event to original timestamp
            val nearestSpeech = findNearestSpeechTime(originalStart, speechTimes)

            val currentError = if (nearestSpeech != null) abs(originalStart - nearestSpeech) else Long.MAX_VALUE

            // 1. Check if the cue is ALREADY in sync with the audio voice and not inverted
            if (currentError <= toleranceMs && originalEnd > originalStart) {
                // Cue is already aligned with voice activity! Keep untouched.
                resultCues.add(cue)
                continue
            }

            // 2. Cue is OUT OF SYNC. Calculate targeted calibration.
            // Check if baseline offset aligns it with audio:
            val candidateStart = if (baselineOffsetMs != 0L) {
                val shifted = (originalStart + baselineOffsetMs).coerceAtLeast(0L)
                val shiftedNear = findNearestSpeechTime(shifted, speechTimes)
                if (shiftedNear != null && abs(shifted - shiftedNear) <= 300L) {
                    shiftedNear
                } else {
                    shifted
                }
            } else if (nearestSpeech != null && abs(nearestSpeech - originalStart) <= 12000L) {
                nearestSpeech
            } else {
                originalStart
            }

            val delta = candidateStart - originalStart

            if (abs(delta) > 50L) { // Meaningful adjustment threshold
                val newStart = candidateStart.coerceAtLeast(0L)
                val newEnd = (newStart + duration).coerceAtLeast(newStart + 200L)

                val reason = when {
                    abs(delta) > 1000L -> "Misaligned dialogue start (Δ ${delta}ms)"
                    driftFactor != 1.0 -> "Drift compensation"
                    else -> "Voice onset timing adjustment"
                }

                adjustedDetails.add(
                    AdjustedCueDetail(
                        cueId = cue.id,
                        originalStartMs = originalStart,
                        originalEndMs = originalEnd,
                        adjustedStartMs = newStart,
                        adjustedEndMs = newEnd,
                        deltaMs = delta,
                        text = cue.text,
                        reason = reason
                    )
                )

                sumDeltas += abs(delta)
                resultCues.add(cue.copy(startTimeMs = newStart, endTimeMs = newEnd))
            } else {
                // Minor delta within noise margin: keep unchanged
                resultCues.add(cue)
            }
        }

        // Clean up any overlaps introduced by selective shifts
        val finalizedCues = SubtitleParser.fixOverlaps(resultCues, minGapMs = 40L)
        val adjustedCount = adjustedDetails.size
        val unchangedCount = cues.size - adjustedCount
        val avgDelta = if (adjustedCount > 0) sumDeltas / adjustedCount else 0L

        return SelectiveSyncOutput(
            synchronizedCues = finalizedCues,
            totalCues = cues.size,
            adjustedCount = adjustedCount,
            unchangedCount = unchangedCount,
            adjustedDetails = adjustedDetails,
            averageDeltaMs = avgDelta
        )
    }

    private fun findNearestSpeechTime(targetMs: Long, speechTimes: List<Long>): Long? {
        if (speechTimes.isEmpty()) return null
        val idx = speechTimes.binarySearch(targetMs)
        if (idx >= 0) return speechTimes[idx]

        val insertionPoint = -(idx + 1)
        val prev = if (insertionPoint > 0) speechTimes[insertionPoint - 1] else null
        val next = if (insertionPoint < speechTimes.size) speechTimes[insertionPoint] else null

        return when {
            prev == null -> next
            next == null -> prev
            abs(targetMs - prev) <= abs(targetMs - next) -> prev
            else -> next
        }
    }
}
