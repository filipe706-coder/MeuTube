package pt.meutube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import pt.meutube.data.FeedManager
import pt.meutube.data.VideoItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Peças de interface reutilizadas em vários ecrãs.
 */

/** Uma linha com miniatura + título + canal. */
@Composable
fun VideoRow(video: VideoItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(160.dp).aspectRatio(16f / 9f)) {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
            )
            if (video.durationSec > 0) {
                Text(
                    formatDuration(video.durationSec),
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                video.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )
            Text(
                listOfNotNull(video.uploader, video.uploadedText).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun VideoList(videos: List<VideoItem>, onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        // distinctBy: a mesma lista não pode ter o mesmo vídeo duas vezes
        // (o LazyColumn usa o url como "chave" e chaves repetidas fazem a app fechar)
        items(videos.distinctBy { it.url }, key = { it.url }) { v -> VideoRow(v) { onOpen(v.url) } }
    }
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 3725 segundos -> "1:02:05" */
fun formatDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * Cabeçalho das abas Vídeos e Shorts:
 *   Título                         [⟳]
 *   Atualizado às 17:40  /  A atualizar 12 de 80 canais…
 */
@Composable
fun FeedHeader(title: String) {
    val fm = FeedManager
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            val status = when {
                fm.refreshing -> "A atualizar ${fm.done} de ${fm.total} canais…"
                fm.lastRefresh == 0L -> "Ainda não atualizado"
                else -> "Atualizado às " + SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(Date(fm.lastRefresh)) +
                    (if (fm.failed > 0) " · ${fm.failed} canais falharam" else "")
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { fm.refresh() }, enabled = !fm.refreshing) {
            Icon(Icons.Default.Refresh, "Atualizar")
        }
    }
    if (fm.refreshing && fm.total > 0) {
        LinearProgressIndicator(
            progress = { fm.done.toFloat() / fm.total },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

/** Barra de topo com seta para voltar (Biblioteca, playlists, etc.). */
@Composable
fun BackHeader(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}
