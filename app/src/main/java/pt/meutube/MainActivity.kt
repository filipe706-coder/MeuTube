package pt.meutube

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import pt.meutube.ui.FeedScreen
import pt.meutube.ui.HistoryScreen
import pt.meutube.ui.PlayerScreen
import pt.meutube.ui.SearchScreen

/**
 * O único ecrã "real" da app. Dentro dele, a navegação troca
 * entre os vários ecrãs feitos em Compose:
 *
 *   feed  | search | history   ← barra de baixo
 *   player/{url}               ← abre por cima quando tocas num vídeo
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) { App() }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("feed", "Subscrições", Icons.Default.Subscriptions),
    Tab("search", "Pesquisa", Icons.Default.Search),
    Tab("history", "Histórico", Icons.Default.History),
)

/** Abre o player. O URL vai codificado para não partir a rota. */
fun NavHostController.openVideo(url: String) = navigate("player/${Uri.encode(url)}")

@androidx.compose.runtime.Composable
private fun App() {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route

    Scaffold(
        bottomBar = {
            // Esconde a barra quando se está a ver um vídeo
            if (route?.startsWith("player") != true) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo("feed") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "feed", modifier = Modifier.padding(padding)) {
            composable("feed") { FeedScreen(onOpen = nav::openVideo) }
            composable("search") { SearchScreen(onOpen = nav::openVideo) }
            composable("history") { HistoryScreen(onOpen = nav::openVideo) }
            composable("player/{url}") { entry ->
                val url = Uri.decode(entry.arguments?.getString("url") ?: "")
                PlayerScreen(url = url, onOpen = nav::openVideo)
            }
        }
    }
}
