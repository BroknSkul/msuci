package com.musicplayer.app.data.remote.model

import com.google.gson.annotations.SerializedName

data class LastFmTrackResponse(
    val track: LastFmTrackInfo? = null
)

data class LastFmTrackInfo(
    val name: String,
    val artist: LastFmArtist,
    val album: LastFmAlbum? = null,
    val duration: String? = null,
    val toptags: LastFmTopTags? = null
)

data class LastFmTopTagsResponse(
    val toptags: LastFmTopTags? = null
)

data class LastFmTopTags(
    val tag: List<LastFmTag>? = null
)

data class LastFmTag(
    val name: String,
    val url: String
)

data class LastFmArtist(
    val name: String,
    val image: List<LastFmImage>? = null
)

data class LastFmArtistInfoResponse(
    val artist: LastFmArtistInfo? = null
)

data class LastFmArtistInfo(
    val name: String,
    val image: List<LastFmImage>? = null,
    val bio: LastFmBio? = null,
    val stats: LastFmStats? = null
)

data class LastFmBio(
    val summary: String? = null,
    val content: String? = null
)

data class LastFmStats(
    val listeners: String? = null,
    val playcount: String? = null
)

data class LastFmAlbum(
    val title: String,
    val image: List<LastFmImage>? = null
)

data class LastFmImage(
    @SerializedName("#text")
    val url: String,
    val size: String
)

data class LastFmSearchResponse(
    val results: LastFmSearchResults
)

data class LastFmSearchResults(
    @SerializedName("trackmatches")
    val trackmatches: LastFmTrackMatches
)

data class LastFmTrackMatches(
    val track: List<LastFmSearchTrack>
)

data class LastFmSearchTrack(
    val name: String,
    val artist: String,
    val url: String,
    val image: List<LastFmImage>? = null
)

data class LastFmSimilarTracksResponse(
    val similartracks: LastFmSimilarTracks
)

data class LastFmSimilarTracks(
    val track: List<LastFmSimilarTrack>
)

data class LastFmSimilarTrack(
    val name: String,
    val artist: LastFmArtist,
    val url: String,
    val image: List<LastFmImage>? = null
)

data class LastFmSimilarArtistsResponse(
    val similarartists: LastFmSimilarArtists
)

data class LastFmSimilarArtists(
    val artist: List<LastFmArtistInfo>
)

data class LastFmTopTracksResponse(
    val tracks: LastFmTopTracks
)

data class LastFmTopTracks(
    val track: List<LastFmTrackInfo>
)
