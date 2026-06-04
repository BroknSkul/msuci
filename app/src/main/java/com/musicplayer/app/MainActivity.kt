package com.musicplayer.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.musicplayer.app.ui.components.MiniPlayer
import com.musicplayer.app.ui.components.MusicBottomBar
import com.musicplayer.app.ui.screens.*
import com.musicplayer.app.ui.theme.MusicPlayerTheme
import com.musicplayer.app.ui.viewmodel.AppViewModel
import com.musicplayer.app.ui.viewmodel.MusicViewModel
import dagger.hilt.android.AndroidEntryPoint

val LocalMusicViewModel = staticCompositionLocalOf<MusicViewModel> {
    error("No MusicViewModel provided")
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val fontWeightScale by appViewModel.fontWeightScale.collectAsState()
            
            MusicPlayerTheme(fontWeightScale = fontWeightScale) {
                val musicViewModel: MusicViewModel = hiltViewModel()
                CompositionLocalProvider(LocalMusicViewModel provides musicViewModel) {
                    MainScreen(appViewModel = appViewModel)
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    appViewModel: AppViewModel,
    musicViewModel: MusicViewModel = LocalMusicViewModel.current
) {
    val navController = rememberNavController()
    val isGlassMode by appViewModel.isGlassMode.collectAsState()
    val playerState by musicViewModel.playerState.collectAsState()
    val albumCoverUrl by musicViewModel.albumCoverUrl.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentRoute != "player") {
                Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                    if (playerState.currentSong != null) {
                        MiniPlayer(
                            song = playerState.currentSong!!,
                            albumCoverUrl = albumCoverUrl,
                            isPlaying = playerState.isPlaying,
                            progress = playerState.progress,
                            duration = playerState.duration,
                            onPlayPauseClick = { musicViewModel.togglePlayPause() },
                            onNextClick = { musicViewModel.skipNext() },
                            onPreviousClick = { musicViewModel.skipPrevious() },
                            onClick = { navController.navigate("player") }
                        )
                    }
                    MusicBottomBar(navController)
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (isGlassMode) MaterialTheme.colorScheme.background.copy(alpha = 0.5f) else MaterialTheme.colorScheme.background
        ) {
            val contentModifier = if (currentRoute == "player") {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxSize().padding(innerPadding)
            }
            
            Box(modifier = contentModifier) {
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") { 
                        HomeScreen(
                            onSongClick = { song ->
                                musicViewModel.playSong(song)
                                navController.navigate("player")
                            },
                            onPlaylistClick = { playlist ->
                                navController.navigate("playlist/${playlist.id}")
                            },
                            onArtistClick = { artistName ->
                                navController.navigate("artist/$artistName")
                            },
                            onAddToFavorite = { musicViewModel.toggleFavorite(it) },
                            onAddToQueue = { musicViewModel.addToQueue(it) },
                            onSettingsClick = { navController.navigate("settings") },
                            onNotificationClick = { navController.navigate("notifications") }
                        ) 
                    }
                    composable("search") { 
                        SearchScreen(
                            onSongClick = { song ->
                                musicViewModel.playSong(song)
                                navController.navigate("player")
                            },
                            onAddToFavorite = { musicViewModel.toggleFavorite(it) },
                            onAddToQueue = { musicViewModel.addToQueue(it) }
                        ) 
                    }
                    composable("library") { 
                        LibraryScreen(
                            onSongClick = { song ->
                                musicViewModel.playSong(song)
                                navController.navigate("player")
                            },
                            onAddToFavorite = { musicViewModel.toggleFavorite(it) },
                            onAddToQueue = { musicViewModel.addToQueue(it) },
                            onPlaylistClick = { playlist ->
                                navController.navigate("playlist/${playlist.id}")
                            }
                        ) 
                    }
                    composable(
                        route = "playlist/{playlistId}",
                        arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
                        PlaylistScreen(
                            playlistId = playlistId,
                            onSongClick = { song ->
                                musicViewModel.playSong(song)
                                navController.navigate("player")
                            },
                            onAddToFavorite = { musicViewModel.toggleFavorite(it) },
                            onAddToQueue = { musicViewModel.addToQueue(it) },
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "artist/{artistName}",
                        arguments = listOf(navArgument("artistName") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val artistName = backStackEntry.arguments?.getString("artistName") ?: ""
                        ArtistDetailScreen(
                            artistName = artistName,
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                    composable("settings") { 
                        SettingsScreen(appViewModel = appViewModel) 
                    }
                    composable("notifications") {
                        NotificationScreen(onBack = { navController.popBackStack() })
                    }
                    composable("player") { 
                        PlayerScreen(
                            musicViewModel = musicViewModel,
                            appViewModel = appViewModel
                        ) 
                    }
                }
            }
        }
    }
}
