package pt.meutube.data

/**
 * As nossas próprias classes simples. O extractor tem classes muito
 * mais complexas; convertemos para estas para a interface ser fácil
 * de escrever e para as podermos guardar no telemóvel.
 */

/** Um vídeo (ou short) numa lista: feed, pesquisa, histórico, playlist. */
data class VideoItem(
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnail: String?,
    val durationSec: Long,
    val uploadedAt: Long? = null, // milissegundos (no histórico: quando o viste)
    val uploadedText: String? = null, // "há 3 dias"
    val channelUrl: String? = null, // de que subscrição veio (para o feed)
)

/** Um canal subscrito. */
data class Channel(
    val url: String,
    val name: String,
    val avatar: String?,
)

/**
 * Uma playlist importada do YouTube (Takeout).
 * O Takeout só traz os IDs dos vídeos; o título de cada um
 * é pedido depois, quando abres a playlist (ver VideoMeta).
 */
data class Playlist(
    val name: String,
    val videoIds: List<String>,
)

/** Título e canal de um vídeo, guardados em cache para as playlists. */
data class VideoMeta(
    val title: String,
    val uploader: String,
)

/** Tudo o que o ecrã do player precisa. */
data class VideoDetails(
    val title: String,
    val uploader: String,
    val uploaderUrl: String,
    val uploaderAvatar: String?,
    val description: String,
    val thumbnail: String?,
    val qualities: List<Quality>,
    val related: List<VideoItem>, // vídeos sugeridos
)

/**
 * Uma qualidade que o utilizador pode escolher.
 * Em qualidades altas o YouTube manda vídeo e áudio em ficheiros separados,
 * por isso audioUrl pode vir preenchido; o player junta os dois.
 */
data class Quality(
    val label: String, // "720p"
    val height: Int,
    val videoUrl: String,
    val audioUrl: String?, // null = o vídeo já traz o som
)

/**
 * Pequenas funções para lidar com IDs do YouTube.
 * Um vídeo tem um ID de 11 caracteres (ex: dQw4w9WgXcQ) e pode aparecer
 * em vários formatos de link; aqui normalizamos tudo.
 */
object YtIds {
    private val VIDEO_ID = Regex("""(?:v=|/shorts/|youtu\.be/|/embed/)([\w-]{11})""")
    private val CHANNEL_ID = Regex("""(UC[\w-]{22})""")

    fun videoId(url: String): String? = VIDEO_ID.find(url)?.groupValues?.get(1)

    fun channelId(text: String): String? = CHANNEL_ID.find(text)?.groupValues?.get(1)

    fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

    fun channelUrl(id: String) = "https://www.youtube.com/channel/$id"

    /** Miniatura que o YouTube gera para qualquer vídeo, sem precisar de pedido extra. */
    fun thumbnail(id: String) = "https://i.ytimg.com/vi/$id/mqdefault.jpg"

    /** Serve para comparar vídeos vindos de sítios diferentes (feed, shorts...). */
    fun key(url: String) = videoId(url) ?: url
}
