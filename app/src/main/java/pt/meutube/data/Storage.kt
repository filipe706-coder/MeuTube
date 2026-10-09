package pt.meutube.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONArray
import org.json.JSONObject

/**
 * Guarda tudo NO TELEMÓVEL (nada vai para a Google):
 *  - subscrições
 *  - histórico
 *  - playlists importadas
 *  - o último feed (para a app abrir logo com vídeos, antes de atualizar)
 *  - títulos dos vídeos das playlists (cache)
 *
 * Usamos SharedPreferences, que é basicamente um pequeno ficheiro
 * chave/valor da app. Cada lista é guardada como texto JSON.
 *
 * As listas são "mutableStateList": quando mudam, o Compose
 * redesenha sozinho os ecrãs que as mostram.
 */
object Storage {
    private lateinit var prefs: SharedPreferences

    val subscriptions = mutableStateListOf<Channel>()
    val history = mutableStateListOf<VideoItem>()
    val playlists = mutableStateListOf<Playlist>()
    val videoMeta = mutableStateMapOf<String, VideoMeta>() // id do vídeo -> título/canal

    private const val MAX_HISTORY = 500

    fun init(context: Context) {
        prefs = context.getSharedPreferences("meutube", Context.MODE_PRIVATE)
        subscriptions.addAll(loadList("subs") { it.toChannel() })
        history.addAll(loadList("history") { it.toVideo() })
        playlists.addAll(loadList("playlists") { it.toPlaylist() })
        loadList("meta") { it }.forEach {
            videoMeta[it.getString("id")] = VideoMeta(it.getString("t"), it.optString("u"))
        }
    }

    // ---------- subscrições ----------

    fun isSubscribed(url: String) = subscriptions.any { sameChannel(it.url, url) }

    fun subscribe(channel: Channel) = subscribeAll(listOf(channel))

    /** Adiciona vários canais de uma vez (usado na importação). Devolve quantos eram novos. */
    fun subscribeAll(channels: List<Channel>): Int {
        val new = channels.filter { !isSubscribed(it.url) }.distinctBy { it.url }
        subscriptions.addAll(new)
        saveSubs()
        return new.size
    }

    fun unsubscribe(url: String) {
        subscriptions.removeAll { sameChannel(it.url, url) }
        saveSubs()
    }

    /** Atualiza nome/avatar de um canal já subscrito. */
    fun updateChannel(channel: Channel) {
        val i = subscriptions.indexOfFirst { sameChannel(it.url, channel.url) }
        if (i >= 0) {
            subscriptions[i] = channel.copy(url = subscriptions[i].url)
            saveSubs()
        }
    }

    /** O mesmo canal pode vir com http/https ou com "www" ou não; comparamos pelo ID. */
    private fun sameChannel(a: String, b: String): Boolean {
        val ia = YtIds.channelId(a)
        val ib = YtIds.channelId(b)
        return if (ia != null && ib != null) ia == ib else a == b
    }

    private fun saveSubs() = saveList("subs", subscriptions) { it.toJson() }

    // ---------- histórico ----------

    /** Põe o vídeo no topo (se já lá estava, sai da posição antiga). */
    fun addToHistory(video: VideoItem) {
        history.removeAll { YtIds.key(it.url) == YtIds.key(video.url) }
        history.add(0, video.copy(uploadedAt = System.currentTimeMillis()))
        while (history.size > MAX_HISTORY) history.removeAt(history.lastIndex)
        saveList("history", history) { it.toJson() }
    }

    fun clearHistory() {
        history.clear()
        saveList("history", history) { it.toJson() }
    }

    // ---------- playlists ----------

    /** Guarda as playlists; se já existir uma com o mesmo nome, é substituída. */
    fun savePlaylists(new: List<Playlist>) {
        val names = new.map { it.name }.toSet()
        playlists.removeAll { it.name in names }
        playlists.addAll(new)
        saveList("playlists", playlists) { it.toJson() }
    }

    fun deletePlaylist(name: String) {
        playlists.removeAll { it.name == name }
        saveList("playlists", playlists) { it.toJson() }
    }

    fun putMeta(id: String, meta: VideoMeta) {
        videoMeta[id] = meta
    }

    /** Grava a cache de títulos (chamado no fim de carregar uma playlist). */
    fun saveMeta() {
        val arr = JSONArray()
        videoMeta.forEach { (id, m) -> arr.put(JSONObject().put("id", id).put("t", m.title).put("u", m.uploader)) }
        prefs.edit().putString("meta", arr.toString()).apply()
    }

    // ---------- cache do feed ----------

    fun saveFeed(videos: List<VideoItem>, shorts: List<VideoItem>, time: Long) {
        saveList("feed_videos", videos) { it.toJson() }
        saveList("feed_shorts", shorts) { it.toJson() }
        prefs.edit().putLong("feed_time", time).apply()
    }

    fun loadFeedVideos() = loadList("feed_videos") { it.toVideo() }
    fun loadFeedShorts() = loadList("feed_shorts") { it.toVideo() }
    fun loadFeedTime() = prefs.getLong("feed_time", 0L)

    // ---------- JSON ----------

    private fun <T> saveList(key: String, list: List<T>, toJson: (T) -> JSONObject) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun <T> loadList(key: String, fromJson: (JSONObject) -> T): List<T> {
        val arr = JSONArray(prefs.getString(key, "[]"))
        return (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
    }

    private fun Channel.toJson() = JSONObject()
        .put("url", url).put("name", name).put("avatar", avatar)

    private fun JSONObject.toChannel() = Channel(
        getString("url"), getString("name"), optString("avatar").ifEmpty { null },
    )

    private fun VideoItem.toJson() = JSONObject()
        .put("url", url).put("title", title).put("uploader", uploader)
        .put("thumb", thumbnail).put("dur", durationSec).put("at", uploadedAt)
        .put("txt", uploadedText).put("ch", channelUrl)

    private fun JSONObject.toVideo() = VideoItem(
        url = getString("url"),
        title = getString("title"),
        uploader = optString("uploader"),
        thumbnail = optString("thumb").ifEmpty { null },
        durationSec = optLong("dur"),
        uploadedAt = if (has("at")) optLong("at") else null,
        uploadedText = optString("txt").ifEmpty { null },
        channelUrl = optString("ch").ifEmpty { null },
    )

    private fun Playlist.toJson() = JSONObject()
        .put("name", name).put("ids", JSONArray(videoIds))

    private fun JSONObject.toPlaylist(): Playlist {
        val ids = getJSONArray("ids")
        return Playlist(getString("name"), (0 until ids.length()).map { ids.getString(it) })
    }
}
