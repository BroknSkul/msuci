package com.musicplayer.app.data.remote.model

import com.google.gson.annotations.SerializedName

data class YoutubeSearchResponse(
    val items: List<YoutubeSearchResult>
)

data class YoutubeSearchResult(
    val id: YoutubeVideoId,
    val snippet: YoutubeSnippet
)

data class YoutubeVideoId(
    val videoId: String
)

data class YoutubeSnippet(
    val title: String,
    val channelTitle: String,
    val thumbnails: YoutubeThumbnails
)

data class YoutubeThumbnails(
    val high: YoutubeThumbnail,
    val default: YoutubeThumbnail
)

data class YoutubeThumbnail(
    val url: String
)

// For video details (duration)
data class YoutubeVideoListResponse(
    val items: List<YoutubeVideoItem>
)

data class YoutubeVideoItem(
    val id: String,
    val snippet: YoutubeSnippet,
    val contentDetails: YoutubeContentDetails
)

data class YoutubeContentDetails(
    val duration: String // ISO 8601 format like PT3M45S
)
