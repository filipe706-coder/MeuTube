package pt.meutube.data

/**
 * As nossas próprias classes simples. O extractor tem classes muito
 * mais complexas; convertemos para estas para a interface ser fácil
 * de escrever e para as podermos guardar no telemóvel.
 */

/** Um vídeo numa lista (pesquisa, feed, histórico). */
data class VideoItem(
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnail: String?,
    val durationSec: Long,
    val uploadedAt: Long? = null, // milissegundos, quando se sabe (para ordenar o feed)
    val uploadedText: String? = null, // "há 3 dias"
)

/** Um canal subscrito. */
data class Channel(
    val url: String,
    val name: String,
    val avatar: String?,
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
