package pt.meutube.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import pt.meutube.data.Channel
import pt.meutube.data.Storage
import pt.meutube.data.VideoItem
import pt.meutube.data.YouTubeRepo

/** O feed fica em memória para não recarregar sempre que mudas de separador. */
private object FeedState {
    var videos by mutableStateOf<List<VideoItem>>(emptyList())
    var loading by mutableStateOf(false)
    var loadedOnce = false
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(onOpen: (String) -> Unit) {
    val s = FeedState
    val scope = rememberCoroutineScope()
    val subs = Storage.subscriptions
    var toRemove by remember { mutableStateOf<Channel?>(null) }

    fun refresh() = scope.launch {
        s.loading = true
        s.videos = YouTubeRepo.feed(subs.toList()) // feed() já ignora canais com erro
        s.loading = false
        s.loadedOnce = true
    }

    // Carrega automaticamente a primeira vez que o ecrã aparece
    LaunchedEffect(Unit) { if (!s.loadedOnce && subs.isNotEmpty()) refresh() }

    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Subscrições", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { refresh() }, enabled = !s.loading && subs.isNotEmpty()) {
                Icon(Icons.Default.Refresh, "Atualizar")
            }
        }

        // Fila de canais. Toque longo = anular subscrição.
        if (subs.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(subs, key = { it.url }) { ch ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(64.dp)
                            .combinedClickable(onClick = {}, onLongClick = { toRemove = ch }),
                    ) {
                        AsyncImage(
                            model = ch.avatar,
                            contentDescription = ch.name,
                            modifier = Modifier.size(48.dp).clip(CircleShape),
                        )
                        Text(
                            ch.name,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        when {
            subs.isEmpty() -> Message(
                "Ainda não segues nenhum canal.\nAbre um vídeo e carrega em \"Subscrever\".",
            )
            s.loading -> Loading()
            s.videos.isEmpty() -> Message("Sem vídeos. Carrega em atualizar.")
            else -> VideoList(s.videos, onOpen)
        }
    }

    // Confirmação antes de anular a subscrição
    toRemove?.let { ch ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text("Anular subscrição?") },
            text = { Text(ch.name) },
            confirmButton = {
                TextButton(onClick = {
                    Storage.unsubscribe(ch.url)
                    s.videos = s.videos.filter { it.uploader != ch.name }
                    toRemove = null
                }) { Text("Anular") }
            },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text("Cancelar") } },
        )
    }
}
