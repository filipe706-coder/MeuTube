package pt.meutube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.ServiceList
import org.json.JSONObject
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.feed.FeedInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream

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
            .map { Quality(it.resolution, it.level(), it.content, null) }

        // Vídeos sem som (720p, 1080p...). Só servem se houver áudio para juntar.
        val videoOnly = if (bestAudio == null) emptyList() else info.videoOnlyStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .filter { it.level() <= 1080 } // acima disso pesa muito no telemóvel
            .map { Quality(it.resolution, it.level(), it.content, bestAudio.content) }

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
            description = info.description?.content ?: "",
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
     * Os uploads recentes de UM canal, já separados em vídeos e shorts.
     *
     * Como funciona:
     *  1. O feed RSS do canal dá os ~15 uploads mais recentes com a DATA EXATA
     *     (é o que nos deixa ordenar tudo do mais novo para o mais antigo).
     *     Mas o RSS mistura vídeos e shorts sem dizer qual é qual.
     *  2. Por isso pedimos também a aba "Shorts" do canal e ficamos com os IDs.
     *  3. Tudo o que está no RSS e também na aba Shorts é short; o resto é vídeo.
     *
     * Se o canal não tiver aba Shorts (ou falhar), fica tudo como vídeo.
     */
    suspend fun channelUploads(ch: Channel): ChannelUploads = withContext(Dispatchers.IO) {
        val recent = FeedInfo.getInfo(yt, ch.url).relatedItems.map {
            it.toVideoItem().copy(channelUrl = ch.url, uploader = it.uploaderName ?: ch.name)
        }

        // id do short -> miniatura vertical (as da aba Shorts são em pé, 9:16)
        val shortThumbs: Map<String, String?> = runCatching {
            val channelId = YtIds.channelId(ch.url) ?: return@runCatching emptyMap()
            val tab = yt.getChannelTabExtractorFromId("channel/$channelId", ChannelTabs.SHORTS)
            tab.fetchPage()
            ChannelTabInfo.getInfo(tab).relatedItems
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { item -> YtIds.videoId(item.url)?.let { it to item.thumbnails.best() } }
                .toMap()
        }.getOrDefault(emptyMap())

        val (shorts, videos) = recent.partition { YtIds.key(it.url) in shortThumbs }
        ChannelUploads(
            videos = videos,
            shorts = shorts.map { s -> s.copy(thumbnail = shortThumbs[YtIds.key(s.url)] ?: s.thumbnail) },
        )
    }

    /**
     * Título e canal de um vídeo a partir só do ID (para as playlists importadas).
     * Usa o "oEmbed" do YouTube: um endereço público e muito leve que devolve
     * um JSON pequeno com o título. Devolve null se o vídeo foi apagado/é privado.
     */
    suspend fun meta(id: String): VideoMeta? = withContext(Dispatchers.IO) {
        val url = "https://www.youtube.com/oembed?format=json&url=" + YtIds.watchUrl(id)
        val request = okhttp3.Request.Builder().url(url)
            .header("User-Agent", OkHttpDownloader.USER_AGENT).build()
        runCatching {
            OkHttpDownloader.client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val json = JSONObject(r.body!!.string())
                VideoMeta(json.optString("title"), json.optString("author_name"))
            }
        }.getOrNull()
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

    /**
     * O "nível" da qualidade a partir do rótulo: "720p60" -> 720.
     * Não usamos a altura real porque nos shorts (vídeo em pé) um 720p
     * tem 1280 de altura, e isso baralhava a escolha de qualidade.
     */
    private fun VideoStream.level(): Int =
        resolution.takeWhile { it.isDigit() }.toIntOrNull() ?: height

    /** O YouTube dá várias imagens em tamanhos diferentes; escolhemos a maior. */
    private fun List<Image>.best(): String? = maxByOrNull { it.height }?.url
}

/** Resultado de channelUploads(). */
data class ChannelUploads(val videos: List<VideoItem>, val shorts: List<VideoItem>)
