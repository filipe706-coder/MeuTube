package pt.meutube

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import pt.meutube.data.FeedManager
import pt.meutube.ui.FeedScreen
import pt.meutube.ui.HistoryScreen
import pt.meutube.ui.LibraryScreen
import pt.meutube.ui.PlayerScreen
import pt.meutube.ui.PlaylistScreen
import pt.meutube.ui.SearchScreen
import pt.meutube.ui.ShortsPlayerScreen
import pt.meutube.ui.ShortsScreen
import pt.meutube.ui.SubscriptionsScreen

/**
 * O único ecrã "real" da app. Dentro dele, a navegação troca
 * entre os vários ecrãs feitos em Compose:
 *
 *   feed | shorts | search | library      ← barra de baixo
 *   player/{url}                          ← vídeo normal
 *   shortsplayer/{index}                  ← leitor vertical de shorts
 *   subs, history, playlist/{nome}        ← abertos a partir da Biblioteca
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) { App() }
        }
    }

    /**
     * Chamado sempre que a app aparece no ecrã (abrir, voltar de outra app...).
     * Se o feed tiver mais de 30 minutos, atualiza sozinho.
     */
    override fun onResume() {
        super.onResume()
        FeedManager.refreshIfStale()
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("feed", "Vídeos", Icons.Default.Subscriptions),
    Tab("shorts", "Shorts", Icons.Default.Bolt),
    Tab("search", "Pesquisa", Icons.Default.Search),
    Tab("library", "Biblioteca", Icons.Default.VideoLibrary),
)

/** Abre o player. O URL vai codificado para não partir a rota. */
fun NavHostController.openVideo(url: String) = navigate("player/${Uri.encode(url)}")

@Composable
private fun App() {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val tabRoutes = tabs.map { it.route }

    Scaffold(
        bottomBar = {
            // A barra de baixo só aparece nas 4 abas principais
            if (route in tabRoutes) {
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
            composable("shorts") { ShortsScreen(onOpenShort = { i -> nav.navigate("shortsplayer/$i") }) }
            composable("search") { SearchScreen(onOpen = nav::openVideo) }
            composable("library") {
                LibraryScreen(
                    onSubscriptions = { nav.navigate("subs") },
                    onHistory = { nav.navigate("history") },
                    onPlaylist = { name -> nav.navigate("playlist/${Uri.encode(name)}") },
                )
            }

            composable("player/{url}") { entry ->
                val url = Uri.decode(entry.arguments?.getString("url") ?: "")
                PlayerScreen(url = url, onOpen = nav::openVideo)
            }
            composable("shortsplayer/{index}") { entry ->
                val index = entry.arguments?.getString("index")?.toIntOrNull() ?: 0
                ShortsPlayerScreen(startIndex = index)
            }
            composable("subs") { SubscriptionsScreen(onBack = { nav.popBackStack() }) }
            composable("history") { HistoryScreen(onBack = { nav.popBackStack() }, onOpen = nav::openVideo) }
            composable("playlist/{name}") { entry ->
                val name = Uri.decode(entry.arguments?.getString("name") ?: "")
                PlaylistScreen(name = name, onBack = { nav.popBackStack() }, onOpen = nav::openVideo)
            }
        }
    }
}
