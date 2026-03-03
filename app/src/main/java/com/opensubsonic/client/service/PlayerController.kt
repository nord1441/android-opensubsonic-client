package com.opensubsonic.client.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.opensubsonic.client.data.model.PlaybackMode
import com.opensubsonic.client.data.model.ServerConfig
import com.opensubsonic.client.data.model.Song
import com.opensubsonic.client.util.SubsonicUrlHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PlayerState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val playbackMode: PlaybackMode = PlaybackMode.SEQUENTIAL,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1
)

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context
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

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        val server = serverConfig ?: return
        val ctrl = controller ?: return

        currentQueue = songs
        val mediaItems = songs.map { song -> song.toMediaItem(server) }

        ctrl.setMediaItems(mediaItems, startIndex, 0)
        ctrl.prepare()
        ctrl.play()

        _playerState.value = _playerState.value.copy(
            queue = songs,
            currentIndex = startIndex,
            currentSong = songs.getOrNull(startIndex)
        )
    }

    fun addToQueue(songs: List<Song>) {
        val server = serverConfig ?: return
        val ctrl = controller ?: return

        currentQueue = currentQueue + songs
        songs.forEach { song ->
            ctrl.addMediaItem(song.toMediaItem(server))
        }

        _playerState.value = _playerState.value.copy(queue = currentQueue)
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

    fun setPlaybackMode(mode: PlaybackMode) {
        val ctrl = controller ?: return
        when (mode) {
            PlaybackMode.SEQUENTIAL -> {
                ctrl.shuffleModeEnabled = false
                ctrl.repeatMode = Player.REPEAT_MODE_OFF
            }
            PlaybackMode.SHUFFLE -> {
                ctrl.shuffleModeEnabled = true
                ctrl.repeatMode = Player.REPEAT_MODE_ALL
            }
            PlaybackMode.REPEAT_ONE -> {
                ctrl.shuffleModeEnabled = false
                ctrl.repeatMode = Player.REPEAT_MODE_ONE
            }
            PlaybackMode.REPEAT_ALL -> {
                ctrl.shuffleModeEnabled = false
                ctrl.repeatMode = Player.REPEAT_MODE_ALL
            }
        }
        _playerState.value = _playerState.value.copy(playbackMode = mode)
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

    private fun Song.toMediaItem(server: ServerConfig): MediaItem {
        val streamUrl = if (isDownloaded && localPath != null) {
            localPath
        } else {
            SubsonicUrlHelper.getStreamUrl(server, id)
        }

        val artworkUri = coverArt?.let {
            Uri.parse(SubsonicUrlHelper.getCoverArtUrl(server, it))
        }

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(streamUrl)
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

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val ctrl = controller ?: return
            val index = ctrl.currentMediaItemIndex
            val song = currentQueue.getOrNull(index)
            _playerState.value = _playerState.value.copy(
                currentSong = song,
                currentIndex = index
            )
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                val ctrl = controller ?: return
                _playerState.value = _playerState.value.copy(
                    duration = ctrl.duration.coerceAtLeast(0)
                )
            }
        }
    }
}
