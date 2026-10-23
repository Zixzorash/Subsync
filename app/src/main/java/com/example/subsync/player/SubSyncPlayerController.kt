package com.example.subsync.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.example.subsync.model.SubtitleCue
import com.example.subsync.model.SubtitleFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class SubSyncPlayerController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _activeCue = MutableStateFlow<SubtitleCue?>(null)
    val activeCue: StateFlow<SubtitleCue?> = _activeCue.asStateFlow()

    private val _upcomingCue = MutableStateFlow<SubtitleCue?>(null)
    val upcomingCue: StateFlow<SubtitleCue?> = _upcomingCue.asStateFlow()

    // Subtitle sync tuning
    private var currentSubtitle: SubtitleFile? = null
    private val _manualNudgeMs = MutableStateFlow(0L)
    val manualNudgeMs: StateFlow<Long> = _manualNudgeMs.asStateFlow()

    fun loadFile(file: File, subtitle: SubtitleFile?) {
        release()
        currentSubtitle = subtitle
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { mp ->
                    _durationMs.value = mp.duration.toLong()
                    startProgressTracker()
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPositionMs.value = _durationMs.value
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("SubSyncPlayerController", "Error loading file", e)
        }
    }

    fun loadUri(uri: Uri, subtitle: SubtitleFile?) {
        release()
        currentSubtitle = subtitle
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setOnPreparedListener { mp ->
                    _durationMs.value = mp.duration.toLong()
                    startProgressTracker()
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPositionMs.value = _durationMs.value
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("SubSyncPlayerController", "Error loading uri", e)
        }
    }

    fun setSubtitle(subtitle: SubtitleFile?) {
        currentSubtitle = subtitle
        updateActiveCue(_currentPositionMs.value)
    }

    fun setManualNudge(nudgeMs: Long) {
        _manualNudgeMs.value = nudgeMs
        updateActiveCue(_currentPositionMs.value)
    }

    fun adjustNudge(deltaMs: Long) {
        _manualNudgeMs.value += deltaMs
        updateActiveCue(_currentPositionMs.value)
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            _isPlaying.value = false
        } else {
            player.start()
            _isPlaying.value = true
        }
    }

    fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        val safePos = positionMs.coerceIn(0L, _durationMs.value)
        player.seekTo(safePos.toInt())
        _currentPositionMs.value = safePos
        updateActiveCue(safePos)
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        val pos = player.currentPosition.toLong()
                        _currentPositionMs.value = pos
                        _isPlaying.value = true
                        updateActiveCue(pos)
                    }
                }
                delay(40L) // 25fps update rate for buttery smooth sync tracking
            }
        }
    }

    private fun updateActiveCue(playbackPosMs: Long) {
        val sub = currentSubtitle ?: run {
            _activeCue.value = null
            _upcomingCue.value = null
            return
        }

        // Subtitle effective time is media playback position MINUS any manual nudge and applied offset
        // Or if subtitle start times are already shifted: cue.startTimeMs vs effective playback
        val effectivePos = playbackPosMs - _manualNudgeMs.value

        val active = sub.cues.firstOrNull { cue ->
            effectivePos in cue.startTimeMs..cue.endTimeMs
        }
        _activeCue.value = active

        val next = sub.cues.firstOrNull { cue ->
            cue.startTimeMs > effectivePos
        }
        _upcomingCue.value = next
    }

    fun release() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        _isPlaying.value = false
        _currentPositionMs.value = 0L
        _activeCue.value = null
        _upcomingCue.value = null
    }
}
