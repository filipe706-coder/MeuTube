package pt.meutube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import pt.meutube.data.Channel
import pt.meutube.data.FeedManager
import pt.meutube.data.Storage

/** Lista dos canais subscritos, por ordem alfabética, com opção de anular. */
@Composable
fun SubscriptionsScreen(onBack: () -> Unit) {
    var toRemove by remember { mutableStateOf<Channel?>(null) }
    val subs = Storage.subscriptions.sortedBy { it.name.lowercase() }

    Column(Modifier.fillMaxSize()) {
        BackHeader("Subscrições (${subs.size})", onBack)
        if (subs.isEmpty()) {
            Message("Ainda não tens subscrições.")
            return@Column
        }
        LazyColumn {
            items(subs, key = { it.url }) { ch ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (ch.avatar != null) {
                        AsyncImage(ch.avatar, null, Modifier.size(40.dp).clip(CircleShape))
                    } else {
                        // Sem foto (canais importados): mostra a inicial
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                ch.name.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text(ch.name, Modifier.weight(1f), maxLines = 1)
                    TextButton(onClick = { toRemove = ch }) { Text("Anular") }
                }
            }
        }
    }

    toRemove?.let { ch ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text("Anular subscrição?") },
            text = { Text(ch.name) },
            confirmButton = {
                TextButton(onClick = {
                    Storage.unsubscribe(ch.url)
                    FeedManager.removeChannel(ch.url)
                    toRemove = null
                }) { Text("Anular") }
            },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text("Cancelar") } },
        )
    }
}
