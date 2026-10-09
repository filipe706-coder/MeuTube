package pt.meutube.ui

import android.content.res.Configuration
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import pt.meutube.data.Channel
import pt.meutube.data.OkHttpDownloader
import pt.meutube.data.Quality
import pt.meutube.data.Storage
import pt.meutube.data.VideoDetails
import pt.meutube.data.VideoItem
import pt.meutube.data.YouTubeRepo

/**
 * Ecrã do vídeo. O fluxo é:
 *  1. pedir os detalhes ao YouTubeRepo (título, qualidades, links...)
 *  2. dar o link escolhido ao ExoPlayer
 *  3. guardar no histórico
 *
 * Repara que em lado nenhum se pede um anúncio: o player do YouTube
 * nunca é carregado, só os ficheiros de vídeo/áudio.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(url: String, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var details by remember { mutableStateOf<VideoDetails?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var quality by remember { mutableStateOf<Quality?>(null) }

    // O player vive enquanto este ecrã existir e é libertado ao sair
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    // Fonte de dados: o mesmo OkHttp e User-Agent que usamos para o resto
    val dataSource = remember {
        OkHttpDataSource.Factory(OkHttpDownloader.client)
            .setUserAgent(OkHttpDownloader.USER_AGENT)
    }

    // 1. Carregar os detalhes quando o ecrã abre (ou o URL muda)
    LaunchedEffect(url) {
        try {
            val d = YouTubeRepo.video(url)
            details = d
            // Começa em 720p se existir, senão na melhor disponível
            quality = d.qualities.firstOrNull { it.height <= 720 } ?: d.qualities.firstOrNull()
            if (d.qualities.isEmpty()) error = "Não foi encontrado nenhum stream reproduzível."
            Storage.addToHistory(
                VideoItem(url, d.title, d.uploader, d.thumbnail, durationSec = 0),
            )
        } catch (e: Exception) {
            error = "Não foi possível abrir o vídeo: ${e.message}"
        }
    }

    // 2. Sempre que a qualidade muda, troca o stream mas mantém o tempo
    LaunchedEffect(quality) {
        val q = quality ?: return@LaunchedEffect
        val position = player.currentPosition
        player.setMediaSource(buildSource(q, dataSource))
        player.prepare()
        player.seekTo(position)
        player.playWhenReady = true
    }

    // Em modo horizontal mostra só o vídeo, em ecrã inteiro
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val videoView: @Composable (Modifier) -> Unit = { mod ->
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player } },
            modifier = mod,
        )
    }

    if (landscape) {
        Box(Modifier.fillMaxSize()) { videoView(Modifier.fillMaxSize()) }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                videoView(Modifier.fillMaxSize())
            }
        }

        error?.let { msg -> item { Text(msg, color = Color.Red, modifier = Modifier.padding(16.dp)) } }

        val d = details
        if (d == null) {
            if (error == null) item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { Loading() } }
            return@LazyColumn
        }

        item {
            Text(
                d.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(12.dp),
            )
        }

        // Canal + subscrever + qualidade
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AsyncImage(d.uploaderAvatar, null, Modifier.size(36.dp).clip(CircleShape))
                Text(d.uploader, Modifier.weight(1f), maxLines = 1)

                val subscribed = Storage.isSubscribed(d.uploaderUrl)
                if (subscribed) {
                    OutlinedButton(onClick = { Storage.unsubscribe(d.uploaderUrl) }) { Text("Subscrito") }
                } else {
                    Button(onClick = {
                        scope.launch {
                            // Vai buscar o nome e avatar "oficiais" do canal
                            val ch = runCatching { YouTubeRepo.channel(d.uploaderUrl) }
                                .getOrElse { Channel(d.uploaderUrl, d.uploader, d.uploaderAvatar) }
                            Storage.subscribe(ch)
                        }
                    }) { Text("Subscrever") }
                }

                QualityPicker(d.qualities, quality) { quality = it }
            }
        }

        if (d.description.isNotBlank()) {
            item { Description(d.description) }
        }

        if (d.related.isNotEmpty()) {
            item {
                Text(
                    "A seguir",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(d.related.distinctBy { it.url }, key = { it.url }) { v -> VideoRow(v) { onOpen(v.url) } }
        }
    }
}

/**
 * Se a qualidade tiver vídeo e áudio separados, o MergingMediaSource
 * toca os dois ao mesmo tempo, como se fossem um só ficheiro.
 */
@OptIn(UnstableApi::class)
internal fun buildSource(q: Quality, ds: OkHttpDataSource.Factory): MediaSource {
    val factory = ProgressiveMediaSource.Factory(ds)
    val video = factory.createMediaSource(MediaItem.fromUri(q.videoUrl))
    val audioUrl = q.audioUrl ?: return video
    val audio = factory.createMediaSource(MediaItem.fromUri(audioUrl))
    return MergingMediaSource(video, audio)
}

@Composable
private fun QualityPicker(options: List<Quality>, selected: Quality?, onPick: (Quality) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(selected?.label ?: "—") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { q ->
                DropdownMenuItem(
                    text = { Text(q.label) },
                    onClick = { open = false; onPick(q) },
                )
            }
        }
    }
}

/** Descrição que abre e fecha ao tocar. */
@Composable
private fun Description(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        maxLines = if (expanded) Int.MAX_VALUE else 3,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clickable { expanded = !expanded },
    )
}
