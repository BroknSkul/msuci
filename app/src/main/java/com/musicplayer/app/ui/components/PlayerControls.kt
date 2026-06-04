package com.musicplayer.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player

@Composable
fun PlayerControls(
    isPlaying: Boolean,
    repeatMode: Int,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onRepeatModeClick: () -> Unit,
    onAddPlaylistClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Dusty Rose Color
    val DustyRose = Color(0xFFC9A9A6)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Modernized Add to Playlist Button
        IconButton(
            onClick = onAddPlaylistClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                contentDescription = "Add to Playlist",
                modifier = Modifier.size(24.dp),
                tint = Color.White.copy(alpha = 0.9f)
            )
        }

        Spacer(modifier = Modifier.width(32.dp)) // More space for playlist button

        IconButton(
            onClick = onSkipPreviousClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                modifier = Modifier.size(32.dp),
                tint = Color.White
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))

        // Play/Pause Button - Removed background, icons only
        val scale by animateFloatAsState(if (isPlaying) 1.05f else 1f, label = "PlayPauseScale")
        
        IconButton(
            onClick = onPlayPauseClick,
            modifier = Modifier
                .size(48.dp)
                .scale(scale)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(60.dp),
                tint = Color.White
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))

        IconButton(
            onClick = onSkipNextClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next",
                modifier = Modifier.size(32.dp),
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.width(32.dp)) // More space for loop button

        // Modernized Loop Button
        val isActive = repeatMode != Player.REPEAT_MODE_OFF
        val icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
        val tint by animateColorAsState(
            if (isActive) DustyRose else Color.White.copy(alpha = 0.5f),
            label = "LoopTint"
        )
        
        IconButton(
            onClick = onRepeatModeClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isActive) DustyRose.copy(alpha = 0.1f) else Color.Transparent)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Repeat Mode",
                modifier = Modifier.size(22.dp),
                tint = tint
            )
        }
    }
}
