package com.example.subsync.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.subsync.model.SubtitleCue
import com.example.subsync.ui.components.SubtitleOverlay
import com.example.subsync.ui.components.WaveformVisualizer
import com.example.subsync.ui.theme.DarkBorder
import com.example.subsync.ui.theme.DarkSurface
import com.example.subsync.ui.theme.DarkSurfaceVariant
import com.example.subsync.ui.theme.ElectricIndigo
import com.example.subsync.ui.theme.EmeraldSuccess
import com.example.subsync.ui.theme.NeonCyan
import com.example.subsync.ui.theme.TextPrimary
import com.example.subsync.ui.theme.TextSecondary
import com.example.subsync.ui.viewmodel.SubSyncViewModel
import java.util.Locale
import kotlin.math.abs

@Composable
fun LivePlayerScreen(
    viewModel: SubSyncViewModel,
    modifier: Modifier = Modifier
) {
    val player = viewModel.playerController
    val currentPosition by player.currentPositionMs.collectAsState()
    val duration by player.durationMs.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val activeCue by player.activeCue.collectAsState()
    val upcomingCue by player.upcomingCue.collectAsState()
    val manualNudge by player.manualNudgeMs.collectAsState()
    val activeSub by viewModel.activeSubtitle.collectAsState()
    val currentEvents by viewModel.currentEvents.collectAsState()

    val progressFraction = if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        // Cinema Viewport & Subtitle Box
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF060910)),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    // Background ambient visualization
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 60.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        WaveformVisualizer(
                            isPlaying = isPlaying,
                            progressFraction = progressFraction,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    // Floating Live Subtitle Box at bottom
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    ) {
                        SubtitleOverlay(
                            activeCue = activeCue,
                            upcomingCue = upcomingCue
                        )
                    }
                }
            }
        }

        // Playback Scrubber & Time
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Slider(
                    value = progressFraction,
                    onValueChange = { fraction ->
                        player.seekTo((fraction * duration).toLong())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("player_seek_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan,
                        inactiveTrackColor = Color(0xFF1E293B)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                    Text(
                        text = formatTime(duration),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                }
            }
        }

        // Playback Controls (Replay, Play/Pause, Forward)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { player.seekTo((currentPosition - 5000L).coerceAtLeast(0L)) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("replay_5s_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Replay 5s",
                        tint = TextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(NeonCyan, Color(0xFF0284C7))
                            )
                        )
                        .clickable { player.togglePlayPause() }
                        .testTag("play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color(0xFF090D16),
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                IconButton(
                    onClick = { player.seekTo((currentPosition + 5000L).coerceAtMost(duration)) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("forward_5s_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Forward 5s",
                        tint = TextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // Real-Time Live Nudge Controller
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Live Offset Nudge Wheel",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                        }

                        // Cumulative total offset readout
                        val totalOffset = (activeSub?.appliedOffsetMs ?: 0L) + manualNudge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurfaceVariant
                        ) {
                            Text(
                                text = "Offset: ${if (totalOffset > 0) "+" else ""}$totalOffset ms",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (totalOffset == 0L) EmeraldSuccess else NeonCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = "Tap buttons to instantly nudge subtitle timings during playback with haptic feedback.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    // Nudge Buttons Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NudgeButton("-500ms", Modifier.weight(1f)) {
                            player.adjustNudge(-500L)
                            viewModel.nudgeActiveOffset(-500L)
                        }
                        NudgeButton("-100ms", Modifier.weight(1f)) {
                            player.adjustNudge(-100L)
                            viewModel.nudgeActiveOffset(-100L)
                        }
                        NudgeButton("-50ms", Modifier.weight(1f)) {
                            player.adjustNudge(-50L)
                            viewModel.nudgeActiveOffset(-50L)
                        }
                        NudgeButton("+50ms", Modifier.weight(1f)) {
                            player.adjustNudge(50L)
                            viewModel.nudgeActiveOffset(50L)
                        }
                        NudgeButton("+100ms", Modifier.weight(1f)) {
                            player.adjustNudge(100L)
                            viewModel.nudgeActiveOffset(100L)
                        }
                        NudgeButton("+500ms", Modifier.weight(1f)) {
                            player.adjustNudge(500L)
                            viewModel.nudgeActiveOffset(500L)
                        }
                    }
                }
            }
        }

        // Dual-Cue Synchrony Inspector
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "SYNCHRONY INSPECTOR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        letterSpacing = 1.sp
                    )

                    // Closest speech event vs active subtitle
                    val closestEvent = currentEvents.minByOrNull { abs(it.approximateTimeMs - currentPosition) }
                    val delta = if (closestEvent != null && activeCue != null) {
                        abs(activeCue!!.startTimeMs - closestEvent.approximateTimeMs)
                    } else null

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Ground truth speech event
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = DarkSurfaceVariant
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Speech Audio Peak", fontSize = 10.sp, color = TextSecondary)
                                Text(
                                    text = closestEvent?.let { "${it.approximateTimeMs}ms: \"${it.recognizedPhrase.take(22)}...\"" }
                                        ?: "No event detected",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary,
                                    maxLines = 2
                                )
                            }
                        }

                        // Subtitle cue
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = DarkSurfaceVariant
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Active Subtitle Cue", fontSize = 10.sp, color = TextSecondary)
                                Text(
                                    text = activeCue?.let { "${it.startTimeMs}ms: \"${it.text.take(22)}...\"" }
                                        ?: "No cue active",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = EmeraldSuccess,
                                    maxLines = 2
                                )
                            }
                        }
                    }

                    if (delta != null) {
                        Text(
                            text = "Audio-Subtitle Timing Drift: ${delta}ms (${if (delta < 150) "Perfect Sync" else "Minor Delay"})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (delta < 150) EmeraldSuccess else Color(0xFFF59E0B)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NudgeButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp)),
        color = DarkSurfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    val millis = (ms % 1000) / 100
    return String.format(Locale.US, "%02d:%02d.%01d", min, sec, millis)
}
