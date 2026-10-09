package pt.meutube.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import pt.meutube.data.Storage
import pt.meutube.data.VideoItem
import pt.meutube.data.VideoMeta
import pt.meutube.data.YouTubeRepo
import pt.meutube.data.YtIds

/**
 * Uma playlist importada.
 * O Takeout só traz os IDs, por isso ao abrir a playlist vamos buscar
 * os títulos que ainda não temos (6 de cada vez) e guardamo-los em cache:
 * da próxima vez que abrires, aparecem logo.
 */
@Composable
fun PlaylistScreen(name: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val playlist = Storage.playlists.firstOrNull { it.name == name }
    if (playlist == null) {
        Message("Playlist não encontrada.")
        return
    }

    LaunchedEffect(name) {
        val missing = playlist.videoIds.filter { it !in Storage.videoMeta }
        if (missing.isEmpty()) return@LaunchedEffect
        val limit = Semaphore(6)
        coroutineScope {
            missing.map { id ->
                async {
                    limit.withPermit {
                        val meta = YouTubeRepo.meta(id) ?: VideoMeta("Vídeo indisponível", "")
                        Storage.putMeta(id, meta) // a lista atualiza-se sozinha
                    }
                }
            }.awaitAll()
        }
        Storage.saveMeta()
    }

    // Converte os IDs em VideoItem (com título se já o tivermos)
    val videos = playlist.videoIds.map { id ->
        val meta = Storage.videoMeta[id]
        VideoItem(
            url = YtIds.watchUrl(id),
            title = meta?.title ?: "A carregar…",
            uploader = meta?.uploader ?: "",
            thumbnail = YtIds.thumbnail(id),
            durationSec = 0,
        )
    }

    Column(Modifier.fillMaxSize()) {
        BackHeader("$name (${videos.size})", onBack) {
            IconButton(onClick = { Storage.deletePlaylist(name); onBack() }) {
                Icon(Icons.Default.Delete, "Apagar playlist da app")
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(videos, key = { it.url }) { v -> VideoRow(v) { onOpen(v.url) } }
        }
    }
}
