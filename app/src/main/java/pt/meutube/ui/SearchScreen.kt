package pt.meutube.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pt.meutube.data.VideoItem
import pt.meutube.data.YouTubeRepo

/**
 * Estado da pesquisa guardado fora do ecrã, para não se perder
 * quando mudas de separador e voltas.
 */
private object SearchState {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<VideoItem>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

@Composable
fun SearchScreen(onOpen: (String) -> Unit) {
    val s = SearchState
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    fun runSearch() {
        if (s.query.isBlank()) return
        focus.clearFocus() // esconde o teclado
        scope.launch {
            s.loading = true
            s.error = null
            try {
                s.results = YouTubeRepo.search(s.query)
            } catch (e: Exception) {
                // Sem rede, o YouTube mudou alguma coisa, captcha...
                s.error = "Falhou a pesquisa: ${e.message}"
            }
            s.loading = false
        }
    }

    Column {
        OutlinedTextField(
            value = s.query,
            onValueChange = { s.query = it },
            placeholder = { Text("Pesquisar") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { runSearch() }),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        when {
            s.loading -> Loading()
            s.error != null -> Message(s.error!!)
            s.results.isEmpty() -> Message("Escreve algo e carrega em pesquisar.")
            else -> VideoList(s.results, onOpen)
        }
    }
}
