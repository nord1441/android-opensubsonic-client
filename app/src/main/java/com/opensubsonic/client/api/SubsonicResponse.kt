package com.opensubsonic.client.api

import com.google.gson.annotations.SerializedName

data class SubsonicResponse<T>(
    @SerializedName("subsonic-response")
    val subsonicResponse: SubsonicEnvelope<T>?
)

data class SubsonicEnvelope<T>(
    val status: String?,
    val version: String?,
    val type: String?,
    val serverVersion: String?,
    val openSubsonic: Boolean?,
    val error: SubsonicError?,
    // Various response fields
    val albumList2: AlbumList2? = null,
    val album: AlbumResponse? = null,
    val artists: ArtistsResponse? = null,
    val artist: ArtistDetailResponse? = null,
    val genres: GenresResponse? = null,
    val playlists: PlaylistsResponse? = null,
    val playlist: PlaylistDetailResponse? = null,
    val searchResult3: SearchResult3? = null,
    val randomSongs: RandomSongsResponse? = null
)

data class SubsonicError(
    val code: Int?,
    val message: String?
)

data class AlbumList2(
    val album: List<AlbumDto>?
)

data class AlbumResponse(
    val id: String?,
    val name: String?,
    val artist: String?,
    val artistId: String?,
    val coverArt: String?,
    val songCount: Int?,
    val duration: Int?,
    val year: Int?,
    val genre: String?,
    val song: List<SongDto>?
)

data class ArtistsResponse(
    val index: List<ArtistIndex>?
)

data class ArtistIndex(
    val name: String?,
    val artist: List<ArtistDto>?
)

data class ArtistDetailResponse(
    val id: String?,
    val name: String?,
    val coverArt: String?,
    val albumCount: Int?,
    val album: List<AlbumDto>?
)

data class GenresResponse(
    val genre: List<GenreDto>?
)

data class PlaylistsResponse(
    val playlist: List<PlaylistDto>?
)

data class PlaylistDetailResponse(
    val id: String?,
    val name: String?,
    val comment: String?,
    val songCount: Int?,
    val duration: Int?,
    val coverArt: String?,
    val owner: String?,
    val public: Boolean?,
    val entry: List<SongDto>?
)

data class SearchResult3(
    val artist: List<ArtistDto>?,
    val album: List<AlbumDto>?,
    val song: List<SongDto>?
)

data class RandomSongsResponse(
    val song: List<SongDto>?
)

data class AlbumDto(
    val id: String?,
    val name: String?,
    val artist: String?,
    val artistId: String?,
    val coverArt: String?,
    val songCount: Int?,
    val duration: Int?,
    val year: Int?,
    val genre: String?
)

data class ArtistDto(
    val id: String?,
    val name: String?,
    val coverArt: String?,
    val albumCount: Int?
)

data class SongDto(
    val id: String?,
    val title: String?,
    val album: String?,
    val albumId: String?,
    val artist: String?,
    val artistId: String?,
    val track: Int?,
    val discNumber: Int?,
    val year: Int?,
    val genre: String?,
    val coverArt: String?,
    val duration: Int?,
    val bitRate: Int?,
    val suffix: String?,
    val contentType: String?,
    val path: String?
)

data class GenreDto(
    val value: String?,
    val songCount: Int?,
    val albumCount: Int?
)

data class PlaylistDto(
    val id: String?,
    val name: String?,
    val comment: String?,
    val songCount: Int?,
    val duration: Int?,
    val coverArt: String?,
    val owner: String?,
    val public: Boolean?
)
