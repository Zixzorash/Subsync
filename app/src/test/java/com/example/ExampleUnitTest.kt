package com.example

import com.example.subsync.data.online.OnlineSubtitleService
import com.example.subsync.model.AudioDialogueEvent
import com.example.subsync.model.HashMatchType
import com.example.subsync.model.MediaItemInfo
import com.example.subsync.model.SubtitleCue
import com.example.subsync.model.SubtitleFormat
import com.example.subsync.util.FuzzyMatcher
import com.example.subsync.util.OpenSubtitlesHasher
import com.example.subsync.util.SubtitleParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testSubtitleParsingAndShift() {
        val rawSrt = """
            1
            00:00:02,000 --> 00:00:05,000
            Hello world!
            
            2
            00:00:06,500 --> 00:00:09,000
            Second dialogue line.
        """.trimIndent()

        val parsed = SubtitleParser.parse(rawSrt, "Test")
        assertEquals(2, parsed.cueCount)
        assertEquals(SubtitleFormat.SRT, parsed.format)
        assertEquals(2000L, parsed.cues[0].startTimeMs)
        assertEquals(5000L, parsed.cues[0].endTimeMs)
        assertEquals("Hello world!", parsed.cues[0].text)

        // Test offset shift (+1000ms)
        val shifted = SubtitleParser.shiftTimestamps(parsed.cues, 1000L)
        assertEquals(3000L, shifted[0].startTimeMs)
        assertEquals(6000L, shifted[0].endTimeMs)
        assertEquals(7500L, shifted[1].startTimeMs)
    }

    @Test
    fun testAssSubtitleParsingAndSerialization() {
        val rawAss = """
            [Script Info]
            Title: Anime Ep 1
            ScriptType: v4.00+
            
            [V4+ Styles]
            Format: Name, Fontname, Fontsize
            Style: Default,Arial,52
            
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:03.20,0:00:06.50,Default,,0,0,0,,{\b1}Target locked!{\b0}\NFire lasers!
            Dialogue: 0,0:00:08.00,0:00:11.45,SpecialMove,,0,0,0,,{\c&H00FFFF&}Horizon Slash!{\c}
        """.trimIndent()

        val parsed = SubtitleParser.parse(rawAss, "Anime Ep 1")
        assertEquals(SubtitleFormat.ASS, parsed.format)
        assertEquals(2, parsed.cueCount)
        assertEquals(3200L, parsed.cues[0].startTimeMs)
        assertEquals(6500L, parsed.cues[0].endTimeMs)
        assertEquals("Target locked!\nFire lasers!", parsed.cues[0].text)
        assertEquals("SpecialMove", parsed.cues[1].style)

        // Test serialize back to ASS
        val serializedAss = SubtitleParser.serialize(parsed, SubtitleFormat.ASS)
        assertTrue(serializedAss.contains("[Script Info]"))
        assertTrue(serializedAss.contains("[Events]"))
        assertTrue(serializedAss.contains("Dialogue: 0,"))
        assertTrue(serializedAss.contains("0:00:03.20"))

        // Test conversion from ASS to SRT
        val convertedSrt = SubtitleParser.serialize(parsed, SubtitleFormat.SRT)
        assertTrue(convertedSrt.contains("00:00:03,200 --> 00:00:06,500"))
        assertTrue(convertedSrt.contains("Target locked!"))
    }

    @Test
    fun testFormatDetection() {
        val srtText = "1\n00:00:01,000 --> 00:00:02,000\nHi"
        val vttText = "WEBVTT\n\n1\n00:00:01.000 --> 00:00:02.000\nHi"
        val assText = "[Events]\nFormat: Layer, Start, End, Text\nDialogue: 0,0:00:01.00,0:00:02.00,Hi"

        assertEquals(SubtitleFormat.SRT, SubtitleParser.detectFormat(srtText))
        assertEquals(SubtitleFormat.VTT, SubtitleParser.detectFormat(vttText))
        assertEquals(SubtitleFormat.ASS, SubtitleParser.detectFormat(assText))
    }

    @Test
    fun testOnlineSubtitleServiceSearch() = runBlocking {
        val service = OnlineSubtitleService()
        val media = MediaItemInfo(
            title = "Sci-Fi Odyssey: The Nebula Incident (Trailer)",
            openSubtitlesHash = "8e24fa9c17e0d3b1",
            durationMs = 42000L
        )

        val results = service.searchSubtitles(media)
        assertTrue("Should return matches for known MovieHash", results.isNotEmpty())

        val exactMatch = results.firstOrNull { it.matchType == HashMatchType.EXACT_MOVIE_HASH }
        assertNotNull("Should find an exact MovieHash match", exactMatch)
        assertTrue(exactMatch!!.downloadableContent.isNotEmpty())

        // Test filtering by ASS format
        val assMatches = service.searchSubtitles(media, formatFilter = SubtitleFormat.ASS)
        assertTrue("Should return ASS matches when filtered", assMatches.any { it.format == SubtitleFormat.ASS })
    }

    @Test
    fun testFuzzyMatchingAndTokenSortRatio() {
        val s1 = "Commander gravitational sensors are spiking"
        val s2 = "Commander, gravitational sensors are spiking off the charts!"
        val sim = FuzzyMatcher.similarity(s1, s2)
        assertTrue("Similarity should be high for matching phrases", sim > 0.6f)

        val reordered1 = "pulse thrusters on my mark"
        val reordered2 = "on my mark pulse thrusters"
        val tokenSim = FuzzyMatcher.tokenSortRatio(reordered1, reordered2)
        assertTrue("Token sort ratio should handle reordered words", tokenSim >= 0.95f)
    }

    @Test
    fun testFuzzyAlignmentOffset() {
        val cues = listOf(
            SubtitleCue(1, 5000L, 8000L, "Divert auxiliary power to shields"),
            SubtitleCue(2, 10000L, 14000L, "Engage thrusters burn")
        )
        val speechEvents = listOf(
            AudioDialogueEvent(3000L, "Divert auxiliary power to shields"),
            AudioDialogueEvent(8000L, "Engage thrusters burn")
        )

        val (offset, confidence) = FuzzyMatcher.computeFuzzyAlignmentOffset(cues, speechEvents)
        assertEquals(-2000L, offset)
        assertTrue(confidence > 0.5f)
    }

    @Test
    fun testOpenSubtitlesChecksum() {
        val testBytes = ByteArray(100) { (it % 10).toByte() }
        val hash = OpenSubtitlesHasher.computeBytesHash(testBytes, testBytes.size.toLong())
        assertEquals(16, hash.length)
    }
}
