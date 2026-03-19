package com.opensubsonic.client.api

import com.opensubsonic.client.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubsonicClient @Inject constructor(
    private val api: SubsonicApi
) {
    suspend fun ping(): Boolean {
        val response = api.ping()
        return response.subsonicResponse?.status == "ok"
    }

    suspend fun getAlbumList(
        type: String = "alphabeticalByName",
        size: Int = 50,
        offset: Int = 0
    ): List<Album> {
        val response = api.getAlbumList2(type, size, offset)
        return response.subsonicResponse?.albumList2?.album?.mapNotNull { it.toAlbum() } ?: emptyList()
    }

    suspend fun getAlbum(id: String): Pair<Album?, List<Song>> {
        val response = api.getAlbum(id)
        val albumResp = response.subsonicResponse?.album
        val album = albumResp?.let {
            Album(
                id = it.id ?: return@let null,
                name = it.name ?: "",
                artist = it.artist,
                artistId = it.artistId,
                coverArt = it.coverArt,
                songCount = it.songCount ?: 0,
                duration = it.duration ?: 0,
                year = it.year,
                genre = it.genre
            )
        }
        val songs = albumResp?.song?.mapNotNull { it.toSong() } ?: emptyList()
        return album to songs
    }

    suspend fun getArtists(): List<Artist> {
        val response = api.getArtists()
        return response.subsonicResponse?.artists?.index?.flatMap { index ->
            index.artist?.mapNotNull { it.toArtist() } ?: emptyList()
        } ?: emptyList()
    }

    suspend fun getArtist(id: String): Pair<Artist?, List<Album>> {
        val response = api.getArtist(id)
        val detail = response.subsonicResponse?.artist
        val artist = detail?.let {
            Artist(
                id = it.id ?: return@let null,
                name = it.name ?: "",
                coverArt = it.coverArt,
                albumCount = it.albumCount ?: 0
            )
        }
        val albums = detail?.album?.mapNotNull { it.toAlbum() } ?: emptyList()
        return artist to albums
    }

    suspend fun getGenres(): List<Genre> {
        val response = api.getGenres()
        return response.subsonicResponse?.genres?.genre?.mapNotNull {
            Genre(
                name = it.value ?: return@mapNotNull null,
                songCount = it.songCount ?: 0,
                albumCount = it.albumCount ?: 0
            )
        } ?: emptyList()
    }

    suspend fun getAlbumsByGenre(genre: String, size: Int = 50, offset: Int = 0): List<Album> {
        val response = api.getAlbumsByGenre(genre = genre, size = size, offset = offset)
        return response.subsonicResponse?.albumList2?.album?.mapNotNull { it.toAlbum() } ?: emptyList()
    }

    suspend fun getPlaylists(): List<Playlist> {
        val response = api.getPlaylists()
        return response.subsonicResponse?.playlists?.playlist?.mapNotNull {
            Playlist(
                id = it.id ?: return@mapNotNull null,
                name = it.name ?: "",
                comment = it.comment,
                songCount = it.songCount ?: 0,
                duration = it.duration ?: 0,
                coverArt = it.coverArt,
                owner = it.owner,
                isPublic = it.public ?: false
            )
        } ?: emptyList()
    }

    suspend fun getPlaylist(id: String): Pair<Playlist?, List<Song>> {
        val response = api.getPlaylist(id)
        val detail = response.subsonicResponse?.playlist
        val playlist = detail?.let {
            Playlist(
                id = it.id ?: return@let null,
                name = it.name ?: "",
                comment = it.comment,
                songCount = it.songCount ?: 0,
                duration = it.duration ?: 0,
                coverArt = it.coverArt,
                owner = it.owner,
                isPublic = it.public ?: false
            )
        }
        val songs = detail?.entry?.mapNotNull { it.toSong() } ?: emptyList()
        return playlist to songs
    }

    suspend fun search(query: String): Triple<List<Artist>, List<Album>, List<Song>> {
        val response = api.search3(query)
        val result = response.subsonicResponse?.searchResult3
        return Triple(
            result?.artist?.mapNotNull { it.toArtist() } ?: emptyList(),
            result?.album?.mapNotNull { it.toAlbum() } ?: emptyList(),
            result?.song?.mapNotNull { it.toSong() } ?: emptyList()
        )
    }

    suspend fun getRandomSongs(size: Int = 50, genre: String? = null): List<Song> {
        val response = api.getRandomSongs(size, genre)
        return response.subsonicResponse?.randomSongs?.song?.mapNotNull { it.toSong() } ?: emptyList()
    }

    private fun AlbumDto.toAlbum(): Album? {
        return Album(
            id = id ?: return null,
            name = name ?: "",
            artist = artist,
            artistId = artistId,
            coverArt = coverArt,
            songCount = songCount ?: 0,
            duration = duration ?: 0,
            year = year,
            genre = genre
        )
    }

    private fun ArtistDto.toArtist(): Artist? {
        return Artist(
            id = id ?: return null,
            name = name ?: "",
            coverArt = coverArt,
            albumCount = albumCount ?: 0
        )
    }

    private fun SongDto.toSong(): Song? {
        return Song(
            id = id ?: return null,
            title = title ?: "",
            album = album,
            albumId = albumId,
            artist = artist,
            artistId = artistId,
            track = track,
            discNumber = discNumber,
            year = year,
            genre = genre,
            coverArt = coverArt,
            duration = duration ?: 0,
            bitRate = bitRate,
            suffix = suffix,
            contentType = contentType,
            path = path
        )
    }
}
