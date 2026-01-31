package org.introskipper.segmenteditor.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.introskipper.segmenteditor.api.JellyfinApiService
import org.introskipper.segmenteditor.data.repository.AuthRepository
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.screen.*
import org.introskipper.segmenteditor.ui.viewmodel.AuthViewModel
import org.introskipper.segmenteditor.ui.viewmodel.AuthViewModelFactory
import org.introskipper.segmenteditor.ui.viewmodel.ConnectionViewModel
import org.introskipper.segmenteditor.ui.viewmodel.ConnectionViewModelFactory

@Composable
fun AppNavigation(
    startDestination: String,
    securePreferences: SecurePreferences,
    apiService: JellyfinApiService
) {
    val navController = rememberNavController()
    val authRepository = remember { AuthRepository(apiService) }
    
    val connectionViewModel: ConnectionViewModel = viewModel(
        factory = ConnectionViewModelFactory(securePreferences, authRepository, apiService)
    )
    
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(securePreferences, authRepository)
    )
    
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.ConnectionWizard.route) {
            ConnectionWizardScreen(
                navController = navController,
                connectionViewModel = connectionViewModel
            )
        }
        
        composable(Screen.ServerEntry.route) {
            ServerEntryScreen(
                navController = navController,
                viewModel = connectionViewModel
            )
        }
        
        composable(Screen.ServerDiscovery.route) {
            ServerDiscoveryScreen(
                navController = navController,
                viewModel = connectionViewModel
            )
        }
        
        composable(Screen.Authentication.route) {
            AuthenticationScreen(
                navController = navController,
                viewModel = authViewModel
            )
        }
        
        composable(Screen.ConnectionSuccess.route) {
            ConnectionSuccessScreen(
                navController = navController,
                viewModel = authViewModel
            )
        }
        
        composable(Screen.Main.route) {
            MainScreen()
        }
        
        composable(Screen.Home.route) {
            HomeScreen(
                onMediaItemClick = { route ->
                    // Route can be either "itemId" or "series/itemId", "album/itemId", "artist/itemId"
                    navController.navigate(route)
                }
            )
        }
        
        composable(
            route = "${Screen.Player.route}/{itemId}",
            arguments = listOf(navArgument("itemId") { type = NavType.StringType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
            PlayerScreen(itemId = itemId, navController = navController)
        }
        
        composable(
            route = "${Screen.Series.route}/{seriesId}",
            arguments = listOf(navArgument("seriesId") { type = NavType.StringType })
        ) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getString("seriesId") ?: ""
            SeriesScreen(
                seriesId = seriesId,
                navController = navController,
                securePreferences = securePreferences
            )
        }
        
        composable(
            route = "${Screen.Album.route}/{albumId}",
            arguments = listOf(navArgument("albumId") { type = NavType.StringType })
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getString("albumId") ?: ""
            AlbumScreen(
                albumId = albumId,
                navController = navController,
                securePreferences = securePreferences
            )
        }
        
        composable(
            route = "${Screen.Artist.route}/{artistId}",
            arguments = listOf(navArgument("artistId") { type = NavType.StringType })
        ) { backStackEntry ->
            val artistId = backStackEntry.arguments?.getString("artistId") ?: ""
            ArtistScreen(
                artistId = artistId,
                navController = navController,
                securePreferences = securePreferences
            )
        }
    }
}

@Composable
fun MainScreen() {
    androidx.compose.material3.Surface {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Text("Main App Screen - Coming Soon")
        }
    }
}
