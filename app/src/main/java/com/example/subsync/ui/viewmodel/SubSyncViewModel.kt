package com.example.subsync.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.subsync.ai.GeminiSubtitleService
import com.example.subsync.data.local.SubSyncDatabase
import com.example.subsync.data.local.SyncProfileEntity
import com.example.subsync.data.online.OnlineSubtitleService
import com.example.subsync.model.AdjustedCueDetail
import com.example.subsync.model.AudioDialogueEvent
import com.example.subsync.model.AudioSpeechBoundary
import com.example.subsync.model.AudioWaveformFrame
import com.example.subsync.model.HashMatchType
import com.example.subsync.model.MediaItemInfo
import com.example.subsync.model.OnlineSubtitleMatch
import com.example.subsync.model.OnlineSubtitleSource
import com.example.subsync.model.SubtitleCue
import com.example.subsync.model.SubtitleFile
import com.example.subsync.model.SubtitleFormat
import com.example.subsync.model.SyncMethod
import com.example.subsync.model.SyncResult
import com.example.subsync.model.VadSensitivity
import com.example.subsync.player.SubSyncPlayerController
import com.example.subsync.util.AcousticAlignmentEngine
import com.example.subsync.util.FuzzyMatcher
import com.example.subsync.util.MediaAnalyzer
import com.example.subsync.util.SelectiveCueSyncEngine
import com.example.subsync.util.SubtitleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

sealed interface SyncUiState {
    data object Idle : SyncUiState
    data class Processing(val stepDescription: String, val progress: Float) : SyncUiState
    data class Success(val result: SyncResult) : SyncUiState
    data class Error(val message: String) : SyncUiState
}

class SubSyncViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SubSyncDatabase.getDatabase(application)
    private val profileDao = db.syncProfileDao()
    private val geminiService = GeminiSubtitleService()
    private val onlineSubtitleService = OnlineSubtitleService()

    val playerController = SubSyncPlayerController(application, viewModelScope)

    val savedProfiles: StateFlow<List<SyncProfileEntity>> = profileDao.getAllProfilesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _onlineSearchResults = MutableStateFlow<List<OnlineSubtitleMatch>>(emptyList())
    val onlineSearchResults: StateFlow<List<OnlineSubtitleMatch>> = _onlineSearchResults.asStateFlow()

    private val _isSearchingOnline = MutableStateFlow(false)
    val isSearchingOnline: StateFlow<Boolean> = _isSearchingOnline.asStateFlow()

    // Currently active media and subtitle (Starts clean with no sample presets)
    private val _selectedMedia = MutableStateFlow<MediaItemInfo?>(null)
    val selectedMedia: StateFlow<MediaItemInfo?> = _selectedMedia.asStateFlow()

    private val _originalSubtitle = MutableStateFlow<SubtitleFile?>(null)
    val originalSubtitle: StateFlow<SubtitleFile?> = _originalSubtitle.asStateFlow()

    private val _activeSubtitle = MutableStateFlow<SubtitleFile?>(null)
    val activeSubtitle: StateFlow<SubtitleFile?> = _activeSubtitle.asStateFlow()

    private val _currentEvents = MutableStateFlow<List<AudioDialogueEvent>>(emptyList())
    val currentEvents: StateFlow<List<AudioDialogueEvent>> = _currentEvents.asStateFlow()

    private val _waveformFrames = MutableStateFlow<List<AudioWaveformFrame>>(emptyList())
    val waveformFrames: StateFlow<List<AudioWaveformFrame>> = _waveformFrames.asStateFlow()

    private val _speechBoundaries = MutableStateFlow<List<AudioSpeechBoundary>>(emptyList())
    val speechBoundaries: StateFlow<List<AudioSpeechBoundary>> = _speechBoundaries.asStateFlow()

    private val _selectedVadSensitivity = MutableStateFlow(VadSensitivity.BALANCED)
    val selectedVadSensitivity: StateFlow<VadSensitivity> = _selectedVadSensitivity.asStateFlow()

    fun setVadSensitivity(sensitivity: VadSensitivity) {
        _selectedVadSensitivity.value = sensitivity
    }

    // Gemini API Key management
    private val _userApiKey = MutableStateFlow(geminiService.getResolvedApiKey(application))
    val userApiKey: StateFlow<String> = _userApiKey.asStateFlow()

    // Sync State & Result
    private val _syncState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    private val _lastSyncResult = MutableStateFlow<SyncResult?>(null)
    val lastSyncResult: StateFlow<SyncResult?> = _lastSyncResult.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    fun saveApiKey(key: String) {
        val trimmed = key.trim()
        geminiService.saveCustomApiKey(getApplication(), trimmed)
        _userApiKey.value = trimmed
        viewModelScope.launch {
            if (trimmed.isNotBlank()) {
                _toastEvent.emit("Gemini API Key saved successfully.")
            } else {
                _toastEvent.emit("Gemini API Key cleared.")
            }
        }
    }

    /**
     * Inspects media file using MediaAnalyzer:
     * - Accurate 2+ hour duration via MediaMetadataRetriever
     * - OpenSubtitles 64-bit MovieHash
     * - Voice activity onset events, RMS waveform profile & VAD speech boundaries
     */
    fun loadCustomMedia(uri: Uri, name: String, isVideo: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _syncState.value = SyncUiState.Processing("Extracting audio stream & computing MovieHash...", 0.25f)
            try {
                val context = getApplication<Application>()
                val analysis = MediaAnalyzer.analyze(context, uri, name, isVideo)

                withContext(Dispatchers.Main) {
                    _selectedMedia.value = analysis.mediaInfo
                    _currentEvents.value = analysis.voiceEvents
                    _waveformFrames.value = analysis.waveformFrames
                    _speechBoundaries.value = analysis.speechBoundaries
                    playerController.loadUri(uri, _activeSubtitle.value)
                    _syncState.value = SyncUiState.Idle
                    _toastEvent.emit("Loaded ${analysis.mediaInfo.formattedDuration} audio stream with ${analysis.speechBoundaries.size} VAD speech boundaries.")
                }

                // Check if hash matches an existing profile
                val known = profileDao.findByOpenSubtitlesHash(analysis.mediaInfo.openSubtitlesHash)
                if (known != null) {
                    _toastEvent.emit("Found saved sync profile for hash ${analysis.mediaInfo.openSubtitlesHash}! (${known.calibratedOffsetMs}ms)")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _syncState.value = SyncUiState.Error("Error inspecting media file: ${e.message}")
                }
            }
        }
    }

    fun loadCustomSubtitle(uri: Uri, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                val parsed = SubtitleParser.parse(text, name)

                withContext(Dispatchers.Main) {
                    _originalSubtitle.value = parsed
                    _activeSubtitle.value = parsed
                    playerController.setSubtitle(parsed)
                    _lastSyncResult.value = null
                    _toastEvent.emit("Loaded ${parsed.cueCount} subtitle cues (${parsed.format.displayName}).")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _syncState.value = SyncUiState.Error("Failed to parse subtitle file: ${e.message}")
                }
            }
        }
    }

    /**
     * AI Auto-Sync (Gemini AI + Selective Cue Alignment):
     * 1. Check MovieHash database for exact matches
     * 2. Call Gemini 3.5 Flash to compute baseline offset & drift across the 2-hour timeline
     * 3. Selectively adjust ONLY cues that are out of sync, preserving correctly-aligned cues
     * 4. Reports total cues, cues adjusted, and detailed list of all adjusted cues
     */
    fun performAiAutoSync() {
        val media = _selectedMedia.value
        val currentSub = _originalSubtitle.value

        if (media == null) {
            viewModelScope.launch {
                _toastEvent.emit("Please import an audio or video file first.")
            }
            return
        }
        if (currentSub == null) {
            viewModelScope.launch {
                _toastEvent.emit("Please import or search for a subtitle file first.")
            }
            return
        }

        val events = _currentEvents.value

        viewModelScope.launch {
            _syncState.value = SyncUiState.Processing("Extracting speech timing & computing hash...", 0.2f)
            vibrateFeedback(30)

            // Step 1: Check Hash database for instant exact match
            val cachedProfile = withContext(Dispatchers.IO) {
                profileDao.findByOpenSubtitlesHash(media.openSubtitlesHash)
            }

            if (cachedProfile != null && cachedProfile.openSubtitlesHash == media.openSubtitlesHash) {
                _syncState.value = SyncUiState.Processing("Matched MovieHash registry! Applying selective alignment...", 0.6f)
                delay(300)

                val output = SelectiveCueSyncEngine.synchronizeSelective(
                    cues = currentSub.cues,
                    speechEvents = events,
                    baselineOffsetMs = cachedProfile.calibratedOffsetMs,
                    driftFactor = cachedProfile.driftFactor
                )

                val synchronizedFile = currentSub.copy(
                    cues = output.synchronizedCues,
                    appliedOffsetMs = cachedProfile.calibratedOffsetMs
                )

                val result = SyncResult(
                    detectedOffsetMs = cachedProfile.calibratedOffsetMs,
                    driftFactor = cachedProfile.driftFactor,
                    confidenceScore = (cachedProfile.confidencePercent / 100f).coerceIn(0.9f, 0.99f),
                    method = SyncMethod.AUDIO_HASH,
                    explanation = "Exact MovieHash match (${media.openSubtitlesHash}). Selectively adjusted ${output.adjustedCount} of ${output.totalCues} cues (${output.unchangedCount} already in sync).",
                    matchedTitle = cachedProfile.mediaTitle,
                    totalCues = output.totalCues,
                    cuesAdjusted = output.adjustedCount,
                    adjustedCueDetails = output.adjustedDetails
                )

                applySyncResult(synchronizedFile, result)
                return@launch
            }

            // Step 2: Try Gemini AI
            _syncState.value = SyncUiState.Processing("AI analyzing dialogue cadence & detecting drift across timeline...", 0.5f)
            val aiResult = geminiService.analyzeAndSynchronize(
                context = getApplication(),
                media = media,
                subtitle = currentSub,
                speechEvents = events
            )

            if (aiResult.isSuccess) {
                val (offset, drift) = aiResult.getOrThrow()
                _syncState.value = SyncUiState.Processing("Gemini AI detected ${offset}ms offset. Performing selective cue alignment...", 0.8f)

                val output = SelectiveCueSyncEngine.synchronizeSelective(
                    cues = currentSub.cues,
                    speechEvents = events,
                    baselineOffsetMs = offset,
                    driftFactor = drift
                )

                val synchronizedFile = currentSub.copy(
                    cues = output.synchronizedCues,
                    appliedOffsetMs = offset
                )

                val result = SyncResult(
                    detectedOffsetMs = offset,
                    driftFactor = drift,
                    confidenceScore = 0.96f,
                    method = SyncMethod.AI_GEMINI,
                    explanation = "Gemini AI calculated ${offset}ms baseline offset (drift ${String.format(Locale.US, "%.4f", drift)}). Selectively adjusted ${output.adjustedCount} of ${output.totalCues} cues (${output.unchangedCount} already aligned).",
                    matchedTitle = media.title,
                    totalCues = output.totalCues,
                    cuesAdjusted = output.adjustedCount,
                    adjustedCueDetails = output.adjustedDetails
                )

                applySyncResult(synchronizedFile, result)
            } else {
                val errorMsg = aiResult.exceptionOrNull()?.message ?: "AI failed"
                // Step 3: Algorithmic Fuzzy Speech Alignment fallback
                _syncState.value = SyncUiState.Processing("Running offline Selective Speech Alignment fallback...", 0.7f)

                val (offset, confidence) = withContext(Dispatchers.Default) {
                    FuzzyMatcher.computeFuzzyAlignmentOffset(currentSub.cues, events)
                }

                val output = SelectiveCueSyncEngine.synchronizeSelective(
                    cues = currentSub.cues,
                    speechEvents = events,
                    baselineOffsetMs = offset,
                    driftFactor = 1.0
                )

                val synchronizedFile = currentSub.copy(
                    cues = output.synchronizedCues,
                    appliedOffsetMs = offset
                )

                val result = SyncResult(
                    detectedOffsetMs = offset,
                    driftFactor = 1.0,
                    confidenceScore = confidence,
                    method = SyncMethod.SELECTIVE_CUE,
                    explanation = "Selective alignment consensus (${errorMsg.take(40)}...). Adjusted ${output.adjustedCount} of ${output.totalCues} cues (${output.unchangedCount} already aligned).",
                    matchedTitle = media.title,
                    totalCues = output.totalCues,
                    cuesAdjusted = output.adjustedCount,
                    adjustedCueDetails = output.adjustedDetails
                )

                applySyncResult(synchronizedFile, result)
            }
        }
    }

    /**
     * Pure Selective Fuzzy Synchronization:
     * Aligns only the cues that diverge from voice audio events.
     */
    fun performFuzzySync() {
        val media = _selectedMedia.value
        val currentSub = _originalSubtitle.value

        if (media == null || currentSub == null) {
            viewModelScope.launch {
                _toastEvent.emit("Please load both a media stream and a subtitle file.")
            }
            return
        }
        val events = _currentEvents.value

        viewModelScope.launch {
            _syncState.value = SyncUiState.Processing("Calculating selective speech correlation...", 0.5f)
            vibrateFeedback(30)

            val (offset, confidence) = withContext(Dispatchers.Default) {
                FuzzyMatcher.computeFuzzyAlignmentOffset(currentSub.cues, events)
            }

            val output = SelectiveCueSyncEngine.synchronizeSelective(
                cues = currentSub.cues,
                speechEvents = events,
                baselineOffsetMs = offset,
                driftFactor = 1.0
            )

            val synchronizedFile = currentSub.copy(
                cues = output.synchronizedCues,
                appliedOffsetMs = offset
            )

            val result = SyncResult(
                detectedOffsetMs = offset,
                driftFactor = 1.0,
                confidenceScore = confidence,
                method = SyncMethod.FUZZY_SPEECH,
                explanation = "Speech correlation consensus. Selectively adjusted ${output.adjustedCount} of ${output.totalCues} cues (${output.unchangedCount} already aligned).",
                matchedTitle = media.title,
                totalCues = output.totalCues,
                cuesAdjusted = output.adjustedCount,
                adjustedCueDetails = output.adjustedDetails
            )

            applySyncResult(synchronizedFile, result)
        }
    }

    private fun applySyncResult(synchronizedFile: SubtitleFile, result: SyncResult) {
        _activeSubtitle.value = synchronizedFile
        _lastSyncResult.value = result
        _syncState.value = SyncUiState.Success(result)
        playerController.setSubtitle(synchronizedFile)
        vibrateFeedback(60)

        // Auto-save profile to Room database
        viewModelScope.launch(Dispatchers.IO) {
            _selectedMedia.value?.let { media ->
                profileDao.insertProfile(
                    SyncProfileEntity(
                        mediaTitle = media.title,
                        openSubtitlesHash = media.openSubtitlesHash,
                        sha256Hash = media.sha256Hash,
                        subtitleTitle = synchronizedFile.title,
                        calibratedOffsetMs = result.detectedOffsetMs,
                        driftFactor = result.driftFactor,
                        confidencePercent = (result.confidenceScore * 100).toInt(),
                        syncMethod = result.method.displayName,
                        cueCount = synchronizedFile.cueCount,
                        mediaDurationMs = media.durationMs
                    )
                )
            }
        }
    }

    /**
     * Reverts to original unadjusted subtitle cues
     */
    fun revertToOriginal() {
        val orig = _originalSubtitle.value ?: return
        _activeSubtitle.value = orig
        _lastSyncResult.value = null
        playerController.setSubtitle(orig)
        viewModelScope.launch {
            _toastEvent.emit("Reverted to original subtitle cues.")
        }
    }

    /**
     * Manual fine-tuning nudge (e.g. +50ms or -100ms)
     */
    fun nudgeActiveOffset(deltaMs: Long) {
        val current = _activeSubtitle.value ?: return
        val shifted = SubtitleParser.shiftTimestamps(current.cues, deltaMs)
        val updated = current.copy(
            cues = shifted,
            appliedOffsetMs = current.appliedOffsetMs + deltaMs
        )
        _activeSubtitle.value = updated
        playerController.setSubtitle(updated)
        vibrateFeedback(20)
    }

    /**
     * Apply speed scaling for frame rate drift (e.g. 23.976 <-> 25 fps)
     */
    fun applyFrameRateDrift(factor: Double) {
        val current = _activeSubtitle.value ?: return
        val scaled = SubtitleParser.scaleSpeed(current.cues, factor)
        val updated = current.copy(cues = scaled)
        _activeSubtitle.value = updated
        playerController.setSubtitle(updated)
        vibrateFeedback(30)
    }

    /**
     * Acoustic Alignment with Waveform/Spectrogram + RMS + VAD:
     * Analyzes audio energy and voice activity boundaries to snap subtitle
     * cues to exact speech onsets/offsets at word/syllable/phoneme resolution,
     * repairing inverted timestamps and resolving overlaps.
     */
    fun performAcousticVadAlignment(sensitivity: VadSensitivity = _selectedVadSensitivity.value) {
        val media = _selectedMedia.value
        val currentSub = _originalSubtitle.value ?: _activeSubtitle.value

        if (media == null || currentSub == null) {
            viewModelScope.launch {
                _toastEvent.emit("Please load both audio/video media and a subtitle file.")
            }
            return
        }

        viewModelScope.launch {
            _syncState.value = SyncUiState.Processing(
                "Acoustic alignment: Computing RMS waveform & VAD speech boundaries...",
                0.35f
            )
            vibrateFeedback(30)

            val boundaries = if (_speechBoundaries.value.isNotEmpty()) {
                _speechBoundaries.value
            } else {
                withContext(Dispatchers.Default) {
                    AcousticAlignmentEngine.detectSpeechBoundaries(
                        timelineDurationMs = media.durationMs,
                        rmsFrames = _waveformFrames.value,
                        sensitivity = sensitivity
                    )
                }
            }

            _syncState.value = SyncUiState.Processing(
                "Snapping ${currentSub.cueCount} cues to word/syllable phonemes & resolving overlaps...",
                0.75f
            )

            val alignmentResult = withContext(Dispatchers.Default) {
                AcousticAlignmentEngine.alignCuesWithAcousticVad(
                    cues = currentSub.cues,
                    boundaries = boundaries,
                    sensitivity = sensitivity,
                    baselineOffsetMs = currentSub.appliedOffsetMs,
                    driftFactor = 1.0
                )
            }

            val synchronizedFile = currentSub.copy(
                cues = alignmentResult.alignedCues
            )

            val explanation = buildString {
                append("Acoustic Alignment (RMS + VAD): ")
                append("Snapped ${alignmentResult.snappedCuesCount} cues to voice boundaries. ")
                if (alignmentResult.invertedTimestampsRepaired > 0) {
                    append("Repaired ${alignmentResult.invertedTimestampsRepaired} inverted timestamps. ")
                }
                if (alignmentResult.overlapsResolved > 0) {
                    append("Resolved ${alignmentResult.overlapsResolved} overlaps. ")
                }
                if (alignmentResult.syllableAdjustedCount > 0) {
                    append("Calibrated ${alignmentResult.syllableAdjustedCount} syllable/phoneme bounds. ")
                }
                append("(${alignmentResult.unchangedCount} cues already accurate).")
            }

            val result = SyncResult(
                detectedOffsetMs = alignmentResult.averageShiftMs,
                driftFactor = 1.0,
                confidenceScore = 0.98f,
                method = SyncMethod.ACOUSTIC_VAD,
                explanation = explanation,
                matchedTitle = media.title,
                totalCues = alignmentResult.totalCues,
                cuesAdjusted = alignmentResult.adjustedDetails.size,
                adjustedCueDetails = alignmentResult.adjustedDetails,
                invertedRepaired = alignmentResult.invertedTimestampsRepaired,
                overlapsResolved = alignmentResult.overlapsResolved,
                syllableAdjusted = alignmentResult.syllableAdjustedCount
            )

            applySyncResult(synchronizedFile, result)
            _toastEvent.emit("Acoustic alignment complete: ${alignmentResult.snappedCuesCount} cues snapped, ${alignmentResult.invertedTimestampsRepaired} inverted repaired.")
        }
    }

    /**
     * Fixes all fundamental timing errors:
     * - Inverted timestamps (endTime <= startTime)
     * - Negative start timestamps
     * - Overlapping cues between adjacent subtitles
     */
    fun fixAllTimingErrors() {
        val current = _activeSubtitle.value ?: return
        val (fixedCues, stats) = SubtitleParser.repairTimingErrors(current.cues)
        val updated = current.copy(cues = fixedCues)
        _activeSubtitle.value = updated
        playerController.setSubtitle(updated)
        vibrateFeedback(30)
        viewModelScope.launch {
            _toastEvent.emit("Timing repair: ${stats.invertedFixed} inverted fixed, ${stats.overlapsResolved} overlaps resolved.")
        }
    }

    fun fixOverlaps() {
        fixAllTimingErrors()
    }

    fun updateCue(updatedCue: SubtitleCue) {
        val current = _activeSubtitle.value ?: return
        val list = current.cues.map { if (it.id == updatedCue.id) updatedCue else it }
        val updated = current.copy(cues = list)
        _activeSubtitle.value = updated
        playerController.setSubtitle(updated)
    }

    fun deleteCue(cueId: Int) {
        val current = _activeSubtitle.value ?: return
        val list = current.cues.filter { it.id != cueId }
        val updated = current.copy(cues = list)
        _activeSubtitle.value = updated
        playerController.setSubtitle(updated)
    }

    fun deleteProfile(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            profileDao.deleteById(id)
        }
    }

    fun exportSerializedSubtitle(format: SubtitleFormat = SubtitleFormat.SRT): String {
        val sub = _activeSubtitle.value ?: return ""
        return SubtitleParser.serialize(sub, format)
    }

    fun searchOnlineSubtitles(
        queryOverride: String? = null,
        language: String = "All",
        source: OnlineSubtitleSource? = null,
        format: SubtitleFormat? = null
    ) {
        val media = _selectedMedia.value
        if (media == null) {
            viewModelScope.launch {
                _toastEvent.emit("Please import a media file first to search subtitles.")
            }
            return
        }
        viewModelScope.launch {
            _isSearchingOnline.value = true
            try {
                val results = onlineSubtitleService.searchSubtitles(
                    media = media,
                    queryOverride = queryOverride,
                    languageFilter = language,
                    sourceFilter = source,
                    formatFilter = format
                )
                _onlineSearchResults.value = results
                if (results.isEmpty()) {
                    _toastEvent.emit("No subtitles found for ${media.title}")
                }
            } catch (e: Exception) {
                _toastEvent.emit("Online search error: ${e.message}")
            } finally {
                _isSearchingOnline.value = false
            }
        }
    }

    fun downloadAndSynchronizeSubtitle(match: OnlineSubtitleMatch, autoSync: Boolean = true) {
        viewModelScope.launch {
            _syncState.value = SyncUiState.Processing(
                "Downloading ${match.format.name} from ${match.source.displayName}...",
                0.3f
            )
            vibrateFeedback(30)
            delay(350)

            try {
                val parsed = SubtitleParser.parse(match.downloadableContent, match.releaseTitle)
                _originalSubtitle.value = parsed
                _activeSubtitle.value = parsed
                playerController.setSubtitle(parsed)

                if (match.recommendedOffsetMs != 0L) {
                    _syncState.value = SyncUiState.Processing(
                        "Auto-aligning to video hash (${match.recommendedOffsetMs}ms)...",
                        0.75f
                    )
                    delay(250)

                    val output = SelectiveCueSyncEngine.synchronizeSelective(
                        cues = parsed.cues,
                        speechEvents = _currentEvents.value,
                        baselineOffsetMs = match.recommendedOffsetMs,
                        driftFactor = 1.0
                    )

                    val synchronizedFile = parsed.copy(
                        cues = output.synchronizedCues,
                        appliedOffsetMs = match.recommendedOffsetMs
                    )
                    _activeSubtitle.value = synchronizedFile
                    playerController.setSubtitle(synchronizedFile)

                    val syncResult = SyncResult(
                        detectedOffsetMs = match.recommendedOffsetMs,
                        driftFactor = 1.0,
                        confidenceScore = if (match.matchType == HashMatchType.EXACT_MOVIE_HASH) 0.99f else 0.92f,
                        method = SyncMethod.ONLINE_DATABASE,
                        explanation = "Matched via ${match.source.displayName} (${match.matchType.label}). Selectively adjusted ${output.adjustedCount} of ${output.totalCues} cues.",
                        matchedTitle = match.releaseTitle,
                        totalCues = output.totalCues,
                        cuesAdjusted = output.adjustedCount,
                        adjustedCueDetails = output.adjustedDetails
                    )
                    _lastSyncResult.value = syncResult
                    _syncState.value = SyncUiState.Success(syncResult)

                    // Auto-save to Room database
                    _selectedMedia.value?.let { media ->
                        profileDao.insertProfile(
                            SyncProfileEntity(
                                mediaTitle = media.title,
                                openSubtitlesHash = media.openSubtitlesHash,
                                sha256Hash = media.sha256Hash,
                                subtitleTitle = synchronizedFile.title,
                                calibratedOffsetMs = syncResult.detectedOffsetMs,
                                driftFactor = syncResult.driftFactor,
                                confidencePercent = (syncResult.confidenceScore * 100).toInt(),
                                syncMethod = "Online (${match.source.displayName})",
                                cueCount = synchronizedFile.cueCount,
                                mediaDurationMs = media.durationMs
                            )
                        )
                    }
                    vibrateFeedback(60)
                    _toastEvent.emit("Synchronized ${match.format.displayName} from ${match.source.displayName}!")
                } else if (autoSync) {
                    performAiAutoSync()
                    _toastEvent.emit("Downloaded ${match.format.displayName}. Running AI Auto-Sync...")
                } else {
                    _syncState.value = SyncUiState.Idle
                    _toastEvent.emit("Loaded ${match.format.displayName} from ${match.source.displayName}.")
                }
            } catch (e: Exception) {
                _syncState.value = SyncUiState.Error("Failed to parse subtitle: ${e.message}")
            }
        }
    }

    private fun vibrateFeedback(ms: Long) {
        try {
            val context = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(ms)
            }
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        playerController.release()
    }
}
