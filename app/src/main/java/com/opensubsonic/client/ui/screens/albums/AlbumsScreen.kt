package com.opensubsonic.client.ui.screens.albums

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensubsonic.client.data.model.Album
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
class AlbumsViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val serverRepository: ServerRepository
) : ViewModel() {
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var server: ServerConfig? = null
    private var offset = 0

    init {
        loadAlbums()
    }

    fun getServer(): ServerConfig? = server

    fun loadAlbums() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                server = serverRepository.getActiveServer()
                val albums = musicRepository.refreshAlbums(offset = offset)
                _albums.value = _albums.value + albums
                offset += albums.size
            } catch (_: Exception) {
                // Offline: load from local DB on first load
                if (_albums.value.isEmpty()) {
                    server = serverRepository.getActiveServer()
                    _albums.value = musicRepository.getAlbumsFlow().first()
                }
            }
            _isLoading.value = false
        }
    }

    fun loadMore() {
        if (!_isLoading.value) loadAlbums()
    }
}

@Composable
fun AlbumsScreen(
    onAlbumClick: (String) -> Unit,
    viewModel: AlbumsViewModel = hiltViewModel()
) {
    val albums by viewModel.albums.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val server = viewModel.getServer()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "ALBUMS",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )

        if (isLoading && albums.isEmpty()) {
            LoadingIndicator()
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(albums) { album ->
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

                item {
                    LaunchedEffect(Unit) { viewModel.loadMore() }
                }
            }
        }
    }
}
