package pt.meutube.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pt.meutube.data.Storage

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val history = Storage.history

    Column(Modifier.fillMaxSize()) {
        BackHeader("Histórico", onBack) {
            if (history.isNotEmpty()) {
                TextButton(onClick = { Storage.clearHistory() }) { Text("Limpar") }
            }
        }
        if (history.isEmpty()) {
            Message("Os vídeos que vires aparecem aqui.")
        } else {
            VideoList(history.toList(), onOpen)
        }
    }
}
