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

    // In-memory cache of songId -> filePath to avoid repeated listFiles() on SD card
    private val fileCache = mutableMapOf<String, String>()
    // Secondary cache: content key (artist - title.suffix) -> (filePath, originalSongId)
    private val contentKeyCache = mutableMapOf<String, Pair<String, String>>()

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
                // Skip if file already exists on disk (by ID or content match)
                if (isSongFileExistsByContent(song)) {
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
                    fileCache[song.id] = localPath
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
                // Build file cache once before bulk download to avoid repeated listFiles()
                buildFileCache()
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

                            // Skip if file already exists on disk (by ID or content match)
                            if (isSongFileExistsByContent(song)) {
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

    /**
     * Check if a song file exists, falling back to content-based matching
     * when server IDs have changed (e.g. after server restart).
     * If found by content key under a different ID, remaps the cache entry.
     */
    private fun isSongFileExistsByContent(song: Song): Boolean {
        if (fileCache.containsKey(song.id)) return true
        // Try content-based match: "Artist - Title.suffix"
        val contentKey = buildContentKey(song)
        val match = contentKeyCache[contentKey]
        if (match != null) {
            // File exists under a different server ID — remap to new ID
            fileCache[song.id] = match.first
            return true
        }
        return false
    }

    private fun buildContentKey(song: Song): String {
        val artist = (song.artist ?: "Unknown").replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val title = song.title.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        return "${artist} - ${title}".lowercase()
    }

    /**
     * Build in-memory file cache from all storage locations.
     * Call once before bulk operations to avoid repeated listFiles().
     */
    private fun buildFileCache() {
        fileCache.clear()
        contentKeyCache.clear()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scanMediaStoreToCache()
        } else {
            scanDirToCache(getInternalMusicDir())
        }
        val sdDir = storagePreferences.getMusicDir(StorageLocation.SD_CARD)
        if (sdDir != null) {
            scanDirToCache(sdDir)
        }
    }

    private fun scanMediaStoreToCache() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME
        )
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%$SUBTUNE_DIR%")
        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs, null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idCol)
                val displayName = cursor.getString(nameCol) ?: continue
                val songId = displayName.substringBefore("__", "")
                if (songId.isNotEmpty()) {
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId
                    )
                    val path = uri.toString()
                    fileCache[songId] = path
                    // Build content key from the part after "__"
                    val contentPart = displayName.substringAfter("__", "")
                    if (contentPart.isNotEmpty()) {
                        // Remove extension, lowercase for matching
                        val key = contentPart.substringBeforeLast(".").lowercase()
                        contentKeyCache[key] = Pair(path, songId)
                    }
                }
            }
        }
    }

    private fun scanDirToCache(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            val songId = file.name.substringBefore("__", "")
            if (songId.isNotEmpty()) {
                fileCache[songId] = file.absolutePath
                // Build content key from the part after "__"
                val contentPart = file.name.substringAfter("__", "")
                if (contentPart.isNotEmpty()) {
                    val key = contentPart.substringBeforeLast(".").lowercase()
                    contentKeyCache[key] = Pair(file.absolutePath, songId)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getInternalMusicDir(): File {
        return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), SUBTUNE_DIR)
    }

    suspend fun scanAndRemapDownloads() {
        withContext(Dispatchers.IO) {
            // Build cache once, reuse for both DB update and isSongFileExists
            buildFileCache()

            // Remap: for songs in DB whose IDs have changed on the server,
            // match by content key and update the fileCache with new IDs
            val allSongs = musicRepository.getAllSongsOnce()
            for (song in allSongs) {
                if (fileCache.containsKey(song.id)) continue
                val contentKey = buildContentKey(song)
                val match = contentKeyCache[contentKey]
                if (match != null) {
                    fileCache[song.id] = match.first
                }
            }

            // Single batch DB update using cached data
            if (fileCache.isNotEmpty()) {
                musicRepository.markSongsAsDownloadedBatch(fileCache.toMap())
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
                            if (!isSongFileExistsByContent(song)) {
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
     * Uses in-memory cache to avoid repeated filesystem access.
     */
    private fun resolveLocalPath(song: Song): String? {
        val cached = fileCache[song.id]
        if (cached != null && !cached.startsWith("content://")) return cached
        // For content:// URIs, try to find filesystem path
        val internalDir = getInternalMusicDir()
        val internalFile = File(internalDir, "${song.id}__").let { prefix ->
            internalDir.takeIf { it.exists() }?.listFiles()?.firstOrNull {
                it.name.startsWith("${song.id}__")
            }
        }
        if (internalFile != null) return internalFile.absolutePath

        val sdDir = storagePreferences.getMusicDir(StorageLocation.SD_CARD)
        if (sdDir != null && sdDir.exists()) {
            val sdFile = sdDir.listFiles()?.firstOrNull { it.name.startsWith("${song.id}__") }
            if (sdFile != null) return sdFile.absolutePath
        }
        return null
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
