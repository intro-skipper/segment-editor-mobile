package org.introskipper.segmenteditor.ui.navigation

sealed class Screen(val route: String) {
    object ConnectionWizard : Screen("connection_wizard")
    object ServerEntry : Screen("server_entry")
    object ServerDiscovery : Screen("server_discovery")
    object Authentication : Screen("authentication")
    object ConnectionSuccess : Screen("connection_success")
    object Library : Screen("library")
    object Main : Screen("main")
    object Home : Screen("home")
    object Player : Screen("player") {
        fun createRoute(itemId: String) = "player/$itemId"
    }
    object Series : Screen("series")
    object Album : Screen("album")
    object Artist : Screen("artist")
    object Settings : Screen("settings")
}
