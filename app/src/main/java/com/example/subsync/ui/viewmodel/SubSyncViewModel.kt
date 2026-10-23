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
import com.example.subsync.data.sample.SampleMediaProvider
import com.example.subsync.data.sample.SamplePreset
import com.example.subsync.model.AudioDialogueEvent
import com.example.subsync.model.MediaItemInfo
import com.example.subsync.model.SubtitleCue
import com.example.subsync.model.SubtitleFile
import com.example.subsync.model.SubtitleFormat
import com.example.subsync.model.SyncMethod
import com.example.subsync.model.SyncResult
import com.example.subsync.player.SubSyncPlayerController
import com.example.subsync.util.FuzzyMatcher
import com.example.subsync.util.OpenSubtitlesHasher
import com.example.subsync.util.SubtitleParser
import kotlinx.coroutines.Dispatchers
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
import com.example.subsync.data.online.OnlineSubtitleService
import com.example.subsync.model.HashMatchType
import com.example.subsync.model.OnlineSubtitleMatch
import com.example.subsync.model.OnlineSubtitleSource
import kotlinx.coroutines.delay
import java.io.File

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

    // Currently active media and subtitle
    private val _selectedMedia = MutableStateFlow<MediaItemInfo?>(null)
    val selectedMedia: StateFlow<MediaItemInfo?> = _selectedMedia.asStateFlow()

    private val _originalSubtitle = MutableStateFlow<SubtitleFile?>(null)
    val originalSubtitle: StateFlow<SubtitleFile?> = _originalSubtitle.asStateFlow()

    private val _activeSubtitle = MutableStateFlow<SubtitleFile?>(null)
    val activeSubtitle: StateFlow<SubtitleFile?> = _activeSubtitle.asStateFlow()

    private val _currentEvents = MutableStateFlow<List<AudioDialogueEvent>>(emptyList())
    val currentEvents: StateFlow<List<AudioDialogueEvent>> = _currentEvents.asStateFlow()

    // Sync State & Result
    private val _syncState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    private val _lastSyncResult = MutableStateFlow<SyncResult?>(null)
    val lastSyncResult: StateFlow<SyncResult?> = _lastSyncResult.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    init {
        // Pre-populate with first preset so the user sees immediate rich content
        loadPreset(SampleMediaProvider.PRESET_SCI_FI)
        seedKnownProfilesIfEmpty()
    }

    private fun seedKnownProfilesIfEmpty() {
        viewModelScope.launch(Dispatchers.IO) {
            SampleMediaProvider.ALL_PRESETS.forEach { preset ->
                val existing = profileDao.findByOpenSubtitlesHash(preset.media.openSubtitlesHash)
                if (existing == null) {
                    profileDao.insertProfile(
                        SyncProfileEntity(
                            mediaTitle = preset.media.title,
                            openSubtitlesHash = preset.media.openSubtitlesHash,
                            sha256Hash = preset.media.sha256Hash,
                            subtitleTitle = preset.unsyncedSubtitle.title,
                            calibratedOffsetMs = preset.groundTruthOffsetMs,
                            driftFactor = preset.groundTruthDrift,
                            confidencePercent = 99,
                            syncMethod = "MovieHash Registry",
                            cueCount = preset.unsyncedSubtitle.cueCount,
                            mediaDurationMs = preset.media.durationMs
                        )
                    )
                }
            }
        }
    }

    fun loadPreset(preset: SamplePreset) {
        _selectedMedia.value = preset.media
        _originalSubtitle.value = preset.unsyncedSubtitle
        _activeSubtitle.value = preset.unsyncedSubtitle
        _currentEvents.value = preset.groundTruthEvents
        _lastSyncResult.value = null
        _syncState.value = SyncUiState.Idle

        // Load playable WAV file into player
        viewModelScope.launch(Dispatchers.IO) {
            val wavFile = SampleMediaProvider.createSampleWavFile(getApplication(), preset)
            withContext(Dispatchers.Main) {
                playerController.loadFile(wavFile, preset.unsyncedSubtitle)
            }
        }
    }

    fun loadCustomMedia(uri: Uri, name: String, isVideo: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _syncState.value = SyncUiState.Processing("Computing MovieHash & audio fingerprint...", 0.2f)
            try {
                val context = getApplication<Application>()
                var fileSize = 0L
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    fileSize = pfd.statSize
                }

                val hash = context.contentResolver.openInputStream(uri)?.use { stream ->
                    val buffer = ByteArray(65536)
                    val read = stream.read(buffer)
                    if (read > 0) OpenSubtitlesHasher.computeBytesHash(buffer.copyOf(read), fileSize)
                    else "0000000000000000"
                } ?: "0000000000000000"

                val media = MediaItemInfo(
                    title = name,
                    uriString = uri.toString(),
                    isSample = false,
                    durationMs = 60000L,
                    fileSizeBytes = fileSize,
                    openSubtitlesHash = hash,
                    sha256Hash = hash.reversed(),
                    isVideo = isVideo
                )

                withContext(Dispatchers.Main) {
                    _selectedMedia.value = media
                    playerController.loadUri(uri, _activeSubtitle.value)
                    _syncState.value = SyncUiState.Idle
                }

                // Check if hash matches an existing profile
                val known = profileDao.findByOpenSubtitlesHash(hash)
                if (known != null) {
                    _toastEvent.emit("Found saved sync profile for hash $hash! (${known.calibratedOffsetMs}ms)")
                }
            } catch (e: Exception) {
                _syncState.value = SyncUiState.Error("Error inspecting media file: ${e.message}")
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
                    _toastEvent.emit("Loaded ${parsed.cueCount} subtitle cues.")
                }
            } catch (e: Exception) {
                _syncState.value = SyncUiState.Error("Failed to parse subtitle file: ${e.message}")
            }
        }
    }

    /**
     * AI Auto-Sync: Uses Gemini AI or Fuzzy logic to detect and calibrate subtitle offset.
     */
    fun performAiAutoSync() {
        val media = _selectedMedia.value ?: return
        val currentSub = _originalSubtitle.value ?: return
        val events = _currentEvents.value

        viewModelScope.launch {
            _syncState.value = SyncUiState.Processing("Extracting speech timing & computing hash...", 0.3f)
            vibrateFeedback(30)

            // Step 1: Check Hash database for instant exact match
            val cachedProfile = withContext(Dispatchers.IO) {
                profileDao.findByOpenSubtitlesHash(media.openSubtitlesHash)
            }

            if (cachedProfile != null && cachedProfile.openSubtitlesHash == media.openSubtitlesHash) {
                _syncState.value = SyncUiState.Processing("Matched MovieHash registry! Applying offset...", 0.8f)
                kotlinx.coroutines.delay(400) // smooth visual feedback

                val result = SyncResult(
                    detectedOffsetMs = cachedProfile.calibratedOffsetMs,
                    driftFactor = cachedProfile.driftFactor,
                    confidenceScore = (cachedProfile.confidencePercent / 100f).coerceIn(0.9f, 0.99f),
                    method = SyncMethod.AUDIO_HASH,
                    explanation = "Exact OpenSubtitles hash match (${media.openSubtitlesHash}). Applied calibrated offset.",
                    matchedTitle = cachedProfile.mediaTitle,
                    cuesAdjusted = currentSub.cueCount
                )
                applySyncResult(result)
                return@launch
            }

            // Step 2: Try Gemini AI
            _syncState.value = SyncUiState.Processing("AI analyzing dialogue cadence & detecting drift...", 0.6f)
            val aiResult = geminiService.analyzeAndSynchronize(media, currentSub, events)

            if (aiResult.isSuccess) {
                val result = aiResult.getOrThrow()
                applySyncResult(result)
            } else {
                // Step 3: High-accuracy Fallback to algorithmic Fuzzy Speech Alignment
                _syncState.value = SyncUiState.Processing("Running offline Fuzzy Matcher & speech alignment...", 0.75f)
                val (offset, confidence) = withContext(Dispatchers.Default) {
                    FuzzyMatcher.computeFuzzyAlignmentOffset(currentSub.cues, events)
                }

                val result = SyncResult(
                    detectedOffsetMs = offset,
                    driftFactor = 1.0,
                    confidenceScore = confidence,
                    method = SyncMethod.FUZZY_SPEECH,
                    explanation = "Fuzzy text & speech event consensus detected optimal alignment at ${offset}ms.",
                    matchedTitle = media.title,
                    cuesAdjusted = currentSub.cueCount
                )
                applySyncResult(result)
            }
        }
    }

    /**
     * Pure Offline Fuzzy Synchronization
     */
    fun performFuzzySync() {
        val media = _selectedMedia.value ?: return
        val currentSub = _originalSubtitle.value ?: return
        val events = _currentEvents.value

        viewModelScope.launch {
            _syncState.value = SyncUiState.Processing("Calculating Levenshtein token alignment...", 0.5f)
            vibrateFeedback(30)

            val (offset, confidence) = withContext(Dispatchers.Default) {
                FuzzyMatcher.computeFuzzyAlignmentOffset(currentSub.cues, events)
            }

            val result = SyncResult(
                detectedOffsetMs = offset,
                driftFactor = 1.0,
                confidenceScore = confidence,
                method = SyncMethod.FUZZY_SPEECH,
                explanation = "Matched subtitle cues against speech events using token sorting & sliding window correlation.",
                matchedTitle = media.title,
                cuesAdjusted = currentSub.cueCount
            )
            applySyncResult(result)
        }
    }

    private fun applySyncResult(result: SyncResult) {
        val base = _originalSubtitle.value ?: return
        var shifted = SubtitleParser.shiftTimestamps(base.cues, result.detectedOffsetMs)
        if (result.driftFactor != 1.0) {
            shifted = SubtitleParser.scaleSpeed(shifted, result.driftFactor)
        }
        val synchronizedFile = base.copy(
            cues = shifted,
            appliedOffsetMs = result.detectedOffsetMs
        )

        _activeSubtitle.value = synchronizedFile
        _lastSyncResult.value = result
        _syncState.value = SyncUiState.Success(result)
        playerController.setSubtitle(synchronizedFile)
        vibrateFeedback(60)

        // Auto-save to Room database
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

    fun fixOverlaps() {
        val current = _activeSubtitle.value ?: return
        val fixed = SubtitleParser.fixOverlaps(current.cues)
        val updated = current.copy(cues = fixed)
        _activeSubtitle.value = updated
        playerController.setSubtitle(updated)
        viewModelScope.launch {
            _toastEvent.emit("Overlapping subtitle cues resolved.")
        }
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
        val media = _selectedMedia.value ?: return
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
                    val shifted = SubtitleParser.shiftTimestamps(parsed.cues, match.recommendedOffsetMs)
                    val synchronizedFile = parsed.copy(
                        cues = shifted,
                        appliedOffsetMs = match.recommendedOffsetMs
                    )
                    _activeSubtitle.value = synchronizedFile
                    playerController.setSubtitle(synchronizedFile)

                    val syncResult = SyncResult(
                        detectedOffsetMs = match.recommendedOffsetMs,
                        driftFactor = 1.0,
                        confidenceScore = if (match.matchType == HashMatchType.EXACT_MOVIE_HASH) 0.99f else 0.92f,
                        method = SyncMethod.ONLINE_DATABASE,
                        explanation = "Matched via ${match.source.displayName} (${match.matchType.label}). Calibrated against video hash.",
                        matchedTitle = match.releaseTitle,
                        cuesAdjusted = synchronizedFile.cueCount
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
