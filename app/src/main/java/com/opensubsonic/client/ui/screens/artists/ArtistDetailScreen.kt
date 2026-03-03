package com.opensubsonic.client.ui.screens.artists

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.opensubsonic.client.data.model.Artist
import com.opensubsonic.client.data.model.ServerConfig
import com.opensubsonic.client.data.repository.MusicRepository
import com.opensubsonic.client.data.repository.ServerRepository
import com.opensubsonic.client.ui.components.*
import com.opensubsonic.client.util.SubsonicUrlHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistDetailState(
    val artist: Artist? = null,
    val albums: List<Album> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val musicRepository: MusicRepository,
    private val serverRepository: ServerRepository
) : ViewModel() {
    private val artistId: String = savedStateHandle["artistId"] ?: ""
    private val _uiState = MutableStateFlow(ArtistDetailState())
    val uiState: StateFlow<ArtistDetailState> = _uiState
    var server: ServerConfig? = null
        private set

    init {
        viewModelScope.launch {
            try {
                server = serverRepository.getActiveServer()
                val (artist, albums) = musicRepository.getArtistDetail(artistId)
                _uiState.value = ArtistDetailState(artist = artist, albums = albums, isLoading = false)
            } catch (_: Exception) {
                _uiState.value = ArtistDetailState(isLoading = false)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
    viewModel: ArtistDetailViewModel = hiltViewModel()
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
                        text = uiState.artist?.name ?: "",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Text(
                        text = "${uiState.albums.size} ALBUMS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            items(uiState.albums) { album ->
                AlbumGridItem(
                    title = album.name,
                    subtitle = album.year?.toString(),
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
