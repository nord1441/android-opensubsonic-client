package com.opensubsonic.client.data.db

import androidx.room.*
import com.opensubsonic.client.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    // Albums
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbums(albums: List<Album>)

    @Query("SELECT * FROM albums ORDER BY name ASC")
    fun getAllAlbums(): Flow<List<Album>>

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getAlbum(id: String): Album?

    @Query("DELETE FROM albums")
    suspend fun deleteAllAlbums()

    // Artists
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtists(artists: List<Artist>)

    @Query("SELECT * FROM artists ORDER BY name ASC")
    fun getAllArtists(): Flow<List<Artist>>

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun getArtist(id: String): Artist?

    @Query("DELETE FROM artists")
    suspend fun deleteAllArtists()

    // Songs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)

    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY discNumber, track")
    fun getSongsByAlbum(albumId: String): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY discNumber, track")
    suspend fun getSongsByAlbumDirect(albumId: String): List<Song>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSong(id: String): Song?

    @Query("SELECT * FROM songs WHERE isDownloaded = 1 ORDER BY title ASC")
    fun getDownloadedSongs(): Flow<List<Song>>

    @Query("UPDATE songs SET isDownloaded = 1, localPath = :localPath WHERE id = :songId")
    suspend fun markAsDownloaded(songId: String, localPath: String)

    @Query("DELETE FROM songs")
    suspend fun deleteAllSongs()

    // Genres
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenres(genres: List<Genre>)

    @Query("SELECT * FROM genres ORDER BY name ASC")
    fun getAllGenres(): Flow<List<Genre>>

    @Query("DELETE FROM genres")
    suspend fun deleteAllGenres()

    // Playlists
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<Playlist>)

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongs(songs: List<PlaylistSong>)

    @Query("SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id = ps.songId WHERE ps.playlistId = :playlistId ORDER BY ps.sortOrder")
    fun getPlaylistSongs(playlistId: String): Flow<List<Song>>

    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun deletePlaylistSongs(playlistId: String)
}
