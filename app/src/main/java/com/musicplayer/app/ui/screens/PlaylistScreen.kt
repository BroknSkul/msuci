package com.musicplayer.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicplayer.app.ui.components.ModernAsyncImage
import com.musicplayer.app.LocalMusicViewModel
import com.musicplayer.app.data.model.Song
import com.musicplayer.app.ui.components.SongItem
import com.musicplayer.app.ui.viewmodel.MusicViewModel
import com.musicplayer.app.ui.viewmodel.PlaylistViewModel
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistId: String,
    onSongClick: (Song) -> Unit,
    onAddToFavorite: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onBackClick: () -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
    musicViewModel: MusicViewModel = LocalMusicViewModel.current
) {
    val context = LocalContext.current
    val songs by viewModel.filteredSongs.collectAsState()
    val allSongs by viewModel.songsInPlaylist.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentPlaylist by viewModel.currentPlaylist.collectAsState()
    val playlists by musicViewModel.allPlaylists.collectAsState()
    val downloadProgress by musicViewModel.downloadProgress.collectAsState()
    
    var selectedSongs by remember { mutableStateOf(setOf<Song>()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    val dustyRose = Color(0xFFC9A9A6)
    
    val listState = rememberLazyListState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                val fileName = "playlist_${playlistId}_thumb.jpg"
                val file = File(context.filesDir, fileName)
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                    viewModel.updatePlaylistThumbnail(playlistId, file.absolutePath)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    )

    LaunchedEffect(playlistId) {
        viewModel.loadSongsInPlaylist(playlistId)
    }
    
    // Hidden initially by scrolling to item 1 (the header)
    var initialScrollDone by remember { mutableStateOf(false) }
    LaunchedEffect(allSongs) {
        if (allSongs.isNotEmpty() && !initialScrollDone) {
            listState.scrollToItem(1)
            initialScrollDone = true
        }
    }

    LaunchedEffect(Unit) {
        musicViewModel.downloadMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. COMPACT SEARCH BAR
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1A1A1A))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.Gray
                            )
                            Spacer(Modifier.width(8.dp))
                            
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(Color.White),
                                singleLine = true,
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = if (playlistId == "downloads_playlist") "search downloads" else "find in playlist",
                                            color = Color.Gray.copy(alpha = 0.6f),
                                            fontSize = 13.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }
                }

                // 2. Playlist Cover & Info
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            PlaylistHeader(
                                currentPlaylist?.thumbnailUrl,
                                allSongs.take(4).map { song ->
                                    val localThumb = File(context.getExternalFilesDir(null), "music_storage/${song.id}.jpg")
                                    if (localThumb.exists()) {
                                        localThumb
                                    } else if (song.thumbnailUrl.isBlank() && song.id.length == 11) {
                                        "https://i.ytimg.com/vi/${song.id}/maxresdefault.jpg"
                                    } else if (song.thumbnailUrl.startsWith("/") && !File(song.thumbnailUrl).exists()) {
                                        "https://i.ytimg.com/vi/${song.id}/maxresdefault.jpg"
                                    } else {
                                        song.thumbnailUrl
                                    }
                                },
                                onChangeCoverClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = currentPlaylist?.name ?: "Playlist",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "${allSongs.size} songs",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                            }
                        }
                        
                        // Fading overlay at the bottom of the header area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black)
                                    )
                                )
                        )
                    }
                }

                // 3. Controls Row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Modern Download Button
                            IconButton(
                                onClick = { allSongs.forEach { musicViewModel.downloadSong(it) } },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = "Download all",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Modern Shuffle & Play
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { musicViewModel.shufflePlaylist(allSongs) },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            FloatingActionButton(
                                onClick = { musicViewModel.playPlaylist(allSongs) },
                                containerColor = dustyRose,
                                contentColor = Color.Black,
                                shape = CircleShape,
                                modifier = Modifier.size(58.dp),
                                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", modifier = Modifier.size(38.dp))
                            }
                        }
                    }
                }

                // 4. Songs List
                items(songs, key = { it.id }) { song ->
                    val isSelected = selectedSongs.contains(song)
                    
                    // Proactively fetch high-res metadata if missing
                    LaunchedEffect(song.id, song.thumbnailUrl) {
                        if (!musicViewModel.isHighRes(song.thumbnailUrl) && !song.thumbnailUrl.startsWith("/")) {
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
                                musicViewModel.playPlaylistFromSong(allSongs, song)
                                onSongClick(song)
                            }
                        },
                        showSelection = isSelectionMode,
                        isSelected = isSelected,
                        downloadProgress = downloadProgress[song.id],
                        onCancelDownload = { musicViewModel.cancelDownload(song.id) },
                        onDownloadClick = { musicViewModel.downloadSong(song) },
                        onDeleteDownload = { musicViewModel.deleteDownload(song) },
                        onSelect = {
                            isSelectionMode = true
                            selectedSongs = setOf(song)
                        }
                    )
                }
                
                item { Spacer(modifier = Modifier.height(100.dp)) }
            }

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
                    color = dustyRose, 
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
                            if (selectedSongs.size == songs.size) {
                                selectedSongs = emptySet()
                                isSelectionMode = false
                            } else {
                                selectedSongs = songs.toSet()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.SelectAll, 
                                contentDescription = "Select All", 
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
                                imageVector = Icons.Default.Download, 
                                contentDescription = "Download", 
                                tint = Color.Black,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        
                        IconButton(
                            onClick = {
                                selectedSongs.forEach { viewModel.removeSongFromPlaylist(it.id, playlistId) }
                                isSelectionMode = false
                                selectedSongs = emptySet()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete, 
                                contentDescription = "Remove", 
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
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
}

@Composable
fun PlaylistHeader(
    thumbnailUrl: String?,
    songThumbnails: List<Any>,
    onChangeCoverClick: () -> Unit
) {
    var showEditButton by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Cover Image
        Box(
            modifier = Modifier
                .size(280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1A1A))
                .clickable { showEditButton = !showEditButton },
            contentAlignment = Alignment.Center
        ) {
            if (!thumbnailUrl.isNullOrBlank()) {
                ModernAsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (songThumbnails.isNotEmpty()) {
                // Grid of 4 thumbnails
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        ModernAsyncImage(
                            model = songThumbnails.getOrNull(0),
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        ModernAsyncImage(
                            model = songThumbnails.getOrNull(1),
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                    Row(modifier = Modifier.weight(1f)) {
                        ModernAsyncImage(
                            model = songThumbnails.getOrNull(2),
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        ModernAsyncImage(
                            model = songThumbnails.getOrNull(3),
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
            }
            
            // Edit Overlay button
            AnimatedVisibility(
                visible = showEditButton,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Surface(
                    onClick = { 
                        showEditButton = false
                        onChangeCoverClick() 
                    },
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Change cover image",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
