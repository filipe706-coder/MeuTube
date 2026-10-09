package pt.meutube.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import pt.meutube.data.FeedManager
import pt.meutube.data.OkHttpDownloader
import pt.meutube.data.Storage
import pt.meutube.data.YouTubeRepo

/**
 * Leitor de Shorts em ecrã inteiro, a deslizar na vertical.
 *
 * Como funciona:
 *  - O VerticalPager mostra uma "página" por short.
 *  - Há UM só ExoPlayer. Sempre que mudas de página, vamos buscar
 *    o link do short dessa página e damo-lo ao player.
 *  - Só a página atual mostra o vídeo; as outras mostram a miniatura.
 *  - Tocar no ecrã pausa/retoma.
 */
@OptIn(UnstableApi::class)
@Composable
fun ShortsPlayerScreen(startIndex: Int) {
    val context = LocalContext.current

    // "Fotografia" da lista no momento em que abriste: se o feed atualizar
    // enquanto vês, as posições não mudam debaixo dos teus dedos.
    val shorts = remember { FeedManager.shorts }
    if (shorts.isEmpty()) return

    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, shorts.lastIndex)) { shorts.size }

    val player = remember {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    val dataSource = remember {
        OkHttpDataSource.Factory(OkHttpDownloader.client).setUserAgent(OkHttpDownloader.USER_AGENT)
    }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Sempre que a página atual muda, carrega e toca esse short
    LaunchedEffect(pager.currentPage) {
        val short = shorts[pager.currentPage]
        player.stop()
        loading = true
        error = null
        try {
            val d = YouTubeRepo.video(short.url)
            val q = d.qualities.firstOrNull { it.height <= 1080 } ?: d.qualities.firstOrNull()
            if (q == null) {
                error = "Este short não tem stream reproduzível."
            } else {
                player.setMediaSource(buildSource(q, dataSource))
                player.prepare()
                player.playWhenReady = true
                Storage.addToHistory(short)
            }
        } catch (e: Exception) {
            error = "Não foi possível abrir: ${e.message}"
        }
        loading = false
    }

    VerticalPager(
        state = pager,
        modifier = Modifier.fillMaxSize().background(Color.Black),
        beyondViewportPageCount = 1,
    ) { page ->
        val short = shorts[page]
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { player.playWhenReady = !player.playWhenReady },
        ) {
            if (page == pager.currentPage && !loading && error == null) {
                AndroidView(
                    factory = {
                        PlayerView(it).apply {
                            useController = false // sem barra de controlos, como no YouTube
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            this.player = player
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                AsyncImage(
                    model = short.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                if (page == pager.currentPage && loading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                }
            }

            // Título e canal em baixo, por cima de um gradiente para se lerem bem
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                    .padding(16.dp),
            ) {
                if (page == pager.currentPage) {
                    error?.let { Text(it, color = Color(0xFFFF8A80)) }
                }
                Text("@" + short.uploader, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(short.title, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
