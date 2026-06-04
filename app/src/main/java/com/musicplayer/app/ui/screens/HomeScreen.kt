package com.musicplayer.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicplayer.app.ui.components.ModernAsyncImage
import com.musicplayer.app.LocalMusicViewModel
import com.musicplayer.app.data.model.Playlist
import com.musicplayer.app.data.model.Song
import com.musicplayer.app.ui.viewmodel.ArtistDisplayData
import com.musicplayer.app.ui.viewmodel.HomeViewModel
import com.musicplayer.app.ui.viewmodel.MusicViewModel

@Composable
fun HomeScreen(
    onSongClick: (Song) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onArtistClick: (String) -> Unit,
    onAddToFavorite: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onSettingsClick: () -> Unit,
    onNotificationClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    musicViewModel: MusicViewModel = LocalMusicViewModel.current
) {
    val allPlaylists by viewModel.allPlaylists.collectAsState()
    val recentArtists by viewModel.recentArtistsData.collectAsState()
    val recentSongs by viewModel.recentSongs.collectAsState()
    val playlists by musicViewModel.allPlaylists.collectAsState()

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedSongs by remember { mutableStateOf(setOf<Song>()) }
    var showPlaylistDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                bottom = 120.dp,
                start = 16.dp,
                end = 16.dp
            )
        ) {
            item {
                HomeHeader(onSettingsClick, onNotificationClick)
            }

            // 1. TOP SECTION: RECENT ARTISTS
            item {
                SectionHeader("Top Artists")
                val displayedArtists = recentArtists.filter { 
                    !it.imageUrl.isNullOrBlank() && 
                    it.name != "Unknown Artist" && 
                    it.name.isNotBlank() 
                }
                if (displayedArtists.isEmpty()) {
                    Text("Play some music to see artists here", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        items(displayedArtists) { artist ->
                            RecentArtistCard(artist = artist, onClick = { onArtistClick(artist.name) })
                        }
                    }
                }
            }

            // 2. MIDDLE SECTION: ALL PLAYLISTS (Rectangular)
            item {
                SectionHeader("Playlists")
                if (allPlaylists.isEmpty()) {
                    Text("No playlists yet", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        items(allPlaylists) { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist) }
                            )
                        }
                    }
                }
            }

            // 3. BOTTOM SECTION: RECENTLY PLAYED
            item {
                SectionHeader("Recently Played")
                if (recentSongs.isEmpty()) {
                    Text("Your history will appear here", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        items(recentSongs) { song ->
                            val isSelected = selectedSongs.contains(song)
                            SquareSongCard(
                                song = song,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onClick = { 
                                    if (isSelectionMode) {
                                        selectedSongs = if (isSelected) selectedSongs - song else selectedSongs + song
                                        if (selectedSongs.isEmpty()) isSelectionMode = false
                                    } else {
                                        onSongClick(song)
                                    }
                                },
                                onLongClick = {
                                    isSelectionMode = true
                                    selectedSongs = setOf(song)
                                }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(100.dp)) }
        }

        // Selection Bar
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp, start = 32.dp, end = 32.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(32.dp),
                color = Color(0xFFC9A9A6), 
                tonalElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        selectedSongs.forEach { onAddToFavorite(it) }
                        isSelectionMode = false
                        selectedSongs = emptySet()
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite, 
                            contentDescription = "Favorite", 
                            tint = Color.Black,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    IconButton(onClick = {
                        showPlaylistDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd, 
                            contentDescription = "Add to Playlist", 
                            tint = Color.Black,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            selectedSongs.forEach { onAddToQueue(it) }
                            isSelectionMode = false
                            selectedSongs = emptySet()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay, 
                            contentDescription = "Add to Queue", 
                            tint = Color.Black,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    IconButton(onClick = {
                        selectedSongs.forEach { musicViewModel.downloadSong(it) }
                        isSelectionMode = false
                        selectedSongs = emptySet()
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Download, 
                            contentDescription = "Download", 
                            tint = Color.Black,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            isSelectionMode = false
                            selectedSongs = emptySet()
                        },
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.5f), CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check, 
                            contentDescription = "Done", 
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        if (showPlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showPlaylistDialog = false },
                title = { Text("Add to Playlist") },
                text = {
                    LazyColumn {
                        items(playlists) { playlist ->
                            ListItem(
                                headlineContent = { Text(playlist.name) },
                                leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) },
                                modifier = Modifier.clickable {
                                    selectedSongs.forEach { song ->
                                        musicViewModel.addSongToPlaylist(song, playlist.id)
                                    }
                                    showPlaylistDialog = false
                                    isSelectionMode = false
                                    selectedSongs = emptySet()
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPlaylistDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(280.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1A1A))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (playlist.id == "favorites_playlist") Icons.Rounded.Favorite else Icons.Rounded.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (playlist.id == "favorites_playlist") Color.Red else MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.songCount} songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun HomeHeader(onSettingsClick: () -> Unit, onNotificationClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "msuci",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 32.sp,
                letterSpacing = (-1.5).sp,
                fontWeight = FontWeight.ExtraBold // Hardcoded to exclude from scaling
            ),
            color = MaterialTheme.colorScheme.primary
        )
        Row {
            IconButton(onClick = onNotificationClick) {
                Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = Color.White)
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SquareSongCard(
    song: Song,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val displayThumbnail = remember(song.id, song.thumbnailUrl) {
        if (song.thumbnailUrl.isBlank() && song.id.length == 11) {
            "https://i.ytimg.com/vi/${song.id}/maxresdefault.jpg"
        } else {
            song.thumbnailUrl
        }
    }

    Column(
        modifier = Modifier
            .width(140.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            ModernAsyncImage(
                model = displayThumbnail,
                contentDescription = null,
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A1A1A)),
                contentScale = ContentScale.Crop,
                alpha = if (isSelected) 0.6f else 1f
            )
            
            if (displayThumbnail.isBlank()) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.Gray.copy(alpha = 0.5f)
                )
            }
            
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color(0xFFC9A9A6) else Color.White.copy(alpha = 0.3f))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RecentArtistCard(artist: ArtistDisplayData, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            if (!artist.imageUrl.isNullOrBlank()) {
                ModernAsyncImage(
                    model = artist.imageUrl,
                    contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = artist.name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
