package com.musicplayer.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
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
import com.musicplayer.app.data.model.Playlist
import com.musicplayer.app.data.model.Song
import com.musicplayer.app.ui.viewmodel.LibraryViewModel
import com.musicplayer.app.ui.viewmodel.DownloadTask
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.musicplayer.app.LocalMusicViewModel
import com.musicplayer.app.ui.viewmodel.MusicViewModel

@Composable
fun LibraryScreen(
    onSongClick: (Song) -> Unit,
    onAddToFavorite: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
    musicViewModel: MusicViewModel = LocalMusicViewModel.current
) {
    val playlists by viewModel.playlists.collectAsState()
    val instaStatus by viewModel.instaPlaylistStatus.collectAsState()
    val activeInstaPlaylist by viewModel.activeInstaPlaylist.collectAsState()

    var isPlaylistSelectMode by remember { mutableStateOf(false) }
    val selectedPlaylistIds = remember { mutableStateListOf<String>() }
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var showInstaPlaylistDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<Playlist?>(null) }
    var showMergeDialog by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        musicViewModel.downloadMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!isPlaylistSelectMode) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                ) {
                    ExtendedFloatingActionButton(
                        onClick = { showInstaPlaylistDialog = true },
                        icon = { Icon(Icons.Default.Bolt, contentDescription = null) },
                        text = { Text("insta-playlist") },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FloatingActionButton(
                        onClick = { showCreateDialog = true },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create Playlist")
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(bottom = paddingValues.calculateBottomPadding())) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = when {
                                isPlaylistSelectMode -> "${selectedPlaylistIds.size} Selected"
                                else -> "Library"
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (!isPlaylistSelectMode) {
                            Text(
                                text = "${playlists.size} playlists",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    if (isPlaylistSelectMode) {
                        Row {
                            IconButton(onClick = {
                                if (selectedPlaylistIds.size == playlists.size - 2) { // -2 for favorites and downloads
                                    selectedPlaylistIds.clear()
                                    isPlaylistSelectMode = false
                                } else {
                                    selectedPlaylistIds.clear()
                                    selectedPlaylistIds.addAll(playlists.filter { it.id != "favorites_playlist" && it.id != "downloads_playlist" }.map { it.id })
                                }
                            }) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                            }
                            if (selectedPlaylistIds.size >= 2) {
                                IconButton(onClick = { showMergeDialog = true }) {
                                    Icon(Icons.Default.Merge, contentDescription = "Merge Playlists")
                                }
                            }
                            
                            if (selectedPlaylistIds.size == 1) {
                                val selectedId = selectedPlaylistIds.first()
                                val selectedPlaylist = playlists.find { it.id == selectedId }
                                if (selectedPlaylist != null) {
                                    IconButton(onClick = { showRenameDialog = selectedPlaylist }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Rename")
                                    }
                                    IconButton(onClick = {
                                        viewModel.togglePinnedStatus(selectedPlaylist)
                                        isPlaylistSelectMode = false
                                        selectedPlaylistIds.clear()
                                    }) {
                                        Icon(
                                            Icons.Default.PushPin,
                                            contentDescription = "Pin/Unpin",
                                            tint = if (selectedPlaylist.isPinned) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = {
                                viewModel.deletePlaylists(selectedPlaylistIds.toSet())
                                isPlaylistSelectMode = false
                                selectedPlaylistIds.clear()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                            }
                            IconButton(onClick = {
                                isPlaylistSelectMode = false
                                selectedPlaylistIds.clear()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Exit Selection")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Insta-Playlist Background Progress
                AnimatedVisibility(visible = activeInstaPlaylist != null) {
                    activeInstaPlaylist?.let { progress ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, 
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Building: ${progress.name}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${progress.status} (Added ${progress.added})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.cancelInstaPlaylist() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close, 
                                            contentDescription = "Cancel",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    progress = { progress.progress / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                )
                            }
                        }
                    }
                }

                PlaylistsGrid(
                    playlists = playlists.filter { it.id != "downloads_playlist" },
                    onPlaylistClick = { playlist ->
                        if (isPlaylistSelectMode) {
                            if (playlist.id != "favorites_playlist" && playlist.id != "downloads_playlist") {
                                if (selectedPlaylistIds.contains(playlist.id)) {
                                    selectedPlaylistIds.remove(playlist.id)
                                    if (selectedPlaylistIds.isEmpty()) isPlaylistSelectMode = false
                                } else {
                                    selectedPlaylistIds.add(playlist.id)
                                }
                            }
                        } else {
                            onPlaylistClick(playlist)
                        }
                    },
                    onLongClick = { playlist ->
                        if (!isPlaylistSelectMode && playlist.id != "favorites_playlist" && playlist.id != "downloads_playlist") {
                            isPlaylistSelectMode = true
                            selectedPlaylistIds.add(playlist.id)
                        }
                    },
                    isSelectMode = isPlaylistSelectMode,
                    selectedPlaylistIds = selectedPlaylistIds
                )
            }
        }
    }

    if (showCreateDialog) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Playlist") },
            text = {
                TextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (playlistName.isNotBlank()) {
                        viewModel.createPlaylist(playlistName)
                        showCreateDialog = false
                        playlistName = ""
                    }
                }) {
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

    if (showMergeDialog) {
        var mergedName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMergeDialog = false },
            title = { Text("Merge Playlists") },
            text = {
                Column {
                    Text("Merging ${selectedPlaylistIds.size} playlists into a new one.")
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = mergedName,
                        onValueChange = { mergedName = it },
                        placeholder = { Text("New merged playlist name") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (mergedName.isNotBlank()) {
                        viewModel.mergePlaylists(selectedPlaylistIds.toList(), mergedName)
                        showMergeDialog = false
                        isPlaylistSelectMode = false
                        selectedPlaylistIds.clear()
                    }
                }) {
                    Text("Merge")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMergeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRenameDialog != null) {
        var newName by remember { mutableStateOf(showRenameDialog?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename Playlist") },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("New name") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.renamePlaylist(showRenameDialog!!.id, newName)
                        showRenameDialog = null
                        isPlaylistSelectMode = false
                        selectedPlaylistIds.clear()
                    }
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showInstaPlaylistDialog) {
        var playlistName by remember { mutableStateOf("") }
        var songsText by remember { mutableStateOf("") }
        val isProcessing = instaStatus != null

        AlertDialog(
            onDismissRequest = { 
                showInstaPlaylistDialog = false 
            },
            title = { Text("Insta-Playlist") },
            text = {
                Column {
                    TextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        placeholder = { Text("Playlist Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = songsText,
                        onValueChange = { songsText = it },
                        placeholder = { Text("Paste songs here (one per line)\ne.g. Artist - Song Title") },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    
                    AnimatedVisibility(visible = isProcessing) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = instaStatus ?: "", 
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = playlistName.isNotBlank() && songsText.isNotBlank() && !isProcessing,
                    onClick = {
                        viewModel.createInstaPlaylist(playlistName, songsText) {
                            showInstaPlaylistDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Text("Generate")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showInstaPlaylistDialog = false
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistsGrid(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    onLongClick: (Playlist) -> Unit,
    isSelectMode: Boolean,
    selectedPlaylistIds: List<String>
) {
    if (playlists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No playlists created yet", color = MaterialTheme.colorScheme.secondary)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(playlists, key = { it.id }) { playlist ->
                val isSelected = selectedPlaylistIds.contains(playlist.id)
                val isFavorites = playlist.id == "favorites_playlist"
                val isDownloads = playlist.id == "downloads_playlist"

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.8f)
                        .combinedClickable(
                            onClick = { onPlaylistClick(playlist) },
                            onLongClick = { onLongClick(playlist) }
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    isFavorites -> Icons.Default.Favorite
                                    isDownloads -> Icons.Default.Download
                                    else -> Icons.Default.MusicNote
                                },
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = when {
                                    isFavorites -> Color.Red
                                    isDownloads -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${playlist.songCount} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (playlist.isPinned) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(16.dp)
                                    .align(Alignment.TopEnd),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (isSelectMode && !isFavorites && !isDownloads) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .align(Alignment.TopStart)
                            )
                        }
                    }
                }
            }
        }
    }
}
