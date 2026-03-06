package com.opensubsonic.client.ui.screens.artists

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensubsonic.client.data.model.Artist
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
class ArtistsViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val serverRepository: ServerRepository
) : ViewModel() {
    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists: StateFlow<List<Artist>> = _artists

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    var server: ServerConfig? = null
        private set

    init {
        loadArtists()
    }

    fun loadArtists() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                server = serverRepository.getActiveServer()
                _artists.value = musicRepository.refreshArtists()
            } catch (_: Exception) {
                // Offline: load from local DB
                if (_artists.value.isEmpty()) {
                    server = serverRepository.getActiveServer()
                    _artists.value = musicRepository.getArtistsFlow().first()
                }
            }
            _isLoading.value = false
        }
    }
}

@Composable
fun ArtistsScreen(
    onArtistClick: (String) -> Unit,
    viewModel: ArtistsViewModel = hiltViewModel()
) {
    val artists by viewModel.artists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val server = viewModel.server

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "ARTISTS",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )

        if (isLoading && artists.isEmpty()) {
            LoadingIndicator()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 140.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(artists) { artist ->
                    ListItemRow(
                        title = artist.name,
                        subtitle = "${artist.albumCount} albums",
                        coverArtUrl = artist.coverArt?.let {
                            server?.let { s -> SubsonicUrlHelper.getCoverArtUrl(s, it) }
                        },
                        onClick = { onArtistClick(artist.id) }
                    )
                }
            }
        }
    }
}
