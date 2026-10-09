package pt.meutube.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pt.meutube.data.FeedManager
import pt.meutube.data.Storage

/**
 * Aba "Vídeos": os vídeos (sem shorts) das tuas subscrições,
 * do mais recente para o mais antigo.
 *
 * Para atualizar: puxa a lista para baixo, ou carrega no ⟳.
 * A lista vem do FeedManager, que também atualiza sozinho ao abrir a app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(onOpen: (String) -> Unit) {
    val fm = FeedManager

    Column {
        FeedHeader("Vídeos")

        // PullToRefreshBox: o gesto de "puxar para baixo para atualizar"
        PullToRefreshBox(
            isRefreshing = fm.refreshing,
            onRefresh = { fm.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                Storage.subscriptions.isEmpty() -> Message(
                    "Ainda não segues nenhum canal.\n\n" +
                        "Importa as tuas subscrições em Biblioteca → Importar do YouTube, " +
                        "ou abre um vídeo e carrega em \"Subscrever\".",
                )
                fm.videos.isEmpty() && fm.refreshing -> Loading()
                fm.videos.isEmpty() -> Message("Sem vídeos. Puxa para baixo para atualizar.")
                else -> VideoList(fm.videos, onOpen)
            }
        }
    }
}
