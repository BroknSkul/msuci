package com.musicplayer.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.musicplayer.app.LocalMusicViewModel
import com.musicplayer.app.data.model.Song
import com.musicplayer.app.ui.components.SongItem
import com.musicplayer.app.ui.viewmodel.ArtistDetailViewModel
import com.musicplayer.app.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArtistDetailScreen(
    artistName: String,
    onBackClick: () -> Unit,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
    musicViewModel: MusicViewModel = LocalMusicViewModel.current
) {
    val artistInfo by viewModel.artistInfo.collectAsState()
    val highResImageUrl by viewModel.highResImageUrl.collectAsState()
    val topTracks by viewModel.topTracks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val playlists by musicViewModel.allPlaylists.collectAsState()

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedSongs by remember { mutableStateOf(setOf<Song>()) }
    var showPlaylistDialog by remember { mutableStateOf(false) }

    LaunchedEffect(artistName) {
        viewModel.loadArtistDetails(artistName)
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = if (isSelectionMode) "${selectedSongs.size} Selected" else artistName, 
                        color = Color.White 
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) {
                            isSelectionMode = false
                            selectedSongs = emptySet()
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(
                            imageVector = if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back", 
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Header with Image and Stats
                    item {
                        ArtistHeader(
                            name = artistName,
                            imageUrl = highResImageUrl ?: artistInfo?.image?.find { it.size == "extralarge" }?.url,
                            listeners = artistInfo?.stats?.listeners,
                            playCount = artistInfo?.stats?.playcount
                        )
                    }

                    // Biography
                    artistInfo?.bio?.summary?.let { bio ->
                        if (bio.isNotBlank()) {
                            item {
                                ArtistBio(bio)
                            }
                        }
                    }

                    // Top Tracks Section
                    if (topTracks.isNotEmpty()) {
                        item {
                            Text(
                                text = "Songs",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(16.dp)
                            )
                        }

                        items(topTracks) { song ->
                            val isSelected = selectedSongs.contains(song)
                            SongItem(
                                song = song,
                                onClick = { 
                                    if (isSelectionMode) {
                                        selectedSongs = if (isSelected) selectedSongs - song else selectedSongs + song
                                        if (selectedSongs.isEmpty()) isSelectionMode = false
                                    } else {
                                        musicViewModel.playSong(song)
                                    }
                                },
                                showSelection = isSelectionMode,
                                isSelected = isSelected,
                                onSelect = {
                                    isSelectionMode = true
                                    selectedSongs = setOf(song)
                                },
                                onDeleteDownload = { musicViewModel.deleteDownload(song) }
                            )
                        }
                    }
                    
                    item { Spacer(modifier = Modifier.height(100.dp)) }
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
                        selectedSongs.forEach { musicViewModel.toggleFavorite(it) }
                        isSelectionMode = false
                        selectedSongs = emptySet()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Favorite, 
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
                    IconButton(onClick = {
                        selectedSongs.forEach { musicViewModel.addToQueue(it) }
                        isSelectionMode = false
                        selectedSongs = emptySet()
                    }) {
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
                            imageVector = Icons.Default.Download, 
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
                            imageVector = Icons.Default.Check, 
                            contentDescription = "Done", 
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
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

@Composable
fun ArtistHeader(
    name: String,
    imageUrl: String?,
    listeners: String?,
    playCount: String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startY = 400f
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            
            if (listeners != null || playCount != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${listeners ?: "0"} listeners",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Text(" • ", color = Color.Gray)
                    Text(
                        text = "${playCount ?: "0"} plays",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistBio(summary: String) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "About",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        val cleanBio = summary.replace(Regex("<.*?>"), "")
        
        Text(
            text = cleanBio,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray,
            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { isExpanded = !isExpanded }
        )
        
        if (cleanBio.length > 100) {
            Text(
                text = if (isExpanded) "Show Less" else "Read More",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { isExpanded = !isExpanded }
            )
        }
    }
}
