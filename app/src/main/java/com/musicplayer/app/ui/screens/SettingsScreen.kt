package com.musicplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicplayer.app.ui.viewmodel.AppViewModel
import com.musicplayer.app.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    appViewModel: AppViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val useBlurredBackground by appViewModel.useBlurredBackground.collectAsState()
    val isGaplessPlaybackEnabled by settingsViewModel.isGaplessPlaybackEnabled.collectAsState()
    val playlists by settingsViewModel.playlists.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }

    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settingsViewModel.exportMessage.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Long
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Data & Backup Section
            SettingsSectionHeader("Data & Backup")
            
            Button(
                onClick = { showExportDialog = true },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Playlist to .txt")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp).alpha(0.1f))

            // Appearance Section
            SettingsSectionHeader("Appearance")
            
            SettingsToggleRow(
                text = "Blurred Background in Player",
                checked = useBlurredBackground,
                onCheckedChange = { appViewModel.toggleBlurredBackground() }
            )

            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Font Thickness",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            val fontWeightScale by appViewModel.fontWeightScale.collectAsState()
            Slider(
                value = fontWeightScale,
                onValueChange = { appViewModel.setFontWeightScale(it) },
                valueRange = 0.5f..2.0f,
                steps = 15,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                )
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp).alpha(0.1f))

            // Playback Section
            SettingsSectionHeader("Playback")

            SettingsToggleRow(
                text = "Gapless Playback",
                checked = isGaplessPlaybackEnabled,
                onCheckedChange = { settingsViewModel.toggleGaplessPlayback(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp).alpha(0.1f))

            // Storage Section
            SettingsSectionHeader("Storage")
            Button(
                onClick = { showClearCacheDialog = true },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Clear Cache (Delete Downloads)")
            }

            // Hidden License Section directly below Clear Cache with minimal gap
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "App License Information",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showLicenseDialog = true }
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showLicenseDialog) {
        Dialog(
            onDismissRequest = { showLicenseDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0F0F0F)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    IconButton(
                        onClick = { showLicenseDialog = false },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Modern Header with Icon
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Gavel,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "LEGAL STUFF",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 2.sp
                        )
                    }
                    
                    Text(
                        text = "BROKNSKUL'S NO-PROFIT LICENSE",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Text(
                        text = "Copyright © 2024 BroknSkul",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Intro Card
                    LicenseCard {
                        Text(
                            text = "Yo! Welcome to msuci. I’m sharing this because I love music and I love code, but we’ve gotta have some ground rules so things stay chill.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = 24.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    LicensePoint(
                        title = "PERSONAL USE ONLY:",
                        subtitle = "The \"Listen But Don't Touch\" Rule",
                        content = "You are free to use this app for your own personal jam sessions. Listen to all the music you want, but don't go poking around in the guts of the code or trying to change how it works. It’s built this way for a reason, so just enjoy the vibes and leave the engine alone."
                    )

                    LicensePoint(
                        title = "THE COMMERCIAL BAN:",
                        subtitle = "Hands off the Hustle",
                        content = "You do not have permission to use this code for any commercial purpose. No selling the code, no using it in a business, and no making money off my hard work."
                    )

                    LicensePoint(
                        title = "UNDERGROUND ONLY:",
                        subtitle = "Don't Blow My Cover",
                        content = "Don't even think about launching this commercially on the Play Store or anywhere else. Look, we’re out here \"borrowing\" streams and retrieving songs in a way that’s… let’s say legally spicy. Since we are illegally retrieving songs, it is illegal, and if you try to make money off this, the big guys will notice, and my ass would get caught right along with yours. Let’s not do that. Keep it a hobby."
                    )

                    LicensePoint(
                        title = "OWNERSHIP:",
                        subtitle = "The Landlord",
                        content = "I, BroknSkul, keep full ownership of this code and the project. You're a guest in this codebase, not the landlord!"
                    )

                    LicensePoint(
                        title = "DISCLAIMER:",
                        subtitle = "Zero Responsibility",
                        content = "This code is provided \"as is.\" If it somehow causes your phone to gain sentience and start judging your 3:00 AM playlist choices, that’s on you. No warranties included!"
                    )

                    Spacer(modifier = Modifier.height(64.dp))
                }
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Select Playlist to Export") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (playlists.isEmpty()) {
                        Text("No playlists found.")
                    } else {
                        playlists.forEach { playlist ->
                            TextButton(
                                onClick = {
                                    settingsViewModel.exportPlaylist(playlist)
                                    showExportDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(playlist.name, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Cache") },
            text = { Text("Are you sure you want to delete all downloaded songs? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsViewModel.clearCache()
                        showClearCacheDialog = false
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsToggleRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}

@Composable
fun LicenseCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
fun LicensePoint(title: String, subtitle: String, content: String) {
    Column(modifier = Modifier.padding(bottom = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }
        
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 4.dp)
        )

        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            lineHeight = 22.sp,
            modifier = Modifier.padding(start = 60.dp, top = 8.dp)
        )
    }
}
