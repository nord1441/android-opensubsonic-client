package com.opensubsonic.client.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Home : Screen("home")
    data object Albums : Screen("albums")
    data object AlbumDetail : Screen("album/{albumId}") {
        fun createRoute(albumId: String) = "album/$albumId"
    }
    data object Artists : Screen("artists")
    data object ArtistDetail : Screen("artist/{artistId}") {
        fun createRoute(artistId: String) = "artist/$artistId"
    }
    data object Genres : Screen("genres")
    data object GenreDetail : Screen("genre/{genreName}") {
        fun createRoute(genreName: String) = "genre/$genreName"
    }
    data object Playlists : Screen("playlists")
    data object PlaylistDetail : Screen("playlist/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist/$playlistId"
    }
    data object Player : Screen("player")
    data object Settings : Screen("settings")
    data object Downloads : Screen("downloads")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "HOME", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(Screen.Albums, "ALBUMS", Icons.Filled.Album, Icons.Outlined.Album),
    BottomNavItem(Screen.Artists, "ARTISTS", Icons.Filled.Person, Icons.Outlined.Person),
    BottomNavItem(Screen.Playlists, "LISTS", Icons.Filled.QueueMusic, Icons.Outlined.QueueMusic),
    BottomNavItem(Screen.Settings, "MORE", Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz)
)
