package com.opensubsonic.client.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.opensubsonic.client.api.SubsonicClient
import com.opensubsonic.client.data.model.Playlist
import com.opensubsonic.client.data.model.ServerConfig
import com.opensubsonic.client.data.model.Song
import com.opensubsonic.client.data.repository.MusicRepository
import kotlinx.coroutines.flow.first
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadProgress(
    val songId: String,
    val progress: Float,
    val isComplete: Boolean = false,
    val error: String? = null
)

data class BulkDownloadState(
    val isDownloading: Boolean = false,
    val totalTracks: Int = 0,
    val completedTracks: Int = 0,
    val currentTrackName: String? = null,
    val currentAlbumName: String? = null,
    val phase: String = "downloading" // "downloading", "playlists", "syncing"
)

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val musicRepository: MusicRepository,
    private val subsonicClient: SubsonicClient,
    private val storagePreferences: StoragePreferences
) {
    private val _activeDownloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, DownloadProgress>> = _activeDownloads

    private val _bulkDownloadState = MutableStateFlow(BulkDownloadState())
    val bulkDownloadState: StateFlow<BulkDownloadState> = _bulkDownloadState

    companion object {
        private const val SUBTUNE_DIR = "SubTune"

        fun buildStableFileName(song: Song, transcodedFormat: String? = null): String {
            val suffix = if (transcodedFormat != null && transcodedFormat != "raw") {
                transcodedFormat
            } else {
                song.suffix ?: "mp3"
            }
            val artist = (song.artist ?: "Unknown").replace(Regex("[/\\\\:*?\"<>|]"), "_")
            val title = song.title.replace(Regex("[/\\\\:*?\"<>|]"), "_")
            // Prefix with song ID for re-mapping after reinstall
            return "${song.id}__${artist} - ${title}.$suffix"
        }
    }

    suspend fun downloadSong(song: Song, server: ServerConfig) {
        withContext(Dispatchers.IO) {
            try {
                // Skip if file already exists on disk for this song ID
                if (isSongFileExists(song.id)) {
                    _activeDownloads.value = _activeDownloads.value + (song.id to DownloadProgress(song.id, 1f, isComplete = true))
                    return@withContext
                }

                _activeDownloads.value = _activeDownloads.value + (song.id to DownloadProgress(song.id, 0f))

                val dlFormat = storagePreferences.getDownloadFormatSync()
                val dlBitrate = storagePreferences.getDownloadBitrateSync()
                val url = SubsonicUrlHelper.getDownloadUrl(server, song.id, dlFormat.apiValue, dlBitrate.value)
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    _activeDownloads.value = _activeDownloads.value + (song.id to DownloadProgress(song.id, 0f, error = "Download failed"))
                    return@withContext
                }

                val body = response.body ?: return@withContext
                // Use the actual transcoded format for the file extension
                val actualFormat = dlFormat.apiValue
                val fileName = buildStableFileName(song, actualFormat)
                // Determine correct MIME type based on actual format
                val transcodedSong = if (actualFormat != "raw") {
                    val mimeType = when (actualFormat) {
                        "mp3" -> "audio/mpeg"
                        "ogg" -> "audio/ogg"
                        "opus" -> "audio/opus"
                        "aac" -> "audio/aac"
                        "flac" -> "audio/flac"
                        else -> song.contentType ?: "audio/mpeg"
                    }
                    song.copy(contentType = mimeType)
                } else {
                    song
                }
                val storageLocation = storagePreferences.getStorageLocationSync()

                val localPath = if (storageLocation == StorageLocation.SD_CARD) {
                    val sdDir = storagePreferences.getMusicDir(StorageLocation.SD_CARD)
                    if (sdDir != null) saveToDir(fileName, body.byteStream(), sdDir)
                    else saveToFile(fileName, body.byteStream())
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveWithMediaStore(fileName, transcodedSong, body.byteStream())
                } else {
                    saveToFile(fileName, body.byteStream())
                }

                if (localPath != null) {
                    musicRepository.markSongAsDownloaded(song.id, localPath)
                    _activeDownloads.value = _activeDownloads.value + (song.id to DownloadProgress(song.id, 1f, isComplete = true))
                }
            } catch (e: Exception) {
                _activeDownloads.value = _activeDownloads.value + (
                    song.id to DownloadProgress(song.id, 0f, error = e.message)
                )
            }
        }
    }

    suspend fun downloadAllAlbums(server: ServerConfig) {
        withContext(Dispatchers.IO) {
            try {
                var offset = 0
                val allAlbums = mutableListOf<com.opensubsonic.client.data.model.Album>()
                while (true) {
                    val batch = subsonicClient.getAlbumList(size = 500, offset = offset)
                    if (batch.isEmpty()) break
                    allAlbums.addAll(batch)
                    offset += batch.size
                    if (batch.size < 500) break
                }

                val totalTracks = allAlbums.sumOf { it.songCount }

                _bulkDownloadState.value = BulkDownloadState(
                    isDownloading = true,
                    totalTracks = totalTracks,
                    completedTracks = 0
                )

                var completed = 0
                for (album in allAlbums) {
                    try {
                        val (_, songs) = subsonicClient.getAlbum(album.id)
                        musicRepository.insertSongs(songs)

                        for (song in songs) {
                            _bulkDownloadState.value = _bulkDownloadState.value.copy(
                                completedTracks = completed,
                                currentTrackName = song.title,
                                currentAlbumName = album.name
                            )

                            // Skip if file already exists on disk for this song ID
                            if (isSongFileExists(song.id)) {
                                completed++
                                continue
                            }

                            downloadSong(song, server)
                            // Clean up completed/errored entries to prevent map growth
                            _activeDownloads.value = _activeDownloads.value - song.id
                            completed++
                        }
                    } catch (_: Exception) {
                        // Skip failed albums
                    }
                }

                // Phase 2: Cache all playlists with M3U files
                _bulkDownloadState.value = _bulkDownloadState.value.copy(
                    phase = "playlists",
                    completedTracks = completed,
                    currentTrackName = null,
                    currentAlbumName = null
                )
                cacheAllPlaylists(server)

                _bulkDownloadState.value = BulkDownloadState(
                    isDownloading = false,
                    totalTracks = completed,
                    completedTracks = completed
                )
            } catch (e: Exception) {
                _bulkDownloadState.value = _bulkDownloadState.value.copy(isDownloading = false)
            }
        }
    }

    private fun isSongFileExists(songId: String): Boolean {
        // Check MediaStore (internal storage, API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isSongFileExistsMediaStore(songId)) {
            return true
        }
        // Check internal filesystem
        if (isSongFileExistsInDir(songId, getInternalMusicDir())) {
            return true
        }
        // Check SD card
        val sdDir = storagePreferences.getMusicDir(StorageLocation.SD_CARD)
        if (sdDir != null && isSongFileExistsInDir(songId, sdDir)) {
            return true
        }
        return false
    }

    private fun isSongFileExistsMediaStore(songId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$SUBTUNE_DIR%", "${songId}__%")
        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs, null
        )?.use { cursor ->
            return cursor.moveToFirst()
        }
        return false
    }

    private fun isSongFileExistsInDir(songId: String, dir: File): Boolean {
        if (!dir.exists()) return false
        return dir.listFiles()?.any { it.name.startsWith("${songId}__") } == true
    }

    @Suppress("DEPRECATION")
    private fun getInternalMusicDir(): File {
        return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), SUBTUNE_DIR)
    }

    private fun isFileAccessible(path: String): Boolean {
        return try {
            if (path.startsWith("content://")) {
                context.contentResolver.openInputStream(android.net.Uri.parse(path))?.close()
                true
            } else {
                File(path).exists()
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun scanAndRemapDownloads() {
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                scanMediaStore()
            } else {
                scanDirectory(getInternalMusicDir())
            }
            // Also scan SD card directory
            val sdDir = storagePreferences.getMusicDir(StorageLocation.SD_CARD)
            if (sdDir != null) {
                scanDirectory(sdDir)
            }
        }
    }

    private suspend fun scanMediaStore() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH
        )
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%$SUBTUNE_DIR%")

        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idCol)
                val displayName = cursor.getString(nameCol) ?: continue

                // Extract song ID from filename: {songId}__{artist} - {title}.{ext}
                val songId = displayName.substringBefore("__", "")
                if (songId.isNotEmpty()) {
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId
                    )
                    musicRepository.markSongAsDownloaded(songId, uri.toString())
                }
            }
        }
    }

    private suspend fun scanDirectory(musicDir: File) {
        if (!musicDir.exists()) return

        musicDir.listFiles()?.forEach { file ->
            val songId = file.name.substringBefore("__", "")
            if (songId.isNotEmpty()) {
                musicRepository.markSongAsDownloaded(songId, file.absolutePath)
            }
        }
    }

    private fun saveWithMediaStore(fileName: String, song: Song, inputStream: java.io.InputStream): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, song.contentType ?: "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$SUBTUNE_DIR")
            put(MediaStore.Audio.Media.TITLE, song.title)
            put(MediaStore.Audio.Media.ARTIST, song.artist ?: "Unknown")
            put(MediaStore.Audio.Media.ALBUM, song.album ?: "Unknown")
            if (song.track != null) put(MediaStore.Audio.Media.TRACK, song.track)
            if (song.duration > 0) put(MediaStore.Audio.Media.DURATION, song.duration * 1000L)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null

        inputStream.use { input ->
            resolver.openOutputStream(uri)?.use { output ->
                input.copyTo(output)
            }
        }

        contentValues.clear()
        contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)

        return uri.toString()
    }

    @Suppress("DEPRECATION")
    private fun saveToFile(fileName: String, inputStream: java.io.InputStream): String? {
        return saveToDir(fileName, inputStream, getInternalMusicDir())
    }

    private fun saveToDir(fileName: String, inputStream: java.io.InputStream, dir: File): String? {
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        inputStream.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return file.absolutePath
    }

    /**
     * Cache all playlists: download songs and generate M3U files.
     */
    suspend fun cacheAllPlaylists(server: ServerConfig) {
        withContext(Dispatchers.IO) {
            try {
                val playlists = subsonicClient.getPlaylists()
                musicRepository.insertPlaylists(playlists)

                for (playlist in playlists) {
                    try {
                        val (_, songs) = subsonicClient.getPlaylist(playlist.id)
                        musicRepository.insertSongs(songs)
                        musicRepository.updatePlaylistSongs(playlist.id, songs)

                        _bulkDownloadState.value = _bulkDownloadState.value.copy(
                            currentAlbumName = playlist.name
                        )

                        for (song in songs) {
                            _bulkDownloadState.value = _bulkDownloadState.value.copy(
                                currentTrackName = song.title
                            )
                            if (!isSongFileExists(song.id)) {
                                downloadSong(song, server)
                                _activeDownloads.value = _activeDownloads.value - song.id
                            }
                        }

                        // Generate M3U file for this playlist
                        generateM3uPlaylist(playlist, songs)
                    } catch (_: Exception) {
                        // Skip failed playlists
                    }
                }
            } catch (_: Exception) {
                // Playlist fetch failed (offline etc.)
            }
        }
    }

    /**
     * Generate an M3U playlist file referencing local paths of downloaded songs.
     */
    suspend fun generateM3uPlaylist(playlist: Playlist, songs: List<Song>) {
        val playlistDir = getPlaylistDir() ?: return
        if (!playlistDir.exists()) playlistDir.mkdirs()

        val safeName = playlist.name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val m3uFile = File(playlistDir, "${safeName}.m3u")

        m3uFile.bufferedWriter().use { writer ->
            writer.write("#EXTM3U")
            writer.newLine()

            for (song in songs) {
                val localPath = resolveLocalPath(song)
                if (localPath != null) {
                    writer.write("#EXTINF:${song.duration},${song.artist ?: "Unknown"} - ${song.title}")
                    writer.newLine()
                    writer.write(localPath)
                    writer.newLine()
                }
            }
        }
    }

    /**
     * Resolve the local filesystem path for a downloaded song.
     * For content:// URIs, find the matching file on disk instead.
     */
    private fun resolveLocalPath(song: Song): String? {
        // Check filesystem directories first
        val internalDir = getInternalMusicDir()
        val internalFile = findSongFileInDir(song.id, internalDir)
        if (internalFile != null) return internalFile.absolutePath

        val sdDir = storagePreferences.getMusicDir(StorageLocation.SD_CARD)
        if (sdDir != null) {
            val sdFile = findSongFileInDir(song.id, sdDir)
            if (sdFile != null) return sdFile.absolutePath
        }

        return null
    }

    private fun findSongFileInDir(songId: String, dir: File): File? {
        if (!dir.exists()) return null
        return dir.listFiles()?.firstOrNull { it.name.startsWith("${songId}__") }
    }

    private suspend fun getPlaylistDir(): File? {
        val storageLocation = try {
            storagePreferences.getStorageLocationSync()
        } catch (_: Exception) {
            StorageLocation.INTERNAL
        }
        val musicDir = if (storageLocation == StorageLocation.SD_CARD) {
            storagePreferences.getMusicDir(StorageLocation.SD_CARD) ?: getInternalMusicDir()
        } else {
            getInternalMusicDir()
        }
        return File(musicDir, "Playlists")
    }

    /**
     * Sync local data with server: update albums, songs, playlists, and regenerate M3U files.
     * Only runs when server is reachable.
     */
    suspend fun syncWithServer(server: ServerConfig) {
        withContext(Dispatchers.IO) {
            try {
                // Check connectivity
                if (!subsonicClient.ping()) return@withContext

                // Sync albums and songs
                var offset = 0
                while (true) {
                    val batch = subsonicClient.getAlbumList(size = 500, offset = offset)
                    if (batch.isEmpty()) break
                    musicRepository.insertAlbums(batch)

                    for (album in batch) {
                        try {
                            val (_, songs) = subsonicClient.getAlbum(album.id)
                            musicRepository.insertSongs(songs)
                        } catch (_: Exception) {}
                    }

                    offset += batch.size
                    if (batch.size < 500) break
                }

                // Sync playlists and regenerate M3U files
                val playlists = subsonicClient.getPlaylists()
                musicRepository.insertPlaylists(playlists)

                // Clean up M3U files for playlists that no longer exist on server
                cleanupOrphanedM3u(playlists)

                for (playlist in playlists) {
                    try {
                        val (_, songs) = subsonicClient.getPlaylist(playlist.id)
                        musicRepository.insertSongs(songs)
                        musicRepository.updatePlaylistSongs(playlist.id, songs)

                        // Regenerate M3U with current song list
                        generateM3uPlaylist(playlist, songs)
                    } catch (_: Exception) {}
                }

                // Re-scan downloads to update local path mappings
                scanAndRemapDownloads()
            } catch (_: Exception) {
                // Server unreachable, skip sync
            }
        }
    }

    private suspend fun cleanupOrphanedM3u(serverPlaylists: List<Playlist>) {
        val playlistDir = getPlaylistDir() ?: return
        if (!playlistDir.exists()) return

        val validNames = serverPlaylists.map { playlist ->
            playlist.name.replace(Regex("[/\\\\:*?\"<>|]"), "_") + ".m3u"
        }.toSet()

        playlistDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".m3u") && file.name !in validNames) {
                file.delete()
            }
        }
    }

    fun clearCompleted() {
        _activeDownloads.value = _activeDownloads.value.filter { !it.value.isComplete && it.value.error == null }
    }
}
