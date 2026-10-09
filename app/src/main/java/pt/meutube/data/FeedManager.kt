package pt.meutube.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Gere o feed das subscrições (abas Vídeos e Shorts).
 *
 * - Ao abrir a app, mostra logo o último feed guardado (não fica em branco).
 * - refresh() vai buscar os uploads mais recentes de TODOS os canais
 *   e substitui o feed pelos novos, do mais recente para o mais antigo.
 * - refreshIfStale() só atualiza se o feed tiver mais de 30 minutos;
 *   é chamado sempre que a app volta ao ecrã, por isso o feed acompanha
 *   o YouTube sem teres de fazer nada.
 *
 * É um "object" com o seu próprio CoroutineScope: o refresh continua
 * mesmo que mudes de aba a meio.
 */
object FeedManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var videos by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var shorts by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var refreshing by mutableStateOf(false)
        private set
    var lastRefresh by mutableLongStateOf(0L)
        private set

    // Progresso: "A atualizar 12 de 80 canais"
    var done by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    var failed by mutableIntStateOf(0)
        private set

    private const val STALE_MS = 30 * 60 * 1000L // 30 minutos
    private const val PARALLEL = 6 // canais pedidos ao mesmo tempo
    private const val MAX_VIDEOS = 400
    private const val MAX_SHORTS = 300

    /** Carrega o feed guardado no telemóvel (chamado no arranque). */
    fun loadCache() {
        videos = Storage.loadFeedVideos()
        shorts = Storage.loadFeedShorts()
        lastRefresh = Storage.loadFeedTime()
    }

    fun refreshIfStale() {
        if (System.currentTimeMillis() - lastRefresh > STALE_MS) refresh()
    }

    fun refresh() {
        if (refreshing || Storage.subscriptions.isEmpty()) return
        scope.launch {
            refreshing = true
            val channels = Storage.subscriptions.toList()
            total = channels.size
            done = 0
            failed = 0

            // O Semaphore funciona como uma "fila com 6 lugares":
            // só 6 canais estão a ser pedidos de cada vez.
            val limit = Semaphore(PARALLEL)
            val results = channels.map { ch ->
                async {
                    limit.withPermit {
                        val r = runCatching { YouTubeRepo.channelUploads(ch) }
                        done++
                        if (r.isFailure) failed++
                        ch to r.getOrNull()
                    }
                }
            }.awaitAll()

            // Se um canal falhou, mantemos os vídeos antigos dele em vez de os perder
            val newVideos = mutableListOf<VideoItem>()
            val newShorts = mutableListOf<VideoItem>()
            for ((ch, uploads) in results) {
                if (uploads != null) {
                    newVideos += uploads.videos
                    newShorts += uploads.shorts
                } else {
                    newVideos += videos.filter { it.channelUrl == ch.url }
                    newShorts += shorts.filter { it.channelUrl == ch.url }
                }
            }

            // Mais recentes primeiro; sem repetidos
            videos = newVideos.sortedByDescending { it.uploadedAt ?: 0L }
                .distinctBy { YtIds.key(it.url) }.take(MAX_VIDEOS)
            shorts = newShorts.sortedByDescending { it.uploadedAt ?: 0L }
                .distinctBy { YtIds.key(it.url) }.take(MAX_SHORTS)
            lastRefresh = System.currentTimeMillis()
            Storage.saveFeed(videos, shorts, lastRefresh)
            refreshing = false

            fillMissingAvatars()
        }
    }

    /** Remove do feed os vídeos de um canal (quando anulas a subscrição). */
    fun removeChannel(url: String) {
        videos = videos.filter { it.channelUrl != url }
        shorts = shorts.filter { it.channelUrl != url }
        Storage.saveFeed(videos, shorts, lastRefresh)
    }

    /**
     * Os canais importados do Takeout não trazem foto.
     * Depois de cada refresh vamos buscar a foto de até 20 canais
     * (aos poucos, para não fazer centenas de pedidos de uma vez).
     */
    private suspend fun fillMissingAvatars() {
        val missing = Storage.subscriptions.filter { it.avatar == null }.take(20)
        val limit = Semaphore(PARALLEL)
        missing.map { ch ->
            scope.async {
                limit.withPermit {
                    runCatching { YouTubeRepo.channel(ch.url) }.getOrNull()
                }
            }
        }.awaitAll().filterNotNull().forEach { Storage.updateChannel(it) }
    }
}
