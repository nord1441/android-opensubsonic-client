@file:Suppress("DEPRECATION")

package com.opensubsonic.client.ui.screens.playlists

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensubsonic.client.data.model.Playlist
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val serverRepository: ServerRepository
) : ViewModel() {
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    var server: ServerConfig? = null
        private set

    init {
        loadPlaylists()
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                server = serverRepository.getActiveServer()
                _playlists.value = musicRepository.refreshPlaylists()
            } catch (_: Exception) {
                // Offline: load from local DB
                if (_playlists.value.isEmpty()) {
                    server = serverRepository.getActiveServer()
                    _playlists.value = musicRepository.getPlaylistsFlow().first()
                }
            }
            _isLoading.value = false
        }
    }
}

@Composable
fun PlaylistsScreen(
    onPlaylistClick: (String) -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val server = viewModel.server

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "PLAYLISTS",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )

        if (isLoading && playlists.isEmpty()) {
            LoadingIndicator()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 140.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(playlists) { playlist ->
                    ListItemRow(
                        title = playlist.name,
                        subtitle = "${playlist.songCount} tracks · ${formatDuration(playlist.duration)}",
                        coverArtUrl = playlist.coverArt?.let {
                            server?.let { s -> SubsonicUrlHelper.getCoverArtUrl(s, it) }
                        },
                        onClick = { onPlaylistClick(playlist.id) }
                    )
                }
            }
        }
    }
}

// Playlist Detail

data class PlaylistDetailState(
    val playlist: Playlist? = null,
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val musicRepository: MusicRepository,
    private val serverRepository: ServerRepository,
    private val playerController: PlayerController,
    private val downloadManager: DownloadManager
) : ViewModel() {
    private val playlistId: String = savedStateHandle["playlistId"] ?: ""
    private val _uiState = MutableStateFlow(PlaylistDetailState())
    val uiState: StateFlow<PlaylistDetailState> = _uiState
    var server: ServerConfig? = null
        private set

    init {
        viewModelScope.launch {
            try {
                server = serverRepository.getActiveServer()
                val (playlist, songs) = musicRepository.getPlaylistDetail(playlistId)
                _uiState.value = PlaylistDetailState(playlist = playlist, songs = songs, isLoading = false)
            } catch (_: Exception) {
                // Offline: load from local DB
                server = serverRepository.getActiveServer()
                val songs = musicRepository.getPlaylistSongs(playlistId).first()
                _uiState.value = PlaylistDetailState(songs = songs, isLoading = false)
            }
        }
    }

    fun playAll() {
        viewModelScope.launch {
            val songs = _uiState.value.songs
            if (songs.isNotEmpty()) {
                server?.let { playerController.setServer(it) }
                playerController.playSongs(songs)
            }
        }
    }

    fun playSong(song: Song) {
        viewModelScope.launch {
            val songs = _uiState.value.songs
            val index = songs.indexOf(song).coerceAtLeast(0)
            server?.let { playerController.setServer(it) }
            playerController.playSongs(songs, index)
        }
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

@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val server = viewModel.server

    when {
        uiState.isLoading -> LoadingIndicator()
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            item {
                Column {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        CoverArtImage(
                            url = uiState.playlist?.coverArt?.let {
                                server?.let { s -> SubsonicUrlHelper.getCoverArtUrl(s, it, 600) }
                            },
                            modifier = Modifier.size(140.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.playlist?.name ?: "",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${uiState.songs.size} TRACKS",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            uiState.playlist?.comment?.let {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

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
                            Text("PLAY ALL", style = MaterialTheme.typography.labelLarge)
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
