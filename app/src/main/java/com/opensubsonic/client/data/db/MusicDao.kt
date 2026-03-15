package com.opensubsonic.client.data.db

import androidx.room.*
import com.opensubsonic.client.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MusicDao {
    // Albums
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAlbums(albums: List<Album>)

    @Query("SELECT * FROM albums ORDER BY name ASC")
    abstract fun getAllAlbums(): Flow<List<Album>>

    @Query("SELECT * FROM albums WHERE id = :id")
    abstract suspend fun getAlbum(id: String): Album?

    @Query("SELECT * FROM albums WHERE artistId = :artistId ORDER BY year DESC, name ASC")
    abstract suspend fun getAlbumsByArtist(artistId: String): List<Album>

    @Query("SELECT * FROM albums WHERE genre = :genre ORDER BY name ASC")
    abstract suspend fun getAlbumsByGenre(genre: String): List<Album>

    @Query("DELETE FROM albums")
    abstract suspend fun deleteAllAlbums()

    // Artists
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertArtists(artists: List<Artist>)

    @Query("SELECT * FROM artists ORDER BY name ASC")
    abstract fun getAllArtists(): Flow<List<Artist>>

    @Query("SELECT * FROM artists WHERE id = :id")
    abstract suspend fun getArtist(id: String): Artist?

    @Query("DELETE FROM artists")
    abstract suspend fun deleteAllArtists()

    // Songs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun replaceSongs(songs: List<Song>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSongsIgnore(songs: List<Song>)

    @Query("SELECT id, isDownloaded, localPath FROM songs WHERE isDownloaded = 1 AND id IN (:ids)")
    abstract suspend fun getDownloadInfoForIds(ids: List<String>): List<DownloadInfo>

    /**
     * Insert songs preserving download status (isDownloaded, localPath).
     * API responses always have isDownloaded=false, so a naive REPLACE would
     * wipe out the download info. This method merges download status from
     * existing records.
     */
    @Transaction
    open suspend fun insertSongs(songs: List<Song>) {
        if (songs.isEmpty()) return
        // Get existing download info in chunks (SQLite IN clause limit)
        val downloadedMap = mutableMapOf<String, DownloadInfo>()
        songs.map { it.id }.chunked(500).forEach { chunk ->
            getDownloadInfoForIds(chunk).forEach { info ->
                downloadedMap[info.id] = info
            }
        }
        // Merge download status into new song data
        val merged = songs.map { song ->
            val existing = downloadedMap[song.id]
            if (existing != null) {
                song.copy(isDownloaded = true, localPath = existing.localPath)
            } else {
                song
            }
        }
        replaceSongs(merged)
    }

    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY discNumber, track")
    abstract fun getSongsByAlbum(albumId: String): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id = :id")
    abstract suspend fun getSong(id: String): Song?

    @Query("SELECT * FROM songs WHERE isDownloaded = 1 ORDER BY title ASC")
    abstract fun getDownloadedSongs(): Flow<List<Song>>

    @Query("UPDATE songs SET isDownloaded = 1, localPath = :localPath WHERE id = :songId")
    abstract suspend fun markAsDownloaded(songId: String, localPath: String)

    @Query("UPDATE songs SET isDownloaded = 0, localPath = NULL WHERE id = :songId")
    abstract suspend fun markAsNotDownloaded(songId: String)

    @Query("UPDATE songs SET isDownloaded = 0, localPath = NULL WHERE isDownloaded = 1")
    abstract suspend fun clearAllDownloadStatus()

    /**
     * Batch mark multiple songs as downloaded in a single transaction.
     */
    @Transaction
    open suspend fun markAsDownloadedBatch(entries: Map<String, String>) {
        for ((songId, localPath) in entries) {
            markAsDownloaded(songId, localPath)
        }
    }

    @Query("SELECT * FROM songs")
    abstract suspend fun getAllSongsOnce(): List<Song>

    @Query("DELETE FROM songs")
    abstract suspend fun deleteAllSongs()

    // Genres
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertGenres(genres: List<Genre>)

    @Query("SELECT * FROM genres ORDER BY name ASC")
    abstract fun getAllGenres(): Flow<List<Genre>>

    @Query("DELETE FROM genres")
    abstract suspend fun deleteAllGenres()

    // Playlists
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPlaylists(playlists: List<Playlist>)

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    abstract fun getAllPlaylists(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPlaylistSongs(songs: List<PlaylistSong>)

    @Query("SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id = ps.songId WHERE ps.playlistId = :playlistId ORDER BY ps.sortOrder")
    abstract fun getPlaylistSongs(playlistId: String): Flow<List<Song>>

    @Query("DELETE FROM playlists")
    abstract suspend fun deleteAllPlaylists()

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    abstract suspend fun deletePlaylistSongs(playlistId: String)
}
