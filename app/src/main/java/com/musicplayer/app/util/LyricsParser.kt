package com.musicplayer.app.util

import com.musicplayer.app.data.model.LyricLine
import java.util.regex.Pattern

object LyricsParser {
    private val TIMESTAMP_PATTERN = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?\\]")
    private val OFFSET_PATTERN = Pattern.compile("\\[offset:([+-]?\\d+)\\]")

    fun parse(lrcContent: String?): List<LyricLine>? {
        if (lrcContent.isNullOrBlank()) return null
        
        val lines = lrcContent.split("\n")
        val parsedLines = mutableListOf<LyricLine>()
        var offset = 0L

        // First pass: Find global offset tag
        for (line in lines) {
            val trimmed = line.trim()
            val offsetMatcher = OFFSET_PATTERN.matcher(trimmed)
            if (offsetMatcher.find()) {
                offset = try { offsetMatcher.group(1)?.toLong() ?: 0L } catch (_: Exception) { 0L }
            }
        }

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isBlank()) continue

            val matcher = TIMESTAMP_PATTERN.matcher(trimmedLine)
            val timestamps = mutableListOf<Long>()
            
            var lastMatchEnd = 0
            while (matcher.find()) {
                val minutes = matcher.group(1)?.toLong() ?: 0L
                val seconds = matcher.group(2)?.toLong() ?: 0L
                val fractionStr = matcher.group(3)
                
                val fractionMs = if (fractionStr != null) {
                    val fraction = fractionStr.toLong()
                    when (fractionStr.length) {
                        3 -> fraction
                        2 -> fraction * 10
                        1 -> fraction * 100
                        else -> fraction
                    }
                } else {
                    0L
                }
                
                // Apply global offset (offset is usually in ms)
                val timeMs = (minutes * 60 * 1000) + (seconds * 1000) + fractionMs + offset
                timestamps.add(timeMs)
                lastMatchEnd = matcher.end()
            }
            
            if (timestamps.isNotEmpty()) {
                val text = trimmedLine.substring(lastMatchEnd).trim()
                // Keep the line even if text is blank (useful for syncing instrumental breaks)
                for (timeMs in timestamps) {
                    parsedLines.add(LyricLine(timeMs, text))
                }
            }
        }

        return if (parsedLines.isNotEmpty()) {
            parsedLines.sortedBy { it.time }
        } else {
            null
        }
    }
}
