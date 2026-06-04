package com.musicplayer.app.data.remote.api

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Headers

interface YouTubeMusicApiService {
    @Headers(
        "Referer: https://music.youtube.com/",
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "X-YouTube-Client-Name: 67",
        "X-YouTube-Client-Version: 1.20240522.01.00"
    )
    @POST("youtubei/v1/search")
    suspend fun searchSongs(
        @Body body: YouTubeSearchRequestBody,
        @Query("key") apiKey: String? = null
    ): YouTubeSearchResponse

    @Headers(
        "Referer: https://music.youtube.com/",
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "X-YouTube-Client-Name: 67",
        "X-YouTube-Client-Version: 1.20240522.01.00"
    )
    @POST("youtubei/v1/browse")
    suspend fun getBrowse(
        @Body body: YouTubeBrowseRequestBody,
        @Query("key") apiKey: String? = null
    ): YouTubeBrowseResponse

    @Headers(
        "Referer: https://music.youtube.com/",
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "X-YouTube-Client-Name: 67",
        "X-YouTube-Client-Version: 1.20240522.01.00"
    )
    @POST("youtubei/v1/next")
    suspend fun getNext(
        @Body body: YouTubeNextRequestBody,
        @Query("key") apiKey: String? = null
    ): YouTubeNextResponse
}

data class YouTubeBrowseRequestBody(
    val context: YouTubeContext = YouTubeContext(),
    val browseId: String
)

data class YouTubeNextRequestBody(
    val context: YouTubeContext = YouTubeContext(),
    val videoId: String? = null,
    val playlistId: String? = null,
    val index: Int? = null,
    val params: String? = null
)

data class YouTubeBrowseResponse(
    val contents: YouTubeBrowseContents?,
    val header: YouTubeBrowseHeader?
)

data class YouTubeBrowseHeader(
    val musicVisualHeaderRenderer: YouTubeVisualHeaderRenderer?
)

data class YouTubeVisualHeaderRenderer(
    val title: YouTubeText?
)

data class YouTubeBrowseContents(
    val singleColumnBrowseResults: YouTubeSingleColumnBrowseResults?,
    val sectionListRenderer: YouTubeSectionListRenderer?,
    val tabbedSearchResults: YouTubeTabbedSearchResults?
)

data class YouTubeSingleColumnBrowseResults(
    val tabs: List<YouTubeTab>?
)

data class YouTubeSectionListRenderer(
    val contents: List<YouTubeSection>?
)

data class YouTubeNextResponse(
    val contents: YouTubeNextContents?
)

data class YouTubeNextContents(
    val singleColumnMusicWatchNextResults: YouTubeWatchNextResults?
)

data class YouTubeWatchNextResults(
    val results: YouTubeWatchNextResultsContainer?,
    val tabbedRenderer: YouTubeTabbedRenderer?
)

data class YouTubeWatchNextResultsContainer(
    val results: YouTubeWatchNextResultsInnerContainer?
)

data class YouTubeWatchNextResultsInnerContainer(
    val contents: List<YouTubeNextContentItem>?
)

data class YouTubeNextContentItem(
    val musicResponsiveListItemRenderer: YouTubeMusicRenderer?
)

data class YouTubeTabbedRenderer(
    val watchNextTabbedResults: YouTubeWatchNextTabbedResults?
)

data class YouTubeWatchNextTabbedResults(
    val tabs: List<YouTubeNextTab>?
)

data class YouTubeNextTab(
    val tabRenderer: YouTubeNextTabRenderer?
)

data class YouTubeNextTabRenderer(
    val title: YouTubeText?,
    val endpoint: YouTubeNavigationEndpoint?
)

data class YouTubeSearchRequestBody(
    val context: YouTubeContext = YouTubeContext(),
    val query: String,
    val params: String? = null
)

data class YouTubeContext(
    val client: YouTubeClient = YouTubeClient(),
    val user: YouTubeUser = YouTubeUser()
) {
    data class YouTubeClient(
        val clientName: String = "WEB_REMIX",
        val clientVersion: String = "1.20240522.01.00",
        val hl: String = "en",
        val gl: String = "US"
    )
    data class YouTubeUser(
        val lockedSafetyMode: Boolean = false
    )
}

data class YouTubeSearchResponse(
    val contents: YouTubeContents?
)

data class YouTubeContents(
    val tabbedSearchResults: YouTubeTabbedSearchResults?
)

data class YouTubeTabbedSearchResults(
    val tabs: List<YouTubeTab>?
)

data class YouTubeTab(
    val tabRenderer: YouTubeTabRenderer?
)

data class YouTubeTabRenderer(
    val content: YouTubeTabContent?
)

data class YouTubeTabContent(
    val sectionList: YouTubeSectionList?
)

data class YouTubeSectionList(
    val contents: List<YouTubeSection>?
)

data class YouTubeSection(
    val musicShelf: YouTubeMusicShelf?,
    val musicDescriptionShelf: YouTubeMusicDescriptionShelf? = null
)

data class YouTubeMusicDescriptionShelf(
    val description: YouTubeText?
)

data class YouTubeMusicShelf(
    val contents: List<YouTubeMusicShelfItem>?
)

data class YouTubeMusicShelfItem(
    val musicResponsiveListItemRenderer: YouTubeMusicRenderer?
)

data class YouTubeMusicRenderer(
    val flexColumns: List<YouTubeFlexColumn>?,
    val playlistItemData: YouTubePlaylistItemData?,
    val navigationEndpoint: YouTubeNavigationEndpoint?,
    val thumbnail: YouTubeThumbnailContainer?
)

data class YouTubeFlexColumn(
    val musicResponsiveListItemFlexColumnRenderer: YouTubeFlexColumnRenderer?
)

data class YouTubeFlexColumnRenderer(
    val text: YouTubeText?
)

data class YouTubeText(
    val runs: List<YouTubeTextRun>?
)

data class YouTubeTextRun(
    val text: String?,
    val navigationEndpoint: YouTubeNavigationEndpoint?
)

data class YouTubeNavigationEndpoint(
    val browseEndpoint: YouTubeBrowseEndpoint? = null,
    val watchEndpoint: YouTubeWatchEndpoint? = null
)

data class YouTubeWatchEndpoint(
    val videoId: String?
)

data class YouTubeBrowseEndpoint(
    val browseId: String?,
    val browseEndpointContextSupportedConfigs: YouTubeBrowseContext?
)

data class YouTubeBrowseContext(
    val browseEndpointContextMusicConfig: YouTubeMusicConfig?
)

data class YouTubeMusicConfig(
    val pageType: String?
)

data class YouTubePlaylistItemData(
    val videoId: String?
)

data class YouTubeThumbnailContainer(
    val musicThumbnailRenderer: YouTubeThumbnailRenderer?
)

data class YouTubeThumbnailRenderer(
    val thumbnail: YouTubeThumbnails?
)

data class YouTubeThumbnails(
    val thumbnails: List<YouTubeThumbnail>?
)

data class YouTubeThumbnail(
    val url: String,
    val width: Int?,
    val height: Int?
)
