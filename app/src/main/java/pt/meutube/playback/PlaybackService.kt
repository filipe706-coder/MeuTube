package pt.meutube.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import pt.meutube.MainActivity

/**
 * Serviço de reprodução em segundo plano.
 *
 * No Android, quando minimizas uma app, o sistema pode pará-la a qualquer
 * momento para poupar memória. A única forma "oficial" de continuar a tocar
 * é ter um serviço com uma notificação visível ("foreground service").
 *
 * O MediaSessionService do Media3 trata de quase tudo sozinho:
 *  - cria a notificação com título, miniatura e botões ⏮ ⏯ ⏭;
 *  - mostra os controlos no ecrã de bloqueio e nos auscultadores Bluetooth;
 *  - põe o serviço em "primeiro plano" enquanto está a tocar.
 * Nós só temos de lhe dar o player (MediaSession).
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // Tocar na notificação abre a app
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, AppPlayer.get(this))
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * Quando fechas a app na lista de apps recentes (deslizar para o lado):
     * se estiver a tocar, continua (podes parar na notificação);
     * se estiver em pausa, desliga tudo.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.release()
        session = null
        AppPlayer.release()
        super.onDestroy()
    }
}
