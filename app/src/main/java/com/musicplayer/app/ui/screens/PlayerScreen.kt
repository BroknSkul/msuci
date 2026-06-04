package com.musicplayer.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.musicplayer.app.LocalMusicViewModel
import com.musicplayer.app.data.model.Song
import com.musicplayer.app.ui.components.AlbumCover
import com.musicplayer.app.ui.components.MusicWaveLoading
import com.musicplayer.app.ui.components.PlayerControls
import com.musicplayer.app.ui.viewmodel.AppViewModel
import com.musicplayer.app.ui.viewmodel.MusicViewModel
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    musicViewModel: MusicViewModel = LocalMusicViewModel.current,
    appViewModel: AppViewModel = hiltViewModel()
) {
    val playerState by musicViewModel.playerState.collectAsState()
    val isFavorite by musicViewModel.isFavorite.collectAsState()
    val playlists by musicViewModel.allPlaylists.collectAsState()
    val isGlassMode by appViewModel.isGlassMode.collectAsState()
    val useBlurredBackground by appViewModel.useBlurredBackground.collectAsState()
    val lyrics by musicViewModel.lyrics.collectAsState()
    val syncedLyrics by musicViewModel.syncedLyrics.collectAsState()
    val currentLyricIndex by musicViewModel.currentLyricIndex.collectAsState()
    val albumCoverUrl by musicViewModel.albumCoverUrl.collectAsState()
    val downloadProgress by musicViewModel.downloadProgress.collectAsState()
    val downloadedSongIds by musicViewModel.downloadedSongIds.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    
    // Song menu state
    var selectedSongForMenu by remember { mutableStateOf<Pair<Int, Song>?>(null) }
    var showSongMenu by remember { mutableStateOf(false) }
    var showAddToPlaylistFromMenu by remember { mutableStateOf(false) }

    // State to toggle between Album Art and Video Thumbnail
    var showVideoThumbnail by remember { mutableStateOf(false) }

    // Smooth dragging state
    var sliderDraggingValue by remember { mutableStateOf<Float?>(null) }

    // Colors
    val dustyRose = Color(0xFFC9A9A6)

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val bestThumbnailUrl = remember(playerState.currentSong, albumCoverUrl) {
        val song = playerState.currentSong
        if (albumCoverUrl != null) {
            albumCoverUrl
        } else if (song != null && song.id.length == 11) {
            "https://i.ytimg.com/vi/${song.id}/maxresdefault.jpg"
        } else {
            song?.thumbnailUrl
        }
    }

    var currentDisplayCover by remember(bestThumbnailUrl) { mutableStateOf(bestThumbnailUrl) }

    // Split lyrics into description and clean lyrics
    val (extractedDescription, cleanLyrics) = remember(lyrics) {
        val currentLyrics = lyrics ?: ""
        val firstMarkerIndex = currentLyrics.indexOf("[")
        if (firstMarkerIndex > 0) {
            val desc = currentLyrics.substring(0, firstMarkerIndex).trim()
            val lyrs = currentLyrics.substring(firstMarkerIndex).trim()
            desc to lyrs
        } else {
            "" to currentLyrics
        }
    }

    LaunchedEffect(Unit) {
        musicViewModel.downloadMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isGlassMode) Color.Transparent else Color.Black)
    ) {
        if (useBlurredBackground) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(currentDisplayCover ?: "")
                    .listener(onError = { _, _ ->
                        if (currentDisplayCover != playerState.currentSong?.thumbnailUrl) {
                            currentDisplayCover = playerState.currentSong?.thumbnailUrl
                        }
                    })
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(30.dp),
                contentScale = ContentScale.Crop,
                alpha = 0.5f
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(modifier = Modifier.height(64.dp))

            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .sizeIn(maxWidth = 318.dp, maxHeight = 318.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            if (delta < -20) showVideoThumbnail = true
                            if (delta > 20) showVideoThumbnail = false
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = showVideoThumbnail,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        if (targetState) {
                            slideInVertically { height -> height } + fadeIn() togetherWith
                                    slideOutVertically { height -> -height } + fadeOut()
                        } else {
                            slideInVertically { height -> -height } + fadeIn() togetherWith
                                    slideOutVertically { height -> height } + fadeOut()
                        }
                    },
                    label = "CoverToggle"
                ) { isVideo ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AlbumCover(
                            thumbnailUrl = currentDisplayCover,
                            fallbackUrl = playerState.currentSong?.thumbnailUrl,
                            alpha = if (playerState.isLoading) 0.6f else 1f,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (playerState.isLoading) {
                            MusicWaveLoading(modifier = Modifier.width(120.dp).height(60.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = playerState.currentSong?.title ?: "Unknown Title",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                text = playerState.currentSong?.artist ?: "Unknown Artist",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { playerState.currentSong?.let { musicViewModel.toggleFavorite(it) } },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    val currentSong = playerState.currentSong
                    val progress = currentSong?.let { downloadProgress[it.id] }
                    val isDownloaded = currentSong?.let { downloadedSongIds.contains(it.id) } ?: false
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isDownloaded && progress == null) {
                            IconButton(
                                onClick = { currentSong?.let { musicViewModel.deleteDownload(it) } },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Download",
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { currentSong?.let { musicViewModel.downloadSong(it) } },
                            enabled = !isDownloaded && progress == null,
                            modifier = Modifier.size(40.dp)
                        ) {
                            if (progress != null) {
                                CircularProgressIndicator(
                                    progress = { if (progress > 0f) progress else 0.1f },
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = if (isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                                    contentDescription = "Download",
                                    tint = Color.White.copy(alpha = if (isDownloaded) 1f else 0.7f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Slider(
                    value = sliderDraggingValue ?: playerState.progress.toFloat(),
                    onValueChange = { sliderDraggingValue = it },
                    onValueChangeFinished = {
                        sliderDraggingValue?.let { 
                            musicViewModel.seekTo(it.toLong())
                            sliderDraggingValue = null
                        }
                    },
                    valueRange = 0f..playerState.duration.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    thumb = {
                        Box(
                            modifier = Modifier.size(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .background(Color.White, CircleShape)
                            )
                        }
                    }
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatTime((sliderDraggingValue ?: playerState.progress.toFloat()).toLong()), 
                        color = Color.White.copy(alpha = 0.6f), 
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(formatTime(playerState.duration), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                }

                Spacer(modifier = Modifier.height(8.dp))

                PlayerControls(
                    isPlaying = playerState.isPlaying,
                    repeatMode = playerState.repeatMode,
                    onPlayPauseClick = { musicViewModel.togglePlayPause() },
                    onSkipNextClick = { musicViewModel.skipNext() },
                    onSkipPreviousClick = { musicViewModel.skipPrevious() },
                    onRepeatModeClick = {
                        val nextMode = (playerState.repeatMode + 1) % 3
                        musicViewModel.setRepeatMode(nextMode)
                    },
                    onAddPlaylistClick = { showPlaylistDialog = true },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showLyricsSheet = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Notes,
                            contentDescription = "Lyrics",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { showQueueSheet = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Up Next",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )

        if (showLyricsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showLyricsSheet = false },
                sheetState = sheetState,
                containerColor = Color.Black,
                scrimColor = Color.Black.copy(alpha = 0.8f),
                dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.4f)) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.9f)
                        .padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = playerState.currentSong?.title ?: "Unknown Title",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-1).sp,
                                lineHeight = 38.sp
                            ),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        
                        IconButton(
                            onClick = { showInfoDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = "Song Info",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
                    ) {
                        Text(
                            text = "Lyrics",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = dustyRose
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = playerState.currentSong?.artist ?: "Unknown Artist",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        if (lyrics == null || lyrics?.startsWith("Searching") == true) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = dustyRose, strokeWidth = 3.dp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Loading lyrics...", color = Color.White.copy(alpha = 0.5f))
                            }
                        } else {
                            val listState = rememberLazyListState()
                            
                            LaunchedEffect(currentLyricIndex) {
                                if (currentLyricIndex >= 0) {
                                    listState.animateScrollToItem(currentLyricIndex, scrollOffset = -200)
                                }
                            }

                            if (!syncedLyrics.isNullOrEmpty()) {
                                SelectionContainer {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(vertical = 32.dp)
                                    ) {
                                        itemsIndexed(syncedLyrics!!, key = { index, _ -> index }) { index, line ->
                                            val isCurrent = index == currentLyricIndex
                                            val opacity by animateFloatAsState(
                                                targetValue = if (isCurrent) 1f else 0.4f,
                                                animationSpec = tween(durationMillis = 200),
                                                label = "lyricOpacity"
                                            )
                                            val scale by animateFloatAsState(
                                                targetValue = if (isCurrent) 1.05f else 1f,
                                                animationSpec = tween(durationMillis = 200),
                                                label = "lyricScale"
                                            )
                                            
                                            Text(
                                                text = line.text,
                                                style = MaterialTheme.typography.headlineMedium.copy(
                                                    lineHeight = 36.sp,
                                                    fontSize = if (isCurrent) 22.sp else 20.sp,
                                                    fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Bold,
                                                    letterSpacing = (-0.5).sp
                                                ),
                                                color = Color.White,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 12.dp)
                                                    .graphicsLayer {
                                                        scaleX = scale
                                                        scaleY = scale
                                                        alpha = opacity
                                                    }
                                                    .clickable { musicViewModel.seekTo(line.time) }
                                                    .animateItemPlacement()
                                            )
                                        }
                                        item { Spacer(modifier = Modifier.height(200.dp)) }
                                    }
                                }
                            } else {
                                SelectionContainer {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(vertical = 32.dp)
                                    ) {
                                        val allLines = cleanLyrics.split("\n")
                                        allLines.forEach { line ->
                                            val trimmedLine = line.trim()
                                            if (trimmedLine.isEmpty()) {
                                                Spacer(modifier = Modifier.height(32.dp))
                                            } else if (trimmedLine.startsWith("[") && trimmedLine.contains("]")) {
                                                Text(
                                                    text = trimmedLine.replace("[", "").replace("]", "").uppercase(),
                                                    style = MaterialTheme.typography.labelLarge.copy(
                                                        color = Color.White.copy(alpha = 0.3f),
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 2.sp
                                                    ),
                                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = trimmedLine,
                                                    style = MaterialTheme.typography.headlineMedium.copy(
                                                        lineHeight = 32.sp,
                                                        fontSize = 19.sp,
                                                        letterSpacing = (-0.5).sp
                                                    ),
                                                    color = Color.White,
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(120.dp))
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Black
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 32.dp, top = 8.dp)
                        ) {
                            Slider(
                                value = sliderDraggingValue ?: playerState.progress.toFloat(),
                                onValueChange = { sliderDraggingValue = it },
                                onValueChangeFinished = {
                                    sliderDraggingValue?.let {
                                        musicViewModel.seekTo(it.toLong())
                                        sliderDraggingValue = null
                                    }
                                },
                                valueRange = 0f..playerState.duration.toFloat().coerceAtLeast(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                ),
                                thumb = {
                                    Box(
                                        modifier = Modifier.size(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(Color.White, CircleShape)
                                        )
                                    }
                                }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { musicViewModel.skipPrevious() },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SkipPrevious,
                                        contentDescription = "Previous",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                val playPauseScale by animateFloatAsState(if (playerState.isPlaying) 1.05f else 1f, label = "playPauseScaleSheet")
                                IconButton(
                                    onClick = { musicViewModel.togglePlayPause() },
                                    modifier = Modifier
                                        .size(64.dp)
                                        .scale(playPauseScale)
                                ) {
                                    Icon(
                                        imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = { musicViewModel.skipNext() },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SkipNext,
                                        contentDescription = "Next",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showInfoDialog) {
            val descriptionText = extractedDescription.ifBlank {
                "Track by ${playerState.currentSong?.artist}\nAlbum: ${playerState.currentSong?.album ?: "Unknown"}\n\nLyrics provided by Genius."
            }
            
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                icon = { Icon(Icons.Rounded.Info, contentDescription = null, tint = dustyRose) },
                title = { Text("About this song", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = descriptionText,
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 24.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text("Close", color = dustyRose)
                    }
                },
                containerColor = Color(0xFF1A1A1A),
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.8f)
            )
        }

        if (showQueueSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                sheetState = sheetState,
                containerColor = Color.Black,
                scrimColor = Color.Black.copy(alpha = 0.8f),
                dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.4f)) }
            ) {
                val density = LocalDensity.current
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.9f)
                        .padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playerState.currentSong?.title ?: "Nothing Playing",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = playerState.currentSong?.artist ?: "Unknown Artist",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        IconButton(
                            onClick = { musicViewModel.toggleShuffle() },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (playerState.isShuffleEnabled) dustyRose.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (playerState.isShuffleEnabled) dustyRose else Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    Text(
                        text = "UP NEXT",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        ),
                        color = dustyRose,
                        modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
                    )
                    
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        state = rememberLazyListState(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        if (playerState.queue.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Queue is empty",
                                        color = Color.White.copy(alpha = 0.3f),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                        
                        itemsIndexed(playerState.queue, key = { _, song -> song.id }) { index, song ->
                            var dragOffset by remember { mutableFloatStateOf(0f) }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset { IntOffset(0, dragOffset.roundToInt()) }
                                    .clip(RoundedCornerShape(12.dp))
                                    .combinedClickable(
                                        onClick = { 
                                            musicViewModel.skipToQueueItem(index)
                                        },
                                        onLongClick = {
                                            selectedSongForMenu = index to song
                                            showSongMenu = true
                                        }
                                    )
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = song.thumbnailUrl.ifEmpty { null },
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.5f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Reorder",
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .size(32.dp)
                                        .padding(4.dp)
                                        .pointerInput(index) {
                                            detectDragGestures(
                                                onDragStart = { },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffset += dragAmount.y
                                                    
                                                    val threshold = with(density) { 60.dp.toPx() }
                                                    if (dragOffset > threshold && index < playerState.queue.size - 1) {
                                                        musicViewModel.moveQueueItem(index, index + 1)
                                                        dragOffset -= threshold
                                                    } else if (dragOffset < -threshold && index > 0) {
                                                        musicViewModel.moveQueueItem(index, index - 1)
                                                        dragOffset += threshold
                                                    }
                                                },
                                                onDragEnd = { dragOffset = 0f },
                                                onDragCancel = { dragOffset = 0f }
                                            )
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showSongMenu && selectedSongForMenu != null) {
            val (index, song) = selectedSongForMenu!!
            ModalBottomSheet(
                onDismissRequest = { showSongMenu = false },
                containerColor = Color(0xFF1A1A1A),
                scrimColor = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(bottom = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = song.thumbnailUrl.ifEmpty { null },
                            contentDescription = null,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                        }
                    }

                    ModernMenuItem(
                        text = "Play Next",
                        icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                        onClick = {
                            musicViewModel.moveQueueItem(index, 0)
                            showSongMenu = false
                        }
                    )
                    ModernMenuItem(
                        text = "Add to Favorites",
                        icon = Icons.Rounded.Favorite,
                        onClick = {
                            musicViewModel.toggleFavorite(song)
                            showSongMenu = false
                        }
                    )
                    ModernMenuItem(
                        text = "Add to Playlist",
                        icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                        onClick = {
                            showAddToPlaylistFromMenu = true
                        }
                    )
                    ModernMenuItem(
                        text = "Remove from Queue",
                        icon = Icons.Default.Delete,
                        color = MaterialTheme.colorScheme.error,
                        onClick = {
                            musicViewModel.removeQueueItem(index)
                            showSongMenu = false
                        }
                    )
                }
            }
        }

        if (showPlaylistDialog) {
            var showCreateDialog by remember { mutableStateOf(false) }
            var newPlaylistName by remember { mutableStateOf("") }
            val songToAdd = if (showAddToPlaylistFromMenu) selectedSongForMenu?.second else playerState.currentSong

            AlertDialog(
                onDismissRequest = { 
                    showPlaylistDialog = false
                    showAddToPlaylistFromMenu = false
                },
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
                        itemsIndexed(playlists) { _, playlist ->
                            ListItem(
                                headlineContent = { Text(playlist.name) },
                                leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) },
                                modifier = Modifier.clickable {
                                    songToAdd?.let { song ->
                                        musicViewModel.addSongToPlaylist(song, playlist.id)
                                    }
                                    showPlaylistDialog = false
                                    showAddToPlaylistFromMenu = false
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { 
                        showPlaylistDialog = false
                        showAddToPlaylistFromMenu = false
                    }) {
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
                                    musicViewModel.createPlaylist(
                                        newPlaylistName, 
                                        songs = songToAdd?.let { setOf(it) } ?: emptySet()
                                    )
                                    showCreateDialog = false
                                    showPlaylistDialog = false
                                    showAddToPlaylistFromMenu = false
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

        if (showAddToPlaylistFromMenu) {
            showPlaylistDialog = true
            showSongMenu = false
        }
    }
}

@Composable
fun ModernMenuItem(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color = Color.White,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (color == Color.White) color.copy(alpha = 0.7f) else color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(20.dp))
            Text(
                text = text,
                color = color,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
