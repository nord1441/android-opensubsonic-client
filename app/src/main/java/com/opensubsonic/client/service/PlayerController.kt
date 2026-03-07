package com.opensubsonic.client.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.io.File
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.opensubsonic.client.data.db.MusicDao
import com.opensubsonic.client.data.model.PlaybackMode
import com.opensubsonic.client.data.model.ServerConfig
import com.opensubsonic.client.data.model.Song
import com.opensubsonic.client.util.StoragePreferences
import com.opensubsonic.client.util.SubsonicUrlHelper
import com.opensubsonic.client.widget.PlaybackWidgetProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

data class PlayerState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val playbackMode: PlaybackMode = PlaybackMode.SEQUENTIAL,
    val shuffleEnabled: Boolean = false,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1
)

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicDao: MusicDao,
    private val storagePreferences: StoragePreferences
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState

    private var currentQueue: List<Song> = emptyList()
    private var serverConfig: ServerConfig? = null

    fun initialize() {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.let {
                if (it.isDone && !it.isCancelled) it.get() else null
            }
            controller?.addListener(playerListener)
        }, MoreExecutors.directExecutor())
    }

    fun setServer(config: ServerConfig) {
        serverConfig = config
    }

    suspend fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        val ctrl = controller ?: return

        // Resolve local paths from DB for downloaded songs
        val resolvedSongs = songs.map { song ->
            val dbSong = musicDao.getSong(song.id)
            if (dbSong != null && dbSong.isDownloaded && dbSong.localPath != null) {
                song.copy(isDownloaded = true, localPath = dbSong.localPath)
            } else {
                song
            }
        }

        // When offline (no server config), only play downloaded songs
        val server = serverConfig
        val playableSongs = if (server != null) {
            resolvedSongs
        } else {
            resolvedSongs.filter { it.isDownloaded && it.localPath != null }
        }
        if (playableSongs.isEmpty()) return

        // Adjust startIndex for filtered list
        val adjustedIndex = if (server != null) {
            startIndex
        } else {
            val targetSong = resolvedSongs.getOrNull(startIndex)
            playableSongs.indexOf(targetSong).coerceAtLeast(0)
        }

        currentQueue = playableSongs
        val mediaItems = playableSongs.map { song -> song.toMediaItem(server) }

        ctrl.setMediaItems(mediaItems, adjustedIndex, 0)
        ctrl.prepare()
        ctrl.play()

        _playerState.value = _playerState.value.copy(
            queue = playableSongs,
            currentIndex = adjustedIndex,
            currentSong = playableSongs.getOrNull(adjustedIndex)
        )
    }

    fun play() {
        controller?.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(position: Long) {
        controller?.seekTo(position)
    }

    fun setRepeatMode(mode: PlaybackMode) {
        val ctrl = controller ?: return
        when (mode) {
            PlaybackMode.SEQUENTIAL -> {
                ctrl.repeatMode = Player.REPEAT_MODE_OFF
            }
            PlaybackMode.REPEAT_ONE -> {
                ctrl.repeatMode = Player.REPEAT_MODE_ONE
            }
            PlaybackMode.REPEAT_ALL -> {
                ctrl.repeatMode = Player.REPEAT_MODE_ALL
            }
            else -> {
                ctrl.repeatMode = Player.REPEAT_MODE_OFF
            }
        }
        _playerState.value = _playerState.value.copy(playbackMode = mode)
    }

    fun toggleShuffle() {
        val ctrl = controller ?: return
        val newShuffle = !ctrl.shuffleModeEnabled
        ctrl.shuffleModeEnabled = newShuffle
        _playerState.value = _playerState.value.copy(shuffleEnabled = newShuffle)
    }

    fun updatePosition() {
        val ctrl = controller ?: return
        _playerState.value = _playerState.value.copy(
            position = ctrl.currentPosition,
            duration = ctrl.duration.coerceAtLeast(0)
        )
    }

    fun release() {
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
    }

    private fun Song.toMediaItem(server: ServerConfig?): MediaItem {
        val mediaUri: Uri = if (isDownloaded && localPath != null) {
            // Ensure proper URI: content:// stays as-is, file paths get file:// scheme
            if (localPath.startsWith("content://")) {
                localPath.toUri()
            } else {
                Uri.fromFile(File(localPath))
            }
        } else if (server != null) {
            val (format, bitrate) = runBlocking {
                storagePreferences.getStreamFormatSync() to storagePreferences.getStreamBitrateSync()
            }
            Uri.parse(SubsonicUrlHelper.getStreamUrl(server, id, format.apiValue, bitrate.value))
        } else {
            // No local path and no server - cannot play
            return MediaItem.Builder().setMediaId(id).build()
        }

        val artworkUri = if (server != null) {
            coverArt?.let { Uri.parse(SubsonicUrlHelper.getCoverArtUrl(server, it)) }
        } else {
            null
        }

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(mediaUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(artworkUri)
                    .setExtras(Bundle().apply {
                        putString("songId", id)
                    })
                    .build()
            )
            .build()
    }

    private fun updateWidget() {
        val state = _playerState.value
        PlaybackWidgetProvider.updateWidget(
            context,
            state.currentSong?.title,
            state.currentSong?.artist,
            state.isPlaying
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
            updateWidget()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val ctrl = controller ?: return
            val index = ctrl.currentMediaItemIndex
            val song = currentQueue.getOrNull(index)
            _playerState.value = _playerState.value.copy(
                currentSong = song,
                currentIndex = index
            )
            updateWidget()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                val ctrl = controller ?: return
                _playerState.value = _playerState.value.copy(
                    duration = ctrl.duration.coerceAtLeast(0)
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("PlayerController", "Playback error: ${error.message}", error)
            // Skip to next track on error
            val ctrl = controller ?: return
            if (ctrl.hasNextMediaItem()) {
                ctrl.seekToNext()
                ctrl.prepare()
                ctrl.play()
            }
        }
    }
}
