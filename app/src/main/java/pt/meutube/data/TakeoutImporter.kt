package pt.meutube.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream

/**
 * Importa os teus dados do YouTube a partir do Google Takeout.
 *
 * O Takeout é um .zip com ficheiros CSV (tabelas em texto, separadas por vírgulas):
 *   .../subscrições/subscrições.csv          -> ID do canal, URL do canal, Título do canal
 *   .../playlists/<Nome da playlist>-vídeos.csv -> ID do vídeo, Data em que foi adicionado
 *   .../playlists/playlists.csv              -> informação geral (ignoramos)
 *
 * Os nomes das pastas mudam com a língua da conta ("subscriptions" / "subscrições"),
 * por isso NÃO confiamos nos nomes: olhamos para o CONTEÚDO de cada CSV.
 *  - tem IDs de canal (começam por "UC" e têm 24 caracteres)? -> subscrições
 *  - a 1.ª coluna tem IDs de vídeo (11 caracteres)?           -> playlist
 */
object TakeoutImporter {

    data class Result(
        val newSubscriptions: Int,
        val totalSubscriptions: Int,
        val playlists: Int,
        val playlistVideos: Int,
    )

    private val VIDEO_ID = Regex("""^[\w-]{11}$""")

    suspend fun import(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        val channels = mutableListOf<Channel>()
        val playlists = mutableListOf<Playlist>()

        // Cada CSV encontrado passa por aqui
        fun handle(fileName: String, text: String) {
            val rows = parseCsv(text)
            val subs = parseSubscriptions(rows)
            if (subs.isNotEmpty()) {
                channels += subs
                return
            }
            val ids = rows.mapNotNull { row -> row.firstOrNull()?.trim()?.takeIf { VIDEO_ID.matches(it) } }
            if (ids.isNotEmpty()) playlists += Playlist(playlistName(fileName), ids.distinct())
        }

        val resolver = context.contentResolver
        val isZip = resolver.openInputStream(uri)!!.use { s ->
            // Um ficheiro .zip começa sempre pelas letras "PK"
            val head = ByteArray(2)
            s.read(head) == 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
        }

        if (isZip) {
            // Lemos o zip "em fluxo": vamos passando pelos ficheiros um a um
            // e só abrimos os .csv. Os outros (vídeos, html...) são saltados.
            ZipInputStream(resolver.openInputStream(uri)!!).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".csv", ignoreCase = true)) {
                        handle(entry.name, zip.readBytes().toString(Charsets.UTF_8))
                    }
                    entry = zip.nextEntry
                }
            }
        } else {
            val text = resolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            handle(uri.lastPathSegment ?: "Playlist", text)
        }

        withContext(Dispatchers.Main) {
            val total = channels.distinctBy { it.url }.size
            val new = Storage.subscribeAll(channels)
            Storage.savePlaylists(playlists)
            Result(new, total, playlists.size, playlists.sumOf { it.videoIds.size })
        }
    }

    /**
     * O CSV das subscrições tem SEMPRE 3 colunas: ID do canal, URL, Título.
     * Exigimos isso porque outros ficheiros do Takeout (comentários, o teu
     * próprio canal...) também têm IDs de canal mas mais colunas.
     */
    private val CHANNEL_ID = Regex("""^UC[\w-]{22}$""")

    private fun parseSubscriptions(rows: List<List<String>>): List<Channel> =
        rows.mapNotNull { row ->
            if (row.size != 3) return@mapNotNull null
            val id = row[0].trim().takeIf { CHANNEL_ID.matches(it) } ?: return@mapNotNull null
            val name = row[2].trim().ifEmpty { id }
            Channel(YtIds.channelUrl(id), name, avatar = null)
        }

    /** "Takeout/.../playlists/Ver mais tarde-vídeos.csv" -> "Ver mais tarde" */
    private fun playlistName(path: String): String =
        path.substringAfterLast('/')
            .removeSuffix(".csv").removeSuffix(".CSV")
            .replace(Regex("""-(vídeos|videos)$""", RegexOption.IGNORE_CASE), "")
            .ifBlank { "Playlist" }

    /**
     * Lê um CSV. Não basta separar por vírgulas porque um título pode ter
     * vírgulas lá dentro; nesse caso vem entre aspas: "Olá, mundo".
     * Por isso percorremos letra a letra e sabemos se estamos dentro de aspas.
     */
    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"'); i++ // "" dentro de aspas = uma aspa
                }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == ',' -> { row.add(field.toString()); field.clear() }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(field.toString()); field.clear()
                    if (row.any { it.isNotBlank() }) rows.add(row)
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        row.add(field.toString())
        if (row.any { it.isNotBlank() }) rows.add(row)
        return rows
    }
}
