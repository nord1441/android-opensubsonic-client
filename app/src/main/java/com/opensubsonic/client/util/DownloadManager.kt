package com.opensubsonic.client.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val musicRepository: MusicRepository
) {
    private val _activeDownloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, DownloadProgress>> = _activeDownloads

    suspend fun downloadSong(song: Song, server: ServerConfig) {
        withContext(Dispatchers.IO) {
            try {
                _activeDownloads.value = _activeDownloads.value + (song.id to DownloadProgress(song.id, 0f))

                val url = SubsonicUrlHelper.getDownloadUrl(server, song.id)
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    _activeDownloads.value = _activeDownloads.value + (song.id to DownloadProgress(song.id, 0f, error = "Download failed"))
                    return@withContext
                }

                val body = response.body ?: return@withContext
                val contentLength = body.contentLength()
                val suffix = song.suffix ?: "mp3"
                val fileName = "${song.artist ?: "Unknown"} - ${song.title}.$suffix"
                val sanitizedFileName = fileName.replace(Regex("[/\\\\:*?\"<>|]"), "_")

                val localPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveWithMediaStore(sanitizedFileName, song, body.bytes(), suffix)
                } else {
                    saveToFile(sanitizedFileName, body.bytes())
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

    private fun saveWithMediaStore(fileName: String, song: Song, data: ByteArray, suffix: String): String? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, song.contentType ?: "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/SubTune")
            put(MediaStore.Audio.Media.TITLE, song.title)
            put(MediaStore.Audio.Media.ARTIST, song.artist ?: "Unknown")
            put(MediaStore.Audio.Media.ALBUM, song.album ?: "Unknown")
            if (song.track != null) put(MediaStore.Audio.Media.TRACK, song.track)
            if (song.duration > 0) put(MediaStore.Audio.Media.DURATION, song.duration * 1000L)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null

        resolver.openOutputStream(uri)?.use { it.write(data) }

        contentValues.clear()
        contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)

        return uri.toString()
    }

    @Suppress("DEPRECATION")
    private fun saveToFile(fileName: String, data: ByteArray): String? {
        val musicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "SubTune")
        if (!musicDir.exists()) musicDir.mkdirs()
        val file = File(musicDir, fileName)
        file.writeBytes(data)
        return file.absolutePath
    }

    fun clearCompleted() {
        _activeDownloads.value = _activeDownloads.value.filter { !it.value.isComplete && it.value.error == null }
    }
}
