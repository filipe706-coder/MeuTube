package pt.meutube.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pt.meutube.data.FeedManager
import pt.meutube.data.Storage
import pt.meutube.data.TakeoutImporter

/**
 * Aba "Biblioteca": importar do YouTube, subscrições, playlists e histórico.
 */
@Composable
fun LibraryScreen(
    onSubscriptions: () -> Unit,
    onHistory: () -> Unit,
    onPlaylist: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var importMsg by remember { mutableStateOf<String?>(null) }

    // Abre o seletor de ficheiros do Android. Quando escolhes um,
    // recebemos o seu "Uri" (o endereço do ficheiro) e importamos.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            importMsg = try {
                val r = TakeoutImporter.import(context, uri)
                if (r.totalSubscriptions == 0 && r.playlists == 0) {
                    "Não encontrei subscrições nem playlists neste ficheiro."
                } else {
                    FeedManager.refresh() // vai logo buscar os vídeos dos canais novos
                    "Importado: ${r.totalSubscriptions} subscrições (${r.newSubscriptions} novas) " +
                        "e ${r.playlists} playlists com ${r.playlistVideos} vídeos."
                }
            } catch (e: Exception) {
                "Erro a importar: ${e.message}"
            }
            importing = false
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                "Biblioteca",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
            )
        }

        // Cartão de importação
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Importar do YouTube", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "1. Abre takeout.google.com e escolhe só \"YouTube e YouTube Music\".\n" +
                            "2. Em \"Todos os dados do YouTube incluídos\", deixa subscrições e playlists.\n" +
                            "3. Exporta, descarrega o .zip e escolhe-o aqui.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { picker.launch(arrayOf("application/zip", "text/*", "application/octet-stream")) },
                            enabled = !importing,
                        ) {
                            Icon(Icons.Default.FileDownload, null)
                            Text("  Escolher ficheiro")
                        }
                        if (importing) CircularProgressIndicator(Modifier.padding(start = 16.dp).size(24.dp))
                    }
                    importMsg?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }

        item {
            LibraryRow(Icons.Default.Subscriptions, "Subscrições", "${Storage.subscriptions.size} canais", onSubscriptions)
        }
        item {
            LibraryRow(Icons.Default.History, "Histórico", "${Storage.history.size} vídeos", onHistory)
        }

        item {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                "Playlists",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
            )
            if (Storage.playlists.isEmpty()) {
                Text(
                    "As playlists importadas (incluindo os vídeos com \"Gosto\") aparecem aqui.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        items(Storage.playlists, key = { it.name }) { p ->
            LibraryRow(Icons.AutoMirrored.Filled.PlaylistPlay, p.name, "${p.videoIds.size} vídeos") { onPlaylist(p.name) }
        }
    }
}

@Composable
private fun LibraryRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null)
        Column {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
