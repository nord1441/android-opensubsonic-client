package com.opensubsonic.client.data.repository

import com.opensubsonic.client.api.SubsonicClient
import com.opensubsonic.client.data.db.MusicDao
import com.opensubsonic.client.data.model.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val client: SubsonicClient,
    private val musicDao: MusicDao
) {
    // Albums
    fun getAlbumsFlow(): Flow<List<Album>> = musicDao.getAllAlbums()

    suspend fun refreshAlbums(offset: Int = 0, size: Int = 50): List<Album> {
        val albums = client.getAlbumList(size = size, offset = offset)
        musicDao.insertAlbums(albums)
        return albums
    }

    suspend fun getRecentAlbums(size: Int = 20): List<Album> {
        return client.getAlbumList(type = "newest", size = size)
    }

    suspend fun getAlbumDetail(id: String): Pair<Album?, List<Song>> {
        val (album, songs) = client.getAlbum(id)
        album?.let { musicDao.insertAlbums(listOf(it)) }
        musicDao.insertSongs(songs)
        return album to songs
    }

    fun getSongsByAlbum(albumId: String): Flow<List<Song>> = musicDao.getSongsByAlbum(albumId)

    // Artists
    fun getArtistsFlow(): Flow<List<Artist>> = musicDao.getAllArtists()

    suspend fun refreshArtists(): List<Artist> {
        val artists = client.getArtists()
        musicDao.insertArtists(artists)
        return artists
    }

    suspend fun getArtistDetail(id: String): Pair<Artist?, List<Album>> {
        val (artist, albums) = client.getArtist(id)
        artist?.let { musicDao.insertArtists(listOf(it)) }
        musicDao.insertAlbums(albums)
        return artist to albums
    }

    // Genres
    fun getGenresFlow(): Flow<List<Genre>> = musicDao.getAllGenres()

    suspend fun refreshGenres(): List<Genre> {
        val genres = client.getGenres()
        musicDao.insertGenres(genres)
        return genres
    }

    suspend fun getAlbumsByGenre(genre: String, size: Int = 50, offset: Int = 0): List<Album> {
        return client.getAlbumsByGenre(genre, size, offset)
    }

    // Playlists
    fun getPlaylistsFlow(): Flow<List<Playlist>> = musicDao.getAllPlaylists()

    suspend fun refreshPlaylists(): List<Playlist> {
        val playlists = client.getPlaylists()
        musicDao.insertPlaylists(playlists)
        return playlists
    }

    suspend fun getPlaylistDetail(id: String): Pair<Playlist?, List<Song>> {
        val (playlist, songs) = client.getPlaylist(id)
        musicDao.insertSongs(songs)
        val playlistSongs = songs.mapIndexed { index, song ->
            PlaylistSong(playlistId = id, songId = song.id, sortOrder = index)
        }
        musicDao.deletePlaylistSongs(id)
        musicDao.insertPlaylistSongs(playlistSongs)
        return playlist to songs
    }

    fun getPlaylistSongs(playlistId: String): Flow<List<Song>> = musicDao.getPlaylistSongs(playlistId)

    // Search
    suspend fun search(query: String): Triple<List<Artist>, List<Album>, List<Song>> {
        return client.search(query)
    }

    // Random
    suspend fun getRandomSongs(size: Int = 50, genre: String? = null): List<Song> {
        return client.getRandomSongs(size, genre)
    }

    // Songs
    suspend fun insertSongs(songs: List<Song>) = musicDao.insertSongs(songs)

    // Downloads
    fun getDownloadedSongs(): Flow<List<Song>> = musicDao.getDownloadedSongs()

    suspend fun markSongAsDownloaded(songId: String, localPath: String) {
        musicDao.markAsDownloaded(songId, localPath)
    }

    suspend fun getSong(id: String): Song? = musicDao.getSong(id)

    suspend fun getCachedAlbum(id: String): Album? = musicDao.getAlbum(id)

    suspend fun getCachedArtist(id: String): Artist? = musicDao.getArtist(id)

    suspend fun getCachedAlbumsByArtist(artistId: String): List<Album> = musicDao.getAlbumsByArtist(artistId)

    suspend fun getCachedAlbumsByGenre(genre: String): List<Album> = musicDao.getAlbumsByGenre(genre)
}
