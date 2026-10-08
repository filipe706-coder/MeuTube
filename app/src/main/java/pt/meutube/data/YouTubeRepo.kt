package pt.meutube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.feed.FeedInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Toda a conversa com o YouTube passa por aqui.
 * O resto da app nunca toca no extractor diretamente: só chama
 * estas funções e recebe as nossas classes simples (Models.kt).
 *
 * Tudo corre em Dispatchers.IO porque são pedidos de rede:
 * no Android é proibido fazê-los na thread da interface.
 */
object YouTubeRepo {

    private val yt = ServiceList.YouTube

    /** Pesquisa de vídeos. */
    suspend fun search(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val handler = yt.searchQHFactory.fromQuery(query)
        SearchInfo.getInfo(yt, handler)
            .relatedItems
            .filterIsInstance<StreamInfoItem>() // ignora canais e playlists
            .map { it.toVideoItem() }
    }

    /**
     * Abre um vídeo: é aqui que acontece a "magia" do passo 2 e 3
     * (obter e desbaralhar os links dos streams).
     */
    suspend fun video(url: String): VideoDetails = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(yt, url)

        // O melhor áudio disponível (para juntar aos vídeos sem som)
        val bestAudio = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .maxByOrNull { it.averageBitrate }

        // Vídeos que já trazem som (normalmente só 360p)
        val muxed = info.videoStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .map { Quality(it.resolution, it.height, it.content, null) }

        // Vídeos sem som (720p, 1080p...). Só servem se houver áudio para juntar.
        val videoOnly = if (bestAudio == null) emptyList() else info.videoOnlyStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .filter { it.height <= 1080 } // acima disso pesa muito no telemóvel
            .map { Quality(it.resolution, it.height, it.content, bestAudio.content) }

        // Uma entrada por resolução, da melhor para a pior.
        // Preferimos a versão com som incluído quando existem as duas.
        val qualities = (muxed + videoOnly)
            .groupBy { it.height }
            .map { (_, list) -> list.firstOrNull { it.audioUrl == null } ?: list.first() }
            .sortedByDescending { it.height }

        VideoDetails(
            title = info.name,
            uploader = info.uploaderName ?: "",
            uploaderUrl = info.uploaderUrl ?: "",
            uploaderAvatar = info.uploaderAvatars.best(),
            description = info.description?.content() ?: "",
            thumbnail = info.thumbnails.best(),
            qualities = qualities,
            related = info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideoItem() },
        )
    }

    /** Dados de um canal (para o guardar nas subscrições). */
    suspend fun channel(url: String): Channel = withContext(Dispatchers.IO) {
        val info = ChannelInfo.getInfo(yt, url)
        Channel(info.url, info.name, info.avatars.best())
    }

    /**
     * O feed: vai buscar os vídeos recentes de cada canal subscrito
     * AO MESMO TEMPO (async) e junta tudo por ordem de data.
     * Usa o feed RSS do YouTube, que é leve e rápido.
     * Se um canal falhar, ignora-o em vez de estragar o feed todo.
     */
    suspend fun feed(channels: List<Channel>): List<VideoItem> = coroutineScope {
        channels.map { ch ->
            async(Dispatchers.IO) {
                runCatching {
                    FeedInfo.getInfo(yt, ch.url).relatedItems.map { it.toVideoItem() }
                }.getOrDefault(emptyList())
            }
        }.awaitAll()
            .flatten()
            .sortedByDescending { it.uploadedAt ?: 0L }
    }

    // ---------- conversões ----------

    private fun StreamInfoItem.toVideoItem() = VideoItem(
        url = url,
        title = name,
        uploader = uploaderName ?: "",
        thumbnail = thumbnails.best(),
        durationSec = duration,
        uploadedAt = uploadDate?.instant?.toEpochMilli(),
        uploadedText = textualUploadDate,
    )

    /** O YouTube dá várias imagens em tamanhos diferentes; escolhemos a maior. */
    private fun List<Image>.best(): String? = maxByOrNull { it.height }?.url
}
