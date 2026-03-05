package com.opensubsonic.client.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.opensubsonic.client.data.model.ServerConfig
import com.opensubsonic.client.data.repository.ServerRepository
import com.opensubsonic.client.di.ServerConfigHolder
import com.opensubsonic.client.service.PlayerController
import com.opensubsonic.client.ui.navigation.*
import com.opensubsonic.client.ui.screens.albums.AlbumDetailScreen
import com.opensubsonic.client.ui.screens.albums.AlbumsScreen
import com.opensubsonic.client.ui.screens.artists.ArtistDetailScreen
import com.opensubsonic.client.ui.screens.artists.ArtistsScreen
import com.opensubsonic.client.ui.screens.genres.GenreDetailScreen
import com.opensubsonic.client.ui.screens.genres.GenresScreen
import com.opensubsonic.client.ui.screens.home.HomeScreen
import com.opensubsonic.client.ui.screens.login.LoginScreen
import com.opensubsonic.client.ui.screens.player.MiniPlayer
import com.opensubsonic.client.ui.screens.player.PlayerScreen
import com.opensubsonic.client.ui.screens.playlists.PlaylistDetailScreen
import com.opensubsonic.client.ui.screens.playlists.PlaylistsScreen
import com.opensubsonic.client.ui.screens.settings.SettingsScreen
import com.opensubsonic.client.ui.theme.SubTuneTheme
import com.opensubsonic.client.util.DownloadManager
import com.opensubsonic.client.util.SubsonicUrlHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var playerController: PlayerController
    @Inject lateinit var serverRepository: ServerRepository
    @Inject lateinit var serverConfigHolder: ServerConfigHolder
    @Inject lateinit var downloadManager: DownloadManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissions()
        playerController.initialize()

        setContent {
            SubTuneTheme {
                SubTuneApp(
                    playerController = playerController,
                    serverRepository = serverRepository,
                    serverConfigHolder = serverConfigHolder,
                    downloadManager = downloadManager
                )
            }
        }
    }

    override fun onDestroy() {
        playerController.release()
        super.onDestroy()
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        }
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
}

@Composable
fun SubTuneApp(
    playerController: PlayerController,
    serverRepository: ServerRepository,
    serverConfigHolder: ServerConfigHolder,
    downloadManager: DownloadManager
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val playerState by playerController.playerState.collectAsState()
    val bulkDownloadState by downloadManager.bulkDownloadState.collectAsState()
    val scope = rememberCoroutineScope()

    var activeServer by remember { mutableStateOf<ServerConfig?>(null) }

    // Load active server and scan for existing downloads
    LaunchedEffect(Unit) {
        activeServer = serverRepository.getActiveServer()
        activeServer?.let {
            serverConfigHolder.update(it.url, it.username, it.password)
            playerController.setServer(it)
        }
        // Scan for previously downloaded files on startup
        downloadManager.scanAndRemapDownloads()
    }

    val showBottomBar = currentRoute in listOf(
        Screen.Home.route, Screen.Albums.route, Screen.Artists.route,
        Screen.Playlists.route, Screen.Settings.route
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                Column {
                    val coverArtUrl = playerState.currentSong?.coverArt?.let {
                        activeServer?.let { s -> SubsonicUrlHelper.getCoverArtUrl(s, it) }
                    }

                    LaunchedEffect(playerState.isPlaying) {
                        while (playerState.isPlaying) {
                            playerController.updatePosition()
                            kotlinx.coroutines.delay(1000)
                        }
                    }

                    MiniPlayer(
                        playerState = playerState,
                        coverArtUrl = coverArtUrl,
                        onPlayPause = {
                            if (playerState.isPlaying) playerController.pause()
                            else playerController.play()
                        },
                        onNext = { playerController.next() },
                        onClick = { navController.navigate(Screen.Player.route) }
                    )

                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                        tonalElevation = 0.dp
                    ) {
                        bottomNavItems.forEach { item ->
                            val selected = currentRoute == item.screen.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (currentRoute != item.screen.route) {
                                        navController.navigate(item.screen.route) {
                                            popUpTo(Screen.Home.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(
                                        item.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.secondary,
                                    selectedTextColor = MaterialTheme.colorScheme.secondary,
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = if (activeServer != null) Screen.Home.route else Screen.Login.route,
            modifier = Modifier.padding(
                bottom = if (showBottomBar) paddingValues.calculateBottomPadding() else 0.dp
            )
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        scope.launch {
                            activeServer = serverRepository.getActiveServer()
                            activeServer?.let {
                                serverConfigHolder.update(it.url, it.username, it.password)
                                playerController.setServer(it)
                            }
                            downloadManager.scanAndRemapDownloads()
                        }
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                HomeScreen(
                    onAlbumClick = { navController.navigate(Screen.AlbumDetail.createRoute(it)) }
                )
            }

            composable(Screen.Albums.route) {
                AlbumsScreen(
                    onAlbumClick = { navController.navigate(Screen.AlbumDetail.createRoute(it)) }
                )
            }

            composable(
                Screen.AlbumDetail.route,
                arguments = listOf(navArgument("albumId") { type = NavType.StringType })
            ) {
                AlbumDetailScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.Artists.route) {
                ArtistsScreen(
                    onArtistClick = { navController.navigate(Screen.ArtistDetail.createRoute(it)) }
                )
            }

            composable(
                Screen.ArtistDetail.route,
                arguments = listOf(navArgument("artistId") { type = NavType.StringType })
            ) {
                ArtistDetailScreen(
                    onBack = { navController.popBackStack() },
                    onAlbumClick = { navController.navigate(Screen.AlbumDetail.createRoute(it)) }
                )
            }

            composable(Screen.Genres.route) {
                GenresScreen(
                    onGenreClick = { navController.navigate(Screen.GenreDetail.createRoute(it)) }
                )
            }

            composable(
                Screen.GenreDetail.route,
                arguments = listOf(navArgument("genreName") { type = NavType.StringType })
            ) {
                GenreDetailScreen(
                    onBack = { navController.popBackStack() },
                    onAlbumClick = { navController.navigate(Screen.AlbumDetail.createRoute(it)) }
                )
            }

            composable(Screen.Playlists.route) {
                PlaylistsScreen(
                    onPlaylistClick = { navController.navigate(Screen.PlaylistDetail.createRoute(it)) }
                )
            }

            composable(
                Screen.PlaylistDetail.route,
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
            ) {
                PlaylistDetailScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.Player.route) {
                val coverArtUrl = playerState.currentSong?.coverArt?.let {
                    activeServer?.let { s -> SubsonicUrlHelper.getCoverArtUrl(s, it, 600) }
                }
                PlayerScreen(
                    playerController = playerController,
                    coverArtUrl = coverArtUrl,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    server = activeServer,
                    bulkDownloadState = bulkDownloadState,
                    onGenresClick = { navController.navigate(Screen.Genres.route) },
                    onDownloadsClick = { /* TODO */ },
                    onDownloadAllAlbums = {
                        activeServer?.let { server ->
                            scope.launch {
                                downloadManager.downloadAllAlbums(server)
                            }
                        }
                    },
                    onScanDownloads = {
                        scope.launch {
                            downloadManager.scanAndRemapDownloads()
                        }
                    },
                    onLogout = {
                        scope.launch {
                            activeServer?.let { serverRepository.deleteServer(it) }
                            activeServer = null
                        }
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
