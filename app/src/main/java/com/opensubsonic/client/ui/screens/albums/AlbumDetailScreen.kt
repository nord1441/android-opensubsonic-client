@file:Suppress("DEPRECATION")

package com.opensubsonic.client.ui.screens.albums

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensubsonic.client.data.model.Album
import com.opensubsonic.client.data.model.PlaybackMode
import com.opensubsonic.client.data.model.ServerConfig
import com.opensubsonic.client.data.model.Song
import com.opensubsonic.client.data.repository.MusicRepository
import com.opensubsonic.client.data.repository.ServerRepository
import com.opensubsonic.client.service.PlayerController
import com.opensubsonic.client.ui.components.*
import com.opensubsonic.client.util.DownloadManager
import com.opensubsonic.client.util.SubsonicUrlHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumDetailState(
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val musicRepository: MusicRepository,
    private val serverRepository: ServerRepository,
    private val playerController: PlayerController,
    private val downloadManager: DownloadManager
) : ViewModel() {
    private val albumId: String = savedStateHandle["albumId"] ?: ""
    private val _uiState = MutableStateFlow(AlbumDetailState())
    val uiState: StateFlow<AlbumDetailState> = _uiState
    var server: ServerConfig? = null
        private set

    init {
        loadAlbum()
    }

    private fun loadAlbum() {
        viewModelScope.launch {
            try {
                server = serverRepository.getActiveServer()
                val (album, songs) = musicRepository.getAlbumDetail(albumId)
                _uiState.value = AlbumDetailState(album = album, songs = songs, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = AlbumDetailState(isLoading = false, error = e.message)
            }
        }
    }

    fun playAll() {
        val songs = _uiState.value.songs
        if (songs.isNotEmpty()) {
            server?.let { playerController.setServer(it) }
            playerController.playSongs(songs)
        }
    }

    fun shufflePlay() {
        val songs = _uiState.value.songs.shuffled()
        if (songs.isNotEmpty()) {
            server?.let { playerController.setServer(it) }
            playerController.playSongs(songs)
            playerController.setPlaybackMode(PlaybackMode.SHUFFLE)
        }
    }

    fun playSong(song: Song) {
        val songs = _uiState.value.songs
        val index = songs.indexOf(song).coerceAtLeast(0)
        server?.let { playerController.setServer(it) }
        playerController.playSongs(songs, index)
    }

    fun downloadAll() {
        viewModelScope.launch {
            val s = server ?: return@launch
            _uiState.value.songs.forEach { song ->
                downloadManager.downloadSong(song, s)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playerState by viewModel.run {
        // Access the player controller through the viewModel
        MutableStateFlow(null)
    }.collectAsState()
    val server = viewModel.server

    when {
        uiState.isLoading -> LoadingIndicator()
        uiState.error != null -> ErrorMessage(uiState.error!!)
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            item {
                Column {
                    // Top bar
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }

                    // Album cover & info
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        CoverArtImage(
                            url = uiState.album?.coverArt?.let {
                                server?.let { s -> SubsonicUrlHelper.getCoverArtUrl(s, it, 600) }
                            },
                            modifier = Modifier.size(160.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.album?.name ?: "",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.album?.artist ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            uiState.album?.year?.let {
                                Text(
                                    text = "$it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            uiState.album?.genre?.let {
                                Text(
                                    text = it.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = viewModel::playAll,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PLAY", style = MaterialTheme.typography.labelLarge)
                        }
                        FilledTonalButton(
                            onClick = viewModel::shufflePlay,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Shuffle, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SHUFFLE", style = MaterialTheme.typography.labelLarge)
                        }
                        IconButton(onClick = viewModel::downloadAll) {
                            Icon(Icons.Filled.Download, "Download all")
                        }
                    }

                    Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
                }
            }

            items(uiState.songs) { song ->
                SongListItem(
                    title = song.title,
                    artist = song.artist,
                    duration = song.duration,
                    track = song.track,
                    onClick = { viewModel.playSong(song) }
                )
            }
        }
    }
}
