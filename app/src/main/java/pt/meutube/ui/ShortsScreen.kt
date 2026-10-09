package pt.meutube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import pt.meutube.data.FeedManager
import pt.meutube.data.Storage

/**
 * Aba "Shorts": grelha de shorts das subscrições, mais recentes primeiro.
 * Tocar num abre o leitor vertical (ShortsPlayerScreen) nessa posição,
 * e daí podes deslizar para cima/baixo como no YouTube.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsScreen(onOpenShort: (index: Int) -> Unit) {
    val fm = FeedManager

    Column {
        FeedHeader("Shorts")

        PullToRefreshBox(
            isRefreshing = fm.refreshing,
            onRefresh = { fm.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                Storage.subscriptions.isEmpty() -> Message("Importa ou segue canais para veres os shorts deles.")
                fm.shorts.isEmpty() && fm.refreshing -> Loading()
                fm.shorts.isEmpty() -> Message("Sem shorts. Puxa para baixo para atualizar.")
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(fm.shorts, key = { _, s -> s.url }) { i, short ->
                        // Miniatura em pé (9:16) com o título por cima, em baixo
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(9f / 16f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenShort(i) },
                        ) {
                            AsyncImage(
                                model = short.thumbnail,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Text(
                                short.title,
                                color = Color.White,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))),
                                    )
                                    .padding(6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
