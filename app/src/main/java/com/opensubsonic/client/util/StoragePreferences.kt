package com.opensubsonic.client.util

import android.content.Context
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class StorageLocation {
    INTERNAL,
    SD_CARD
}

enum class TranscodeFormat(val apiValue: String, val label: String) {
    RAW("raw", "Original"),
    MP3("mp3", "MP3"),
    OGG("ogg", "OGG Vorbis"),
    OPUS("opus", "Opus"),
    AAC("aac", "AAC"),
    FLAC("flac", "FLAC");
}

enum class TranscodeBitrate(val value: Int, val label: String) {
    BITRATE_64(64, "64 kbps"),
    BITRATE_96(96, "96 kbps"),
    BITRATE_128(128, "128 kbps"),
    BITRATE_192(192, "192 kbps"),
    BITRATE_256(256, "256 kbps"),
    BITRATE_320(320, "320 kbps"),
    UNLIMITED(0, "Unlimited");
}

@Singleton
class StoragePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val STORAGE_LOCATION_KEY = stringPreferencesKey("storage_location")
        private val STREAM_FORMAT_KEY = stringPreferencesKey("stream_format")
        private val STREAM_BITRATE_KEY = stringPreferencesKey("stream_bitrate")
        private val DOWNLOAD_FORMAT_KEY = stringPreferencesKey("download_format")
        private val DOWNLOAD_BITRATE_KEY = stringPreferencesKey("download_bitrate")
        private const val SUBTUNE_DIR = "SubTune"
    }

    val storageLocation: Flow<StorageLocation> = dataStore.data.map { prefs ->
        when (prefs[STORAGE_LOCATION_KEY]) {
            "SD_CARD" -> StorageLocation.SD_CARD
            else -> StorageLocation.INTERNAL
        }
    }

    suspend fun setStorageLocation(location: StorageLocation) {
        dataStore.edit { prefs ->
            prefs[STORAGE_LOCATION_KEY] = location.name
        }
    }

    suspend fun getStorageLocationSync(): StorageLocation {
        return storageLocation.first()
    }

    // Streaming transcoding preferences
    val streamFormat: Flow<TranscodeFormat> = dataStore.data.map { prefs ->
        prefs[STREAM_FORMAT_KEY]?.let { name ->
            TranscodeFormat.entries.find { it.name == name }
        } ?: TranscodeFormat.RAW
    }

    val streamBitrate: Flow<TranscodeBitrate> = dataStore.data.map { prefs ->
        prefs[STREAM_BITRATE_KEY]?.let { name ->
            TranscodeBitrate.entries.find { it.name == name }
        } ?: TranscodeBitrate.UNLIMITED
    }

    // Download transcoding preferences
    val downloadFormat: Flow<TranscodeFormat> = dataStore.data.map { prefs ->
        prefs[DOWNLOAD_FORMAT_KEY]?.let { name ->
            TranscodeFormat.entries.find { it.name == name }
        } ?: TranscodeFormat.RAW
    }

    val downloadBitrate: Flow<TranscodeBitrate> = dataStore.data.map { prefs ->
        prefs[DOWNLOAD_BITRATE_KEY]?.let { name ->
            TranscodeBitrate.entries.find { it.name == name }
        } ?: TranscodeBitrate.UNLIMITED
    }

    suspend fun setStreamFormat(format: TranscodeFormat) {
        dataStore.edit { it[STREAM_FORMAT_KEY] = format.name }
    }

    suspend fun setStreamBitrate(bitrate: TranscodeBitrate) {
        dataStore.edit { it[STREAM_BITRATE_KEY] = bitrate.name }
    }

    suspend fun setDownloadFormat(format: TranscodeFormat) {
        dataStore.edit { it[DOWNLOAD_FORMAT_KEY] = format.name }
    }

    suspend fun setDownloadBitrate(bitrate: TranscodeBitrate) {
        dataStore.edit { it[DOWNLOAD_BITRATE_KEY] = bitrate.name }
    }

    suspend fun getStreamFormatSync(): TranscodeFormat = streamFormat.first()
    suspend fun getStreamBitrateSync(): TranscodeBitrate = streamBitrate.first()
    suspend fun getDownloadFormatSync(): TranscodeFormat = downloadFormat.first()
    suspend fun getDownloadBitrateSync(): TranscodeBitrate = downloadBitrate.first()

    fun getAvailableStorageLocations(): List<Pair<StorageLocation, File?>> {
        val locations = mutableListOf<Pair<StorageLocation, File?>>()

        // Internal storage is always available
        val internalDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        locations.add(StorageLocation.INTERNAL to internalDir)

        // Check for SD card
        val externalDirs = context.getExternalFilesDirs(Environment.DIRECTORY_MUSIC)
        for (dir in externalDirs) {
            if (dir == null) continue
            // Skip primary storage (internal)
            if (Environment.isExternalStorageEmulated(dir)) continue
            locations.add(StorageLocation.SD_CARD to dir)
        }

        return locations
    }

    fun hasSDCard(): Boolean {
        return getAvailableStorageLocations().any { it.first == StorageLocation.SD_CARD }
    }

    /**
     * Returns the music directory for the given storage location.
     * For internal: /storage/emulated/0/Music/SubTune (public, via MediaStore on API 29+)
     * For SD card: /storage/xxxx-xxxx/Android/data/<package>/files/Music/SubTune (app-specific)
     */
    fun getMusicDir(location: StorageLocation): File? {
        return when (location) {
            StorageLocation.INTERNAL -> {
                @Suppress("DEPRECATION")
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), SUBTUNE_DIR)
            }
            StorageLocation.SD_CARD -> {
                val externalDirs = context.getExternalFilesDirs(Environment.DIRECTORY_MUSIC)
                for (dir in externalDirs) {
                    if (dir == null) continue
                    if (!Environment.isExternalStorageEmulated(dir)) {
                        return File(dir, SUBTUNE_DIR)
                    }
                }
                null
            }
        }
    }
}
