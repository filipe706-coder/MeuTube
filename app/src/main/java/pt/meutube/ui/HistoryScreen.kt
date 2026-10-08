package pt.meutube.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pt.meutube.data.Storage

@Composable
fun HistoryScreen(onOpen: (String) -> Unit) {
    val history = Storage.history

    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Histórico", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (history.isNotEmpty()) {
                TextButton(onClick = { Storage.clearHistory() }) { Text("Limpar") }
            }
        }
        if (history.isEmpty()) {
            Message("Os vídeos que vires aparecem aqui.")
        } else {
            // No histórico, "uploadedAt" guarda quando VISTE o vídeo
            VideoList(history.toList(), onOpen)
        }
    }
}
