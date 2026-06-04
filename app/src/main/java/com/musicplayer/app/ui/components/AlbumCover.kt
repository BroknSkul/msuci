package com.musicplayer.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * A robust AsyncImage wrapper that handles YouTube thumbnail high-res fallback
 * and crops out black bars from letterboxed 4:3 thumbnails to ensure a perfect 1:1 fit.
 */
@Composable
fun ModernAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = 1f
) {
    val modelStr = model?.toString() ?: ""
    
    // Auto-upgrade YouTube thumbnails to maxres if possible
    val improvedModel = remember(model) {
        if (modelStr.contains("ytimg.com") && !modelStr.contains("maxresdefault")) {
            val videoId = Regex("vi/([^/]+)/").find(modelStr)?.groupValues?.get(1)
            if (videoId != null) "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg" else model
        } else {
            model
        }
    }

    var currentModel by remember(improvedModel) { mutableStateOf(improvedModel) }
    val currentModelStr = currentModel?.toString() ?: ""
    val isLetterboxed = currentModelStr.contains("hqdefault.jpg") || currentModelStr.contains("sddefault.jpg")

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(currentModel)
            .listener(
                onError = { _, _ ->
                    // If maxres fails, fallback to hqdefault (which is letterboxed, but reliable)
                    if (currentModelStr.contains("maxresdefault")) {
                        val videoId = Regex("vi/([^/]+)/").find(currentModelStr)?.groupValues?.get(1)
                        if (videoId != null) {
                            currentModel = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                        }
                    }
                }
            )
            .build(),
        contentDescription = contentDescription,
        modifier = modifier
            .graphicsLayer {
                if (isLetterboxed && contentScale == ContentScale.Crop) {
                    // Zoom in by ~35% to crop out top/bottom black bars in 4:3 frames
                    scaleX = 1.35f
                    scaleY = 1.35f
                }
            },
        contentScale = contentScale,
        alpha = alpha
    )
}

/**
 * Standard Album Cover component that crops the image to a 1:1 aspect ratio.
 */
@Composable
fun AlbumCover(
    thumbnailUrl: Any?,
    modifier: Modifier = Modifier,
    fallbackUrl: Any? = null,
    alpha: Float = 1f
) {
    ModernAsyncImage(
        model = if (thumbnailUrl != null) thumbnailUrl else fallbackUrl,
        contentDescription = "Album Cover",
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp)),
        alpha = alpha
    )
}

/**
 * A modernized, smooth music wave animation with 7 cylindrical bars.
 * Movement is fluid and natural, following specific synchronization phases.
 */
@Composable
fun MusicWaveLoading(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "MusicWave")
    
    // Smoother duration and natural easing for a "liquid" feel
    val duration = 750
    val smoothEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1.0f) // Ease-in-out Sine

    // Bar 1 & 7: Small to Long (Expanding)
    val anim17 by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = smoothEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Wave17"
    )

    // Bar 2 & 6: Medium Small to Long (Expanding)
    val anim26 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = smoothEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Wave26"
    )

    // Bar 3 & 5: Medium Long to Small (Shrinking)
    val anim35 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = smoothEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Wave35"
    )

    // Bar 4 (Center): Long to Small (Shrinking)
    val anim4 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = smoothEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Wave4"
    )

    val heights = listOf(anim17, anim26, anim35, anim4, anim35, anim26, anim17)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val barCount = 7
        val spacing = 5.dp.toPx()
        val totalSpacing = spacing * (barCount - 1)
        
        // Balanced width for a modernized cylindrical look
        val barWidth = (width - totalSpacing) / barCount
        val color = Color.White.copy(alpha = 0.9f)

        heights.forEachIndexed { index, value ->
            val barHeight = height * value
            val x = index * (barWidth + spacing)
            val y = (height - barHeight) / 2 // Perfectly centered vertically

            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2) // Fully cylindrical
            )
        }
    }
}
