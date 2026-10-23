package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.subsync.ui.components.SubSyncTopBar
import com.example.subsync.ui.screens.EditorScreen
import com.example.subsync.ui.screens.HashCatalogScreen
import com.example.subsync.ui.screens.LivePlayerScreen
import com.example.subsync.ui.screens.StudioScreen
import com.example.subsync.ui.theme.DarkBackground
import com.example.subsync.ui.theme.DarkBorder
import com.example.subsync.ui.theme.DarkSurface
import com.example.subsync.ui.theme.ElectricIndigo
import com.example.subsync.ui.theme.MyApplicationTheme
import com.example.subsync.ui.theme.NeonCyan
import com.example.subsync.ui.theme.TextPrimary
import com.example.subsync.ui.theme.TextSecondary
import com.example.subsync.ui.viewmodel.SubSyncViewModel

enum class SubSyncScreen(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    STUDIO("Studio", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_studio"),
    PLAYER("Live Player", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle, "nav_player"),
    EDITOR("Editor", Icons.Filled.EditNote, Icons.Outlined.EditNote, "nav_editor"),
    CATALOG("Hashes", Icons.Filled.Fingerprint, Icons.Outlined.Fingerprint, "nav_catalog")
}

class MainActivity : ComponentActivity() {

    private val viewModel: SubSyncViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                SubSyncApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun SubSyncApp(viewModel: SubSyncViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var currentScreen by remember { mutableStateOf(SubSyncScreen.STUDIO) }

    val selectedMedia by viewModel.selectedMedia.collectAsState()

    // Handle system back navigation
    BackHandler(enabled = currentScreen != SubSyncScreen.STUDIO) {
        currentScreen = SubSyncScreen.STUDIO
    }

    // Observe toast events
    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SubSyncTopBar(
                currentPresetName = selectedMedia?.title?.split(":")?.firstOrNull(),
                hashTag = selectedMedia?.openSubtitlesHash
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("subsync_bottom_navigation")
            ) {
                SubSyncScreen.entries.forEach { screen ->
                    val isSelected = currentScreen == screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentScreen = screen },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                fontSize = 11.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DarkBackground,
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag(screen.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                SubSyncScreen.STUDIO -> {
                    StudioScreen(
                        viewModel = viewModel,
                        onNavigateToPlayer = { currentScreen = SubSyncScreen.PLAYER }
                    )
                }
                SubSyncScreen.PLAYER -> {
                    LivePlayerScreen(
                        viewModel = viewModel
                    )
                }
                SubSyncScreen.EDITOR -> {
                    EditorScreen(
                        viewModel = viewModel
                    )
                }
                SubSyncScreen.CATALOG -> {
                    HashCatalogScreen(
                        viewModel = viewModel,
                        onNavigateToStudio = { currentScreen = SubSyncScreen.STUDIO }
                    )
                }
            }
        }
    }
}
