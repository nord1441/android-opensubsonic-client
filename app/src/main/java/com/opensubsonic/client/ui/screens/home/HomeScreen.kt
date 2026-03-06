package com.opensubsonic.client.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensubsonic.client.data.model.Album
import com.opensubsonic.client.data.model.Song
import com.opensubsonic.client.data.model.ServerConfig
import com.opensubsonic.client.data.repository.MusicRepository
import com.opensubsonic.client.data.repository.ServerRepository
import com.opensubsonic.client.service.PlayerController
import com.opensubsonic.client.ui.components.*
import com.opensubsonic.client.util.SubsonicUrlHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val recentAlbums: List<Album> = emptyList(),
    val randomSongs: List<Song> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val serverRepository: ServerRepository,
    private val playerController: PlayerController
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    private var server: ServerConfig? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                server = serverRepository.getActiveServer()
                val recent = musicRepository.getRecentAlbums(20)
                val random = musicRepository.getRandomSongs(30)
                _uiState.value = HomeUiState(
                    recentAlbums = recent,
                    randomSongs = random,
                    isLoading = false
                )
            } catch (_: Exception) {
                // Offline: show cached albums and downloaded songs
                server = serverRepository.getActiveServer()
                val cachedAlbums = musicRepository.getAlbumsFlow().first().take(20)
                val downloadedSongs = musicRepository.getDownloadedSongs().first().take(30)
                _uiState.value = HomeUiState(
                    recentAlbums = cachedAlbums,
                    randomSongs = downloadedSongs,
                    isLoading = false
                )
            }
        }
    }

    fun getServer(): ServerConfig? = server

    fun playRandomSongs() {
        viewModelScope.launch {
            val songs = _uiState.value.randomSongs
            if (songs.isNotEmpty()) {
                server?.let { playerController.setServer(it) }
                playerController.playSongs(songs)
            }
        }
    }

    fun playSong(song: Song, songs: List<Song>) {
        viewModelScope.launch {
            server?.let { playerController.setServer(it) }
            val index = songs.indexOf(song).coerceAtLeast(0)
            playerController.playSongs(songs, index)
        }
    }
}

@Composable
fun HomeScreen(
    onAlbumClick: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val server = viewModel.getServer()

    when {
        uiState.isLoading -> LoadingIndicator()
        uiState.error != null -> ErrorMessage(uiState.error!!, onRetry = viewModel::refresh)
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            item {
                Text(
                    text = "SUBTUNE",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                )
            }

            if (uiState.recentAlbums.isNotEmpty()) {
                item { SectionHeader("RECENTLY ADDED") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.recentAlbums) { album ->
                            AlbumGridItem(
                                title = album.name,
                                subtitle = album.artist,
                                coverArtUrl = album.coverArt?.let {
                                    server?.let { s -> SubsonicUrlHelper.getCoverArtUrl(s, it) }
                                },
                                onClick = { onAlbumClick(album.id) },
                                modifier = Modifier.width(140.dp)
                            )
                        }
                    }
                }
            }

            if (uiState.randomSongs.isNotEmpty()) {
                item {
                    SectionHeader("DISCOVER") {
                        TextButton(onClick = viewModel::playRandomSongs) {
                            Text(
                                "PLAY ALL",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                items(uiState.randomSongs) { song ->
                    SongListItem(
                        title = song.title,
                        artist = song.artist,
                        duration = song.duration,
                        onClick = { viewModel.playSong(song, uiState.randomSongs) }
                    )
                }
            }
        }
    }
}
