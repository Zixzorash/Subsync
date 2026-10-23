package com.example.subsync.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.subsync.model.SubtitleCue
import com.example.subsync.model.SubtitleFormat
import com.example.subsync.ui.theme.CrimsonError
import com.example.subsync.ui.theme.DarkBorder
import com.example.subsync.ui.theme.DarkSurface
import com.example.subsync.ui.theme.DarkSurfaceVariant
import com.example.subsync.ui.theme.ElectricIndigo
import com.example.subsync.ui.theme.EmeraldSuccess
import com.example.subsync.ui.theme.NeonCyan
import com.example.subsync.ui.theme.TextPrimary
import com.example.subsync.ui.theme.TextSecondary
import com.example.subsync.ui.viewmodel.SubSyncViewModel

@Composable
fun EditorScreen(
    viewModel: SubSyncViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeSubtitle by viewModel.activeSubtitle.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var editingCue by remember { mutableStateOf<SubtitleCue?>(null) }
    var showShiftDialog by remember { mutableStateOf(false) }
    var showDriftDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    val cues = activeSubtitle?.cues ?: emptyList()
    val filteredCues = if (searchQuery.isBlank()) cues else cues.filter {
        it.text.contains(searchQuery, ignoreCase = true) || it.id.toString() == searchQuery
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Search & Batch Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("subtitle_search_input"),
                placeholder = { Text("Filter cues...", fontSize = 13.sp, color = TextSecondary) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            // Export / Share button
            Button(
                onClick = { showExportDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                modifier = Modifier.height(50.dp).testTag("export_subtitle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Export Subtitle",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Quick Batch Operations Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ToolChip(
                icon = Icons.Default.MoreTime,
                label = "Shift ms",
                modifier = Modifier.weight(1f)
            ) {
                showShiftDialog = true
            }

            ToolChip(
                icon = Icons.Default.Speed,
                label = "FPS Drift",
                modifier = Modifier.weight(1f)
            ) {
                showDriftDialog = true
            }

            ToolChip(
                icon = Icons.Default.AutoFixHigh,
                label = "Fix Errors",
                modifier = Modifier.weight(1f)
            ) {
                viewModel.fixAllTimingErrors()
            }
        }

        // Summary bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${filteredCues.size} CUES DISPLAYED",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NeonCyan,
                letterSpacing = 1.sp
            )
            Text(
                text = "Applied Offset: ${activeSubtitle?.appliedOffsetMs ?: 0}ms",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
        }

        if (cues.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "No subtitle loaded yet",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Go to the Studio tab to import an SRT, VTT, or ASS file, or find one online.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // Cues List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
            items(filteredCues, key = { it.id }) { cue ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingCue = cue }
                        .testTag("cue_item_${cue.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cue ID Badge
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${cue.id}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${cue.formatStartTime()}  ➜  ${cue.formatEndTime()}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = cue.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }

                        IconButton(
                            onClick = { editingCue = cue },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Cue",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

    // Edit Single Cue Dialog
    if (editingCue != null) {
        val cue = editingCue!!
        var textValue by remember { mutableStateOf(cue.text) }
        var startMsValue by remember { mutableStateOf(cue.startTimeMs.toString()) }
        var endMsValue by remember { mutableStateOf(cue.endTimeMs.toString()) }

        AlertDialog(
            onDismissRequest = { editingCue = null },
            title = { Text("Edit Cue #${cue.id}", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        label = { Text("Dialogue Text") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startMsValue,
                            onValueChange = { startMsValue = it },
                            label = { Text("Start (ms)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endMsValue,
                            onValueChange = { endMsValue = it },
                            label = { Text("End (ms)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val s = startMsValue.toLongOrNull() ?: cue.startTimeMs
                        val e = endMsValue.toLongOrNull() ?: cue.endTimeMs
                        viewModel.updateCue(cue.copy(startTimeMs = s, endTimeMs = e, text = textValue))
                        editingCue = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color(0xFF090D16))
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            viewModel.deleteCue(cue.id)
                            editingCue = null
                        }
                    ) {
                        Text("Delete", color = CrimsonError)
                    }
                    TextButton(onClick = { editingCue = null }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            },
            containerColor = DarkSurface
        )
    }

    // Shift Dialog
    if (showShiftDialog) {
        var shiftInput by remember { mutableStateOf("500") }
        AlertDialog(
            onDismissRequest = { showShiftDialog = false },
            title = { Text("Shift Subtitle Timestamps", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter milliseconds to shift all cues (+ to delay, - to show earlier):", fontSize = 13.sp, color = TextSecondary)
                    OutlinedTextField(
                        value = shiftInput,
                        onValueChange = { shiftInput = it },
                        label = { Text("Offset (ms)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val offset = shiftInput.toLongOrNull() ?: 0L
                        viewModel.nudgeActiveOffset(offset)
                        showShiftDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color(0xFF090D16))
                ) {
                    Text("Apply Shift")
                }
            },
            dismissButton = {
                TextButton(onClick = { showShiftDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // FPS Drift Dialog
    if (showDriftDialog) {
        AlertDialog(
            onDismissRequest = { showDriftDialog = false },
            title = { Text("Frame Rate Drift Calibration", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Convert between broadcast and cinema framerates to eliminate progressive sync drift:", fontSize = 13.sp, color = TextSecondary)

                    FpsOption("PAL to Film (25 fps ➜ 23.976 fps)", "0.95904x") {
                        viewModel.applyFrameRateDrift(23.976 / 25.0)
                        showDriftDialog = false
                    }
                    FpsOption("Film to PAL (23.976 fps ➜ 25 fps)", "1.0427x") {
                        viewModel.applyFrameRateDrift(25.0 / 23.976)
                        showDriftDialog = false
                    }
                    FpsOption("Cinema to PAL (24 fps ➜ 25 fps)", "0.9600x") {
                        viewModel.applyFrameRateDrift(24.0 / 25.0)
                        showDriftDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDriftDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Export Dialog
    if (showExportDialog) {
        val srtContent = viewModel.exportSerializedSubtitle(SubtitleFormat.SRT)
        val vttContent = viewModel.exportSerializedSubtitle(SubtitleFormat.VTT)

        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Synchronized Subtitles", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Export calibrated .srt or .vtt subtitle file or copy to clipboard:", fontSize = 13.sp, color = TextSecondary)

                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SubSync SRT", srtContent))
                            Toast.makeText(context, "SRT copied to clipboard!", Toast.LENGTH_SHORT).show()
                            showExportDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color(0xFF090D16)),
                        modifier = Modifier.fillMaxWidth().testTag("copy_srt_button")
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy as SubRip (.SRT)")
                    }

                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SubSync VTT", vttContent))
                            Toast.makeText(context, "VTT copied to clipboard!", Toast.LENGTH_SHORT).show()
                            showExportDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color(0xFF090D16)),
                        modifier = Modifier.fillMaxWidth().testTag("copy_vtt_button")
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy as WebVTT (.VTT)")
                    }

                    val assContent = viewModel.exportSerializedSubtitle(SubtitleFormat.ASS)
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SubSync ASS", assContent))
                            Toast.makeText(context, "ASS copied to clipboard!", Toast.LENGTH_SHORT).show()
                            showExportDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7), contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().testTag("copy_ass_button")
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy as Advanced SubStation (.ASS)")
                    }

                    Button(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                val textToSend = if (activeSubtitle?.format == SubtitleFormat.ASS) assContent else srtContent
                                putExtra(Intent.EXTRA_TEXT, textToSend)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Subtitle File"))
                            showExportDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                        modifier = Modifier.fillMaxWidth().testTag("share_subtitle_button")
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Current Subtitle (${activeSubtitle?.format?.name ?: "SRT"})")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun ToolChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp)),
        color = DarkSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
    }
}

@Composable
private fun FpsOption(title: String, factor: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp)),
        color = DarkSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 12.sp, color = TextPrimary)
            Text(factor, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
        }
    }
}
