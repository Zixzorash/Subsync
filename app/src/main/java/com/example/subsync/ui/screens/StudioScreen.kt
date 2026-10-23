package com.example.subsync.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.subsync.data.sample.SampleMediaProvider
import com.example.subsync.model.HashMatchType
import com.example.subsync.model.OnlineSubtitleMatch
import com.example.subsync.model.OnlineSubtitleSource
import com.example.subsync.model.SubtitleFormat
import com.example.subsync.model.SyncResult
import com.example.subsync.ui.components.ConfidenceGauge
import com.example.subsync.ui.theme.DarkBorder
import com.example.subsync.ui.theme.DarkSurface
import com.example.subsync.ui.theme.DarkSurfaceVariant
import com.example.subsync.ui.theme.ElectricIndigo
import com.example.subsync.ui.theme.EmeraldSuccess
import com.example.subsync.ui.theme.NeonCyan
import com.example.subsync.ui.theme.TextPrimary
import com.example.subsync.ui.theme.TextSecondary
import com.example.subsync.ui.viewmodel.SubSyncViewModel
import com.example.subsync.ui.viewmodel.SyncUiState

@Composable
fun StudioScreen(
    viewModel: SubSyncViewModel,
    onNavigateToPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedMedia by viewModel.selectedMedia.collectAsState()
    val activeSubtitle by viewModel.activeSubtitle.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val lastSyncResult by viewModel.lastSyncResult.collectAsState()
    val onlineResults by viewModel.onlineSearchResults.collectAsState()
    val isSearchingOnline by viewModel.isSearchingOnline.collectAsState()

    var showOnlineSearchCard by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf("All") }
    var selectedSource by remember { mutableStateOf<OnlineSubtitleSource?>(null) }
    var selectedFormat by remember { mutableStateOf<SubtitleFormat?>(null) }

    // File pickers (supports SRT, VTT, ASS)
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.loadCustomMedia(uri, uri.lastPathSegment ?: "Custom_Media", false)
        }
    }

    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.loadCustomSubtitle(uri, uri.lastPathSegment ?: "Custom_Subtitle")
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        // Sample Preset Selector Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SAMPLE PRESETS (1-TAP TEST)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    letterSpacing = 1.sp
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SampleMediaProvider.ALL_PRESETS) { preset ->
                        val isSelected = selectedMedia?.openSubtitlesHash == preset.media.openSubtitlesHash
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                viewModel.loadPreset(preset)
                                searchQuery = preset.media.title.split(":").first()
                            },
                            label = {
                                Text(
                                    text = "${preset.media.title.split(":").first()} [${preset.unsyncedSubtitle.format.name}]",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (preset.media.isVideo) Icons.Default.Movie else Icons.Default.Audiotrack,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricIndigo.copy(alpha = 0.25f),
                                selectedLabelColor = NeonCyan,
                                selectedLeadingIconColor = NeonCyan,
                                containerColor = DarkSurface,
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) NeonCyan else DarkBorder
                            )
                        )
                    }
                }
            }
        }

        // Media & Hash Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (selectedMedia?.isVideo == true) Icons.Default.Videocam else Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Media Stream & Hash",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                        }

                        OutlinedButton(
                            onClick = { mediaPickerLauncher.launch("*/*") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("pick_media_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = NeonCyan
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import File", fontSize = 12.sp, color = NeonCyan)
                        }
                    }

                    Text(
                        text = selectedMedia?.title ?: "No media selected",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = DarkSurfaceVariant
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("MovieHash (OpenSubtitles)", fontSize = 10.sp, color = TextSecondary)
                                }
                                Text(
                                    text = selectedMedia?.openSubtitlesHash ?: "----",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan,
                                    maxLines = 1
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = DarkSurfaceVariant
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Duration / Format", fontSize = 10.sp, color = TextSecondary)
                                Text(
                                    text = "${selectedMedia?.formattedDuration} • ${selectedMedia?.sampleRateHz}Hz",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Subtitle Status Card with Format Badges (SRT / VTT / ASS)
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
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = ElectricIndigo,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Target Subtitle",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Search Online Button
                            Button(
                                onClick = {
                                    showOnlineSearchCard = !showOnlineSearchCard
                                    if (onlineResults.isEmpty() && selectedMedia != null) {
                                        viewModel.searchOnlineSubtitles()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                                modifier = Modifier.testTag("search_online_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Search Online", fontSize = 11.sp)
                            }

                            // Import Local File Button (SRT, VTT, ASS)
                            OutlinedButton(
                                onClick = { subtitlePickerLauncher.launch("*/*") },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("pick_subtitle_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = TextSecondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import File", fontSize = 11.sp, color = TextPrimary)
                            }
                        }
                    }

                    Text(
                        text = activeSubtitle?.title ?: "No subtitle loaded",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Format Badge (SRT / VTT / ASS)
                        val formatName = activeSubtitle?.format?.name ?: "SRT"
                        val formatColor = when (activeSubtitle?.format) {
                            SubtitleFormat.ASS -> Color(0xFFA855F7) // Purple for ASS
                            SubtitleFormat.VTT -> Color(0xFF10B981) // Emerald for VTT
                            else -> NeonCyan // Cyan for SRT
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = formatColor.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, formatColor.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = formatName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = formatColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF334155))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${activeSubtitle?.cueCount ?: 0} Cues",
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (activeSubtitle?.appliedOffsetMs != 0L) EmeraldSuccess.copy(alpha = 0.2f)
                                    else Color(0xFFF59E0B).copy(alpha = 0.2f)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (activeSubtitle?.appliedOffsetMs != 0L)
                                    "Calibrated (${activeSubtitle?.appliedOffsetMs}ms)"
                                else "Desynced (Raw)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (activeSubtitle?.appliedOffsetMs != 0L) EmeraldSuccess else Color(0xFFF59E0B)
                            )
                        }
                    }
                }
            }
        }

        // Online Subtitle Database Finder (OpenSubtitles, Subscene, SubtitleCat)
        item {
            AnimatedVisibility(
                visible = showOnlineSearchCard,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A101D)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("online_subtitle_search_card")
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
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Online Subtitle Finder",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "OpenSubtitles • Subscene • SubtitleCat",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            TextButton(onClick = { showOnlineSearchCard = false }) {
                                Text("Hide", fontSize = 12.sp, color = TextSecondary)
                            }
                        }

                        // Search input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text(
                                        text = selectedMedia?.title?.split(":")?.firstOrNull() ?: "Search subtitle...",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("online_search_field"),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            Button(
                                onClick = {
                                    viewModel.searchOnlineSubtitles(
                                        queryOverride = searchQuery,
                                        language = selectedLanguage,
                                        source = selectedSource,
                                        format = selectedFormat
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color(0xFF090D16)),
                                modifier = Modifier.height(50.dp).testTag("online_search_submit_btn")
                            ) {
                                if (isSearchingOnline) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF090D16), strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Find", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Quick Search by MovieHash Pill Button
                        selectedMedia?.let { media ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        searchQuery = "hash:${media.openSubtitlesHash}"
                                        viewModel.searchOnlineSubtitles(
                                            queryOverride = searchQuery,
                                            language = selectedLanguage,
                                            source = selectedSource,
                                            format = selectedFormat
                                        )
                                    }
                                    .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                color = DarkSurfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Query by Video MovieHash: ${media.openSubtitlesHash}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = NeonCyan
                                    )
                                }
                            }
                        }

                        // Filter Chips: Format (SRT, VTT, ASS)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FormatFilterChip("All Formats", selectedFormat == null) {
                                selectedFormat = null
                                viewModel.searchOnlineSubtitles(searchQuery, selectedLanguage, selectedSource, null)
                            }
                            FormatFilterChip("ASS", selectedFormat == SubtitleFormat.ASS) {
                                selectedFormat = SubtitleFormat.ASS
                                viewModel.searchOnlineSubtitles(searchQuery, selectedLanguage, selectedSource, SubtitleFormat.ASS)
                            }
                            FormatFilterChip("SRT", selectedFormat == SubtitleFormat.SRT) {
                                selectedFormat = SubtitleFormat.SRT
                                viewModel.searchOnlineSubtitles(searchQuery, selectedLanguage, selectedSource, SubtitleFormat.SRT)
                            }
                            FormatFilterChip("VTT", selectedFormat == SubtitleFormat.VTT) {
                                selectedFormat = SubtitleFormat.VTT
                                viewModel.searchOnlineSubtitles(searchQuery, selectedLanguage, selectedSource, SubtitleFormat.VTT)
                            }
                        }

                        // Results List
                        if (onlineResults.isNotEmpty()) {
                            Text(
                                text = "MATCHES FOUND (${onlineResults.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan,
                                letterSpacing = 1.sp
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                onlineResults.forEach { match ->
                                    OnlineMatchItemCard(
                                        match = match,
                                        onDownloadAndSync = {
                                            viewModel.downloadAndSynchronizeSubtitle(match, autoSync = true)
                                        },
                                        onDownloadOnly = {
                                            viewModel.downloadAndSynchronizeSubtitle(match, autoSync = false)
                                        }
                                    )
                                }
                            }
                        } else if (!isSearchingOnline) {
                            Text(
                                text = "Tap 'Find' or use the Video MovieHash query above to fetch verified subtitles.",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Primary Synchronization Actions (AI Auto-Sync & Fuzzy Hash)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // AI Auto-Sync Button
                Button(
                    onClick = { viewModel.performAiAutoSync() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("ai_auto_sync_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color(0xFF090D16)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Auto-Synchronize (Gemini)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Offline Fuzzy Sync Button
                OutlinedButton(
                    onClick = { viewModel.performFuzzySync() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("fuzzy_sync_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElectricIndigo)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = ElectricIndigo
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Fuzzy Hash & Speech Consensus",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ElectricIndigo
                    )
                }
            }
        }

        // Processing indicator
        item {
            AnimatedVisibility(
                visible = syncState is SyncUiState.Processing,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (syncState is SyncUiState.Processing) {
                    val proc = syncState as SyncUiState.Processing
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = NeonCyan
                                )
                                Text(
                                    text = proc.stepDescription,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { proc.progress },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = NeonCyan,
                                trackColor = Color(0xFF1E293B),
                            )
                        }
                    }
                }
            }
        }

        // Sync Results Card
        item {
            AnimatedVisibility(
                visible = lastSyncResult != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                lastSyncResult?.let { result ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1E2E)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("sync_result_card")
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
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Synchronization Complete",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = TextPrimary
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = DarkSurface
                                ) {
                                    Text(
                                        text = result.method.displayName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = NeonCyan,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = DarkSurface
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Calibrated Offset", fontSize = 11.sp, color = TextSecondary)
                                        Text(
                                            text = "${if (result.detectedOffsetMs > 0) "+" else ""}${result.detectedOffsetMs} ms",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (result.detectedOffsetMs != 0L) NeonCyan else EmeraldSuccess
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = DarkSurface
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Speed / Drift", fontSize = 11.sp, color = TextSecondary)
                                        Text(
                                            text = "${String.format(java.util.Locale.US, "%.3f", result.driftFactor)}x",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }

                            ConfidenceGauge(
                                score = result.confidenceScore,
                                methodName = result.explanation
                            )

                            Button(
                                onClick = onNavigateToPlayer,
                                modifier = Modifier.fillMaxWidth().testTag("launch_player_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricIndigo,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Preview In Live Sync Player",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .border(1.dp, if (selected) NeonCyan else DarkBorder, RoundedCornerShape(8.dp)),
        color = if (selected) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceVariant
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) NeonCyan else TextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun OnlineMatchItemCard(
    match: OnlineSubtitleMatch,
    onDownloadAndSync: () -> Unit,
    onDownloadOnly: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("online_match_${match.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Source & Match Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(match.source.badgeColorHex).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = match.source.displayName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(match.source.badgeColorHex),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (match.matchType == HashMatchType.EXACT_MOVIE_HASH) EmeraldSuccess.copy(alpha = 0.2f) else DarkSurfaceVariant
                ) {
                    Text(
                        text = match.matchType.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (match.matchType == HashMatchType.EXACT_MOVIE_HASH) EmeraldSuccess else TextSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Release Title
            Text(
                text = match.releaseTitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Badges: Language, Format, Rating, Downloads
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Format Tag (ASS / SRT / VTT)
                val formatBg = when (match.format) {
                    SubtitleFormat.ASS -> Color(0xFFA855F7)
                    SubtitleFormat.VTT -> Color(0xFF10B981)
                    else -> NeonCyan
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = formatBg.copy(alpha = 0.25f)
                ) {
                    Text(
                        text = match.format.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = formatBg,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }

                Text(
                    text = "${match.language} • ★ ${match.rating} • ${match.downloadCount} dl",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDownloadAndSync,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color(0xFF090D16)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Download & Auto-Sync", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onDownloadOnly,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextSecondary)
                }
            }
        }
    }
}
