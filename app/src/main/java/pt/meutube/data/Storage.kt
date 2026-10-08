package pt.meutube.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/**
 * Guarda as subscrições e o histórico NO TELEMÓVEL (nada vai para a Google).
 *
 * Usamos SharedPreferences, que é basicamente um pequeno ficheiro
 * chave/valor da app. Cada lista é guardada como texto JSON.
 * Para centenas de itens chega perfeitamente; se um dia crescer muito,
 * troca-se por uma base de dados (Room) sem mexer nos ecrãs.
 *
 * As listas são "mutableStateList": quando mudam, o Compose
 * redesenha sozinho os ecrãs que as mostram.
 */
object Storage {
    private lateinit var prefs: SharedPreferences

    val subscriptions = mutableStateListOf<Channel>()
    val history = mutableStateListOf<VideoItem>()

    private const val MAX_HISTORY = 500

    fun init(context: Context) {
        prefs = context.getSharedPreferences("meutube", Context.MODE_PRIVATE)
        subscriptions.addAll(loadList("subs") { it.toChannel() })
        history.addAll(loadList("history") { it.toVideo() })
    }

    // ---------- subscrições ----------

    fun isSubscribed(url: String) = subscriptions.any { it.url == url }

    fun subscribe(channel: Channel) {
        if (isSubscribed(channel.url)) return
        subscriptions.add(channel)
        saveList("subs", subscriptions) { it.toJson() }
    }

    fun unsubscribe(url: String) {
        subscriptions.removeAll { it.url == url }
        saveList("subs", subscriptions) { it.toJson() }
    }

    // ---------- histórico ----------

    /** Põe o vídeo no topo (se já lá estava, sai da posição antiga). */
    fun addToHistory(video: VideoItem) {
        history.removeAll { it.url == video.url }
        history.add(0, video.copy(uploadedAt = System.currentTimeMillis()))
        while (history.size > MAX_HISTORY) history.removeAt(history.lastIndex)
        saveList("history", history) { it.toJson() }
    }

    fun clearHistory() {
        history.clear()
        saveList("history", history) { it.toJson() }
    }

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

    private fun JSONObject.toVideo() = VideoItem(
        url = getString("url"),
        title = getString("title"),
        uploader = optString("uploader"),
        thumbnail = optString("thumb").ifEmpty { null },
        durationSec = optLong("dur"),
        uploadedAt = optLong("at"),
    )
}
