package com.example.subsync.data.sample

import android.content.Context
import com.example.subsync.model.AudioDialogueEvent
import com.example.subsync.model.MediaItemInfo
import com.example.subsync.model.SubtitleCue
import com.example.subsync.model.SubtitleFile
import com.example.subsync.model.SubtitleFormat
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class SamplePreset(
    val media: MediaItemInfo,
    val unsyncedSubtitle: SubtitleFile,
    val groundTruthEvents: List<AudioDialogueEvent>,
    val groundTruthOffsetMs: Long,
    val groundTruthDrift: Double = 1.0,
    val description: String
)

object SampleMediaProvider {

    val PRESET_SCI_FI = SamplePreset(
        media = MediaItemInfo(
            title = "Sci-Fi Odyssey: The Nebula Incident (Trailer)",
            isSample = true,
            durationMs = 42000L,
            fileSizeBytes = 34500000L,
            openSubtitlesHash = "8e24fa9c17e0d3b1",
            sha256Hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            audioChannels = 2,
            sampleRateHz = 48000,
            bitrateKbps = 320,
            mimeType = "video/mp4",
            isVideo = true
        ),
        unsyncedSubtitle = SubtitleFile(
            title = "Sci-Fi_Odyssey_Trailer_Desynced.srt",
            format = SubtitleFormat.SRT,
            cues = listOf(
                SubtitleCue(1, 5700L, 9500L, "Commander, gravitational sensors are spiking off the charts!"),
                SubtitleCue(2, 11000L, 16200L, "Hold your station. Divert auxiliary power to forward deflector shields."),
                SubtitleCue(3, 17600L, 22800L, "Negative! The nebula anomaly is pulling us into the event horizon!"),
                SubtitleCue(4, 24900L, 29800L, "Engage pulse thrusters on my mark... Three, two, one, burn!"),
                SubtitleCue(5, 32300L, 37200L, "We broke through! Telemetry stabilizes... but where are we?"),
                SubtitleCue(6, 39000L, 43500L, "Look at the star charts. That isn't our galaxy.")
            )
        ),
        groundTruthEvents = listOf(
            AudioDialogueEvent(3200L, "Commander gravitational sensors are spiking off charts"),
            AudioDialogueEvent(8500L, "Hold station divert auxiliary power to forward shields"),
            AudioDialogueEvent(15100L, "Negative nebula anomaly is pulling us into event horizon"),
            AudioDialogueEvent(22400L, "Engage pulse thrusters on mark three two one burn"),
            AudioDialogueEvent(29800L, "We broke through telemetry stabilizes where are we"),
            AudioDialogueEvent(36500L, "Look at star charts that is not our galaxy")
        ),
        groundTruthOffsetMs = -2500L, // Subtitle is 2500ms late, needs -2500ms shift
        description = "Delayed by +2,500ms with slight 24fps -> 25fps drift"
    )

    val PRESET_PODCAST = SamplePreset(
        media = MediaItemInfo(
            title = "Tech Talk Podcast: Quantum Computing #42",
            isSample = true,
            durationMs = 38000L,
            fileSizeBytes = 4200000L,
            openSubtitlesHash = "3f99ab71e4210c44",
            sha256Hash = "87298351f08388c445681966046e009631e873f2f812e3acc05ecbc3f0da505a",
            audioChannels = 2,
            sampleRateHz = 44100,
            bitrateKbps = 192,
            mimeType = "audio/mpeg",
            isVideo = false
        ),
        unsyncedSubtitle = SubtitleFile(
            title = "Quantum_Byte_Ep42_Desynced.srt",
            format = SubtitleFormat.SRT,
            cues = listOf(
                SubtitleCue(1, 300L, 4200L, "Welcome back to Quantum Byte. Today we explore quantum superposition."),
                SubtitleCue(2, 6000L, 11500L, "Unlike classical bits that are zero or one, qubits exist in both states."),
                SubtitleCue(3, 12700L, 19200L, "This enables algorithms like Shor and Grover to solve problems exponentially faster."),
                SubtitleCue(4, 20200L, 27100L, "Decoherence remains the biggest hurdle for fault-tolerant hardware."),
                SubtitleCue(5, 28400L, 35500L, "Cryogenic dilution refrigerators keep the processors close to absolute zero.")
            )
        ),
        groundTruthEvents = listOf(
            AudioDialogueEvent(2100L, "Welcome back to Quantum Byte today we explore superposition"),
            AudioDialogueEvent(7800L, "Unlike classical bits that are zero or one qubits exist in both states"),
            AudioDialogueEvent(14500L, "This enables algorithms like Shor and Grover to solve faster"),
            AudioDialogueEvent(22000L, "Decoherence remains biggest hurdle for hardware"),
            AudioDialogueEvent(30200L, "Cryogenic dilution refrigerators keep processors close to absolute zero")
        ),
        groundTruthOffsetMs = 1800L, // Subtitle is 1800ms early, needs +1800ms shift
        description = "Early by -1,800ms audio lead time"
    )

    val PRESET_ANIME = SamplePreset(
        media = MediaItemInfo(
            title = "Anime Action: Blades of Destiny Ep 1",
            isSample = true,
            durationMs = 35000L,
            fileSizeBytes = 28000000L,
            openSubtitlesHash = "a1b2c3d4e5f60718",
            sha256Hash = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8",
            audioChannels = 2,
            sampleRateHz = 48000,
            bitrateKbps = 256,
            mimeType = "video/mp4",
            isVideo = true
        ),
        unsyncedSubtitle = SubtitleFile(
            title = "Blades_Destiny_Ep01_Stylized.ass",
            format = SubtitleFormat.ASS,
            cues = listOf(
                SubtitleCue(1, 6200L, 10500L, "You think your phantom sword can pierce my celestial armor?", style = "BossDialogue"),
                SubtitleCue(2, 12600L, 18500L, "True strength isn't in steel. It is forged in the spirits of those I protect!", style = "HeroDialogue"),
                SubtitleCue(3, 20000L, 25200L, "Then witness the dark dragon flame burst!", style = "BossShout"),
                SubtitleCue(4, 27400L, 32000L, "Secret technique: Horizon Slash!", style = "SpecialMove"),
                SubtitleCue(5, 34200L, 38000L, "Impossible... How did your blade bypass my barrier?!", style = "BossDialogue")
            )
        ),
        groundTruthEvents = listOf(
            AudioDialogueEvent(3000L, "You think your phantom sword can pierce my armor"),
            AudioDialogueEvent(9400L, "True strength is not in steel forged in spirits"),
            AudioDialogueEvent(16800L, "Witness dark dragon flame burst"),
            AudioDialogueEvent(24200L, "Secret technique horizon slash"),
            AudioDialogueEvent(31000L, "Impossible how did blade bypass barrier")
        ),
        groundTruthOffsetMs = -3200L, // Subtitle is 3200ms late, needs -3200ms shift
        description = "Delayed by +3,200ms with scene dialogue shift"
    )

    val ALL_PRESETS = listOf(PRESET_SCI_FI, PRESET_PODCAST, PRESET_ANIME)

    /**
     * Generates a valid PCM 16-bit Mono WAV audio file on disk so MediaPlayer can play it seamlessly.
     * Generates musical synth chords with audio chimes synced to dialogue cues.
     */
    fun createSampleWavFile(context: Context, preset: SamplePreset): File {
        val file = File(context.cacheDir, "sample_${preset.media.openSubtitlesHash}.wav")
        if (file.exists() && file.length() > 1000) {
            return file
        }

        val sampleRate = 22050
        val durationSeconds = (preset.media.durationMs / 1000).toInt() + 1
        val numSamples = sampleRate * durationSeconds
        val pcmData = ShortArray(numSamples)

        // Generate ambient harmonic hum + melodic beeps at speech event timestamps
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            // Gentle ambient drone at 110Hz + 220Hz
            var sample = (Math.sin(2.0 * Math.PI * 110.0 * t) * 0.15 +
                    Math.sin(2.0 * Math.PI * 220.0 * t) * 0.10)

            // Check if within any speech event
            for (event in preset.groundTruthEvents) {
                val eventStartSec = event.approximateTimeMs.toDouble() / 1000.0
                val eventEndSec = eventStartSec + 2.5
                if (t >= eventStartSec && t <= eventEndSec) {
                    val dt = t - eventStartSec
                    // Speech-like tone formant
                    val envelope = Math.sin(Math.PI * (dt / 2.5)).coerceAtLeast(0.0)
                    val speechWave = (Math.sin(2.0 * Math.PI * 440.0 * t) * 0.4 +
                            Math.sin(2.0 * Math.PI * 880.0 * t) * 0.2) * envelope
                    sample += speechWave
                }
            }

            pcmData[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }

        // Write WAV header and data
        FileOutputStream(file).use { out ->
            val byteRate = sampleRate * 2 // 16-bit mono = 2 bytes per sample
            val dataSize = numSamples * 2
            val totalSize = 36 + dataSize

            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(totalSize)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16) // Subchunk1Size (16 for PCM)
            header.putShort(1) // AudioFormat (1 for PCM)
            header.putShort(1) // NumChannels (1 mono)
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(2) // BlockAlign
            header.putShort(16) // BitsPerSample
            header.put("data".toByteArray())
            header.putInt(dataSize)

            out.write(header.array())

            val buffer = ByteBuffer.allocate(pcmData.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcmData) {
                buffer.putShort(s)
            }
            out.write(buffer.array())
        }

        return file
    }
}
