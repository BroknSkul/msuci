package com.musicplayer.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicplayer.app.ui.components.ModernAsyncImage
import com.musicplayer.app.data.model.Song
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    song: Song,
    onClick: () -> Unit,
    isDownloaded: Boolean = false,
    downloadProgress: Float? = null,
    onCancelDownload: (() -> Unit)? = null,
    showSelection: Boolean = false,
    isSelected: Boolean = false,
    onSelect: (() -> Unit)? = null,
    onDownloadClick: (() -> Unit)? = null,
    onDeleteDownload: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val displayThumbnail = remember(song.id, song.thumbnailUrl) {
        val localThumbFile = java.io.File(context.getExternalFilesDir(null), "music_storage/${song.id}.jpg")
        
        if (localThumbFile.exists()) {
            localThumbFile // Passing File object to Coil is very reliable offline
        } else if (song.thumbnailUrl.startsWith("/") || song.thumbnailUrl.startsWith("file:")) {
            val file = if (song.thumbnailUrl.startsWith("file:")) 
                java.io.File(java.net.URI(song.thumbnailUrl)) 
            else 
                java.io.File(song.thumbnailUrl)
            
            if (file.exists()) file else "https://i.ytimg.com/vi/${song.id}/maxresdefault.jpg"
        } else if (song.thumbnailUrl.isBlank() && song.id.length == 11) {
            "https://i.ytimg.com/vi/${song.id}/maxresdefault.jpg"
        } else {
            song.thumbnailUrl
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFFC9A9A6).copy(alpha = 0.4f) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { 
                    onSelect?.invoke()
                }
            )
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                ModernAsyncImage(
                    model = displayThumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1A1A)),
                    contentScale = ContentScale.Crop,
                    alpha = if (isSelected) 0.6f else 1f
                )
                
                if (showSelection) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color(0xFFC9A9A6) else Color.White.copy(alpha = 0.3f))
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            if (isDownloaded || song.isDownloaded) {
                                Icon(Icons.Rounded.DownloadDone, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (downloadProgress != null) {
                    IconButton(onClick = { onCancelDownload?.invoke() }) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                                trackColor = Color.Gray.copy(alpha = 0.3f)
                            )
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Cancel Download",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                } else if (isDownloaded || song.isDownloaded) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.DownloadDone,
                            contentDescription = "Downloaded",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        if (onDeleteDownload != null && !showSelection) {
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = onDeleteDownload,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete Download",
                                    tint = Color.Gray.copy(alpha = 0.8f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                } else if (onDownloadClick != null) {
                    IconButton(onClick = onDownloadClick) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Download",
                            tint = Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(Modifier.width(8.dp))
                
                if (song.duration > 0) {
                    Text(
                        text = formatTime(song.duration),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray
                    )
                }
            }
        }
        
        if (downloadProgress != null) {
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { downloadProgress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Gray.copy(alpha = 0.1f)
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
