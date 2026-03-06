package com.opensubsonic.client.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.opensubsonic.client.api.SubsonicClient
import com.opensubsonic.client.data.model.ServerConfig
import com.opensubsonic.client.data.model.Song
import com.opensubsonic.client.data.repository.MusicRepository
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
    val currentAlbumName: String? = null
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

        fun buildStableFileName(song: Song): String {
            val suffix = song.suffix ?: "mp3"
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

                val url = SubsonicUrlHelper.getDownloadUrl(server, song.id)
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    _activeDownloads.value = _activeDownloads.value + (song.id to DownloadProgress(song.id, 0f, error = "Download failed"))
                    return@withContext
                }

                val body = response.body ?: return@withContext
                val fileName = buildStableFileName(song)
                val storageLocation = storagePreferences.getStorageLocationSync()

                val localPath = if (storageLocation == StorageLocation.SD_CARD) {
                    val sdDir = storagePreferences.getMusicDir(StorageLocation.SD_CARD)
                    if (sdDir != null) saveToDir(fileName, body.byteStream(), sdDir)
                    else saveToFile(fileName, body.byteStream())
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveWithMediaStore(fileName, song, body.byteStream())
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

    fun clearCompleted() {
        _activeDownloads.value = _activeDownloads.value.filter { !it.value.isComplete && it.value.error == null }
    }
}
