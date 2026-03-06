package com.opensubsonic.client.ui.screens.genres

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensubsonic.client.data.model.Album
import com.opensubsonic.client.data.model.Genre
import com.opensubsonic.client.data.model.ServerConfig
import com.opensubsonic.client.data.repository.MusicRepository
import com.opensubsonic.client.data.repository.ServerRepository
import com.opensubsonic.client.ui.components.*
import com.opensubsonic.client.util.SubsonicUrlHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GenresViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {
    private val _genres = MutableStateFlow<List<Genre>>(emptyList())
    val genres: StateFlow<List<Genre>> = _genres

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        viewModelScope.launch {
            try {
                _genres.value = musicRepository.refreshGenres()
            } catch (_: Exception) {
                _genres.value = musicRepository.getGenresFlow().first()
            }
            _isLoading.value = false
        }
    }
}

@Composable
fun GenresScreen(
    onGenreClick: (String) -> Unit,
    viewModel: GenresViewModel = hiltViewModel()
) {
    val genres by viewModel.genres.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "GENRES",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )

        if (isLoading) {
            LoadingIndicator()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 140.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(genres) { genre ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onGenreClick(genre.name) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = genre.name,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${genre.albumCount} albums",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// Genre Detail

data class GenreDetailState(
    val genreName: String = "",
    val albums: List<Album> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class GenreDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val musicRepository: MusicRepository,
    private val serverRepository: ServerRepository
) : ViewModel() {
    private val genreName: String = savedStateHandle["genreName"] ?: ""
    private val _uiState = MutableStateFlow(GenreDetailState(genreName = genreName))
    val uiState: StateFlow<GenreDetailState> = _uiState
    var server: ServerConfig? = null
        private set

    init {
        viewModelScope.launch {
            try {
                server = serverRepository.getActiveServer()
                val albums = musicRepository.getAlbumsByGenre(genreName)
                _uiState.value = GenreDetailState(genreName = genreName, albums = albums, isLoading = false)
            } catch (_: Exception) {
                // Offline: load from local DB
                server = serverRepository.getActiveServer()
                val albums = musicRepository.getCachedAlbumsByGenre(genreName)
                _uiState.value = GenreDetailState(genreName = genreName, albums = albums, isLoading = false)
            }
        }
    }
}

@Composable
fun GenreDetailScreen(
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
    viewModel: GenreDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val server = viewModel.server

    when {
        uiState.isLoading -> LoadingIndicator()
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = uiState.genreName.uppercase(),
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            items(uiState.albums) { album ->
                AlbumGridItem(
                    title = album.name,
                    subtitle = album.artist,
                    coverArtUrl = album.coverArt?.let {
                        server?.let { s -> SubsonicUrlHelper.getCoverArtUrl(s, it) }
                    },
                    onClick = { onAlbumClick(album.id) },
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
}
