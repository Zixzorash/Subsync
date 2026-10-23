package com.example.subsync.util

import com.example.subsync.model.AudioDialogueEvent
import com.example.subsync.model.SubtitleCue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object FuzzyMatcher {

    /**
     * Normalized Levenshtein distance similarity between 0.0 and 1.0.
     */
    fun similarity(s1: String, s2: String): Float {
        val str1 = cleanString(s1)
        val str2 = cleanString(s2)
        if (str1.isEmpty() && str2.isEmpty()) return 1.0f
        if (str1.isEmpty() || str2.isEmpty()) return 0.0f
        if (str1 == str2) return 1.0f

        val dist = levenshteinDistance(str1, str2)
        val maxLen = max(str1.length, str2.length)
        return (1.0f - (dist.toFloat() / maxLen.toFloat())).coerceIn(0.0f, 1.0f)
    }

    /**
     * Token Sort Ratio: splits strings into words, sorts them alphabetically,
     * and computes similarity. Handles reordered phrases and minor omissions.
     */
    fun tokenSortRatio(s1: String, s2: String): Float {
        val tokens1 = cleanString(s1).split(" ").filter { it.isNotBlank() }.sorted().joinToString(" ")
        val tokens2 = cleanString(s2).split(" ").filter { it.isNotBlank() }.sorted().joinToString(" ")
        return similarity(tokens1, tokens2)
    }

    private fun cleanString(input: String): String {
        return input.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        val len0 = lhs.length + 1
        val len1 = rhs.length + 1
        var cost = IntArray(len0)
        var newcost = IntArray(len0)

        for (i in 0 until len0) cost[i] = i

        for (j in 1 until len1) {
            newcost[0] = j
            for (i in 1 until len0) {
                val match = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
                val costReplace = cost[i - 1] + match
                val costInsert = cost[i] + 1
                val costDelete = newcost[i - 1] + 1
                newcost[i] = min(min(costInsert, costDelete), costReplace)
            }
            val swap = cost
            cost = newcost
            newcost = swap
        }
        return cost[len0 - 1]
    }

    /**
     * Matches subtitle cues against audio speech events using fuzzy alignment.
     * Evaluates candidate offset shifts from -15,000ms to +15,000ms to find the peak consensus offset.
     */
    fun computeFuzzyAlignmentOffset(
        cues: List<SubtitleCue>,
        speechEvents: List<AudioDialogueEvent>
    ): Pair<Long, Float> {
        if (cues.isEmpty() || speechEvents.isEmpty()) {
            return Pair(0L, 0.0f)
        }

        // For each speech event, find the best matching subtitle cue and compute delta
        val candidateDeltas = mutableListOf<Pair<Long, Float>>() // Pair<deltaMs, matchWeight>

        for (event in speechEvents) {
            var bestCue: SubtitleCue? = null
            var bestScore = 0.0f

            for (cue in cues) {
                val sim = tokenSortRatio(event.recognizedPhrase, cue.text)
                // We prioritize text similarity; time window up to +/- 20s
                val rawDelta = event.approximateTimeMs - cue.startTimeMs
                if (abs(rawDelta) < 25000L && sim > 0.45f) {
                    if (sim > bestScore) {
                        bestScore = sim
                        bestCue = cue
                    }
                }
            }

            if (bestCue != null && bestScore >= 0.5f) {
                val delta = event.approximateTimeMs - bestCue.startTimeMs
                candidateDeltas.add(Pair(delta, bestScore))
            }
        }

        if (candidateDeltas.isEmpty()) {
            return Pair(0L, 0.2f)
        }

        // Cluster deltas into bins of 200ms
        val binSizeMs = 250L
        val bins = mutableMapOf<Long, Float>()

        for ((delta, weight) in candidateDeltas) {
            val binKey = (delta / binSizeMs) * binSizeMs
            bins[binKey] = (bins[binKey] ?: 0f) + weight
        }

        val bestBin = bins.maxByOrNull { it.value } ?: return Pair(0L, 0.1f)
        val winningBinKey = bestBin.key

        // Calculate weighted average around the winning bin
        val inliers = candidateDeltas.filter { abs(it.first - winningBinKey) <= binSizeMs * 2 }
        val weightedSum = inliers.sumOf { (it.first * it.second).toDouble() }
        val totalWeight = inliers.sumOf { it.second.toDouble() }

        val optimalOffset = if (totalWeight > 0.0) (weightedSum / totalWeight).toLong() else winningBinKey
        val confidence = min(1.0f, (inliers.size.toFloat() / max(1, speechEvents.size)) * 0.9f + 0.1f)

        return Pair(optimalOffset, confidence)
    }
}
