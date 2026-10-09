package pt.meutube

import android.app.Application
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import pt.meutube.data.FeedManager
import pt.meutube.data.OkHttpDownloader
import pt.meutube.data.Storage

/**
 * Corre uma vez quando a app arranca, antes de qualquer ecrã.
 * Aqui "ligamos" o extractor ao nosso cliente HTTP e dizemos-lhe
 * que queremos resultados em português de Portugal.
 */
class MeuTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(
            OkHttpDownloader,
            Localization("pt", "PT"),
            ContentCountry("PT"),
        )
        Storage.init(this)
        FeedManager.loadCache() // mostra logo o último feed guardado
    }
}
