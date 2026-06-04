package com.musicplayer.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Check
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicplayer.app.ui.components.ModernAsyncImage
import com.musicplayer.app.LocalMusicViewModel
import com.musicplayer.app.data.model.Song
import com.musicplayer.app.ui.components.SongItem
import com.musicplayer.app.ui.viewmodel.MusicViewModel
import com.musicplayer.app.ui.viewmodel.SearchViewModel

@Composable
fun SearchScreen(
    onSongClick: (Song) -> Unit,
    onAddToFavorite: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    searchViewModel: SearchViewModel = hiltViewModel(),
    musicViewModel: MusicViewModel = LocalMusicViewModel.current
) {
    var query by remember { mutableStateOf("") }
    val searchResults by searchViewModel.searchResults.collectAsState()
    val isSearching by searchViewModel.isSearching.collectAsState()
    val searchHistory by searchViewModel.searchHistory.collectAsState()
    val playlists by musicViewModel.allPlaylists.collectAsState()
    val downloadedSongIds by musicViewModel.downloadedSongIds.collectAsState()
    
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedSongs by remember { mutableStateOf(setOf<Song>()) }
    var showPlaylistDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp)
        ) {
            Text(
                text = if (isSelectionMode) "${selectedSongs.size} Selected" else "Search",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!isSelectionMode) {
                TextField(
                    value = query,
                    onValueChange = { 
                        query = it
                        searchViewModel.search(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search songs...", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { 
                                query = ""
                                searchViewModel.search("")
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1A1A1A),
                        unfocusedContainerColor = Color(0xFF1A1A1A),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (query.isEmpty() && searchHistory.isNotEmpty() && !isSelectionMode) {
                Text(
                    text = "Recent Searches",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn {
                    items(searchHistory) { historyItem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    query = historyItem.query
                                    searchViewModel.search(historyItem.query)
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!historyItem.thumbnailUrl.isNullOrEmpty()) {
                                ModernAsyncImage(
                                    model = historyItem.thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF1A1A1A))
                                )
                            } else {
                                Icon(
                                    Icons.Default.History, 
                                    contentDescription = null, 
                                    tint = Color.Gray, 
                                    modifier = Modifier.size(24.dp).padding(start = 8.dp, end = 8.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = historyItem.query, modifier = Modifier.weight(1f), color = Color.White)
                            IconButton(onClick = { searchViewModel.deleteHistoryItem(historyItem.query) }) {
                                Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(18.dp), tint = Color.Gray)
                            }
                        }
                    }
                }
            } else if (isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (searchResults.isEmpty() && query.lowercase().contains("d4vd")) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "no killer shall be supported here ❌",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchResults) { song ->
                        val isSelected = selectedSongs.contains(song)
                        val isDownloaded = downloadedSongIds.contains(song.id)
                        
                        // Prefetch when song comes into view to speed up play start
                        LaunchedEffect(song.id) {
                            musicViewModel.prefetchStreamUrl(song)
                        }
                        
                        // Proactively fetch high-res metadata if missing
                        LaunchedEffect(song.id, song.thumbnailUrl) {
                            if (!musicViewModel.isHighRes(song.thumbnailUrl)) {
                                musicViewModel.fetchSongMetadata(song)
                            }
                        }

                        SongItem(
                            song = song,
                            onClick = { 
                                if (isSelectionMode) {
                                    selectedSongs = if (isSelected) selectedSongs - song else selectedSongs + song
                                    if (selectedSongs.isEmpty()) isSelectionMode = false
                                } else {
                                    onSongClick(song)
                                    searchViewModel.onSongClicked(song, query)
                                }
                            },
                            isDownloaded = isDownloaded,
                            showSelection = isSelectionMode,
                            isSelected = isSelected,
                            onSelect = {
                                isSelectionMode = true
                                selectedSongs = setOf(song)
                            },
                            onDownloadClick = { musicViewModel.downloadSong(song) },
                            onDeleteDownload = { musicViewModel.deleteDownload(song) }
                        )
                    }
                }
            }
        }

        // Selection Bar (Old Mini Bar)
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
                        isSelectionMode = false
                        selectedSongs = emptySet()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close, 
                            contentDescription = "Cancel", 
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
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
                            musicViewModel.deleteSongs(selectedSongs)
                            isSelectionMode = false
                            selectedSongs = emptySet()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete, 
                            contentDescription = "Delete", 
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        if (showPlaylistDialog) {
            var showCreateDialog by remember { mutableStateOf(false) }
            var newPlaylistName by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showPlaylistDialog = false },
                title = { Text("Add to Playlist") },
                text = {
                    LazyColumn {
                        item {
                            ListItem(
                                headlineContent = { Text("Create New Playlist") },
                                leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                                modifier = Modifier.clickable { 
                                    showCreateDialog = true
                                }
                            )
                        }
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

            if (showCreateDialog) {
                AlertDialog(
                    onDismissRequest = { showCreateDialog = false },
                    title = { Text("New Playlist") },
                    text = {
                        TextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            placeholder = { Text("Playlist name") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (newPlaylistName.isNotBlank()) {
                                    musicViewModel.createPlaylist(newPlaylistName, songs = selectedSongs)
                                    showCreateDialog = false
                                    showPlaylistDialog = false
                                    isSelectionMode = false
                                    selectedSongs = emptySet()
                                    newPlaylistName = ""
                                }
                            }
                        ) {
                            Text("Create")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreateDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
