package pt.meutube.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer

/**
 * O player PRINCIPAL da app (vídeos normais). Existe só UM, partilhado:
 *  - o ecrã do vídeo (PlayerScreen) mostra a imagem dele;
 *  - o PlaybackService "embrulha-o" numa sessão de media, o que faz aparecer
 *    a notificação com ⏯ e mantém o som a tocar com a app minimizada.
 *
 * Antes, cada ecrã de vídeo criava o seu player e destruía-o ao sair.
 * Agora o player vive fora dos ecrãs, por isso sobrevive à app ir para segundo plano.
 */
object AppPlayer {
    private var player: ExoPlayer? = null

    /**
     * Quem está a usar o player neste momento (cada ecrã de vídeo tem um "token").
     * Serve para um ecrã antigo, ao fechar, não parar o vídeo que um ecrã NOVO
     * acabou de começar (acontece ao tocares num vídeo sugerido).
     */
    var owner: Any? = null

    fun get(context: Context): ExoPlayer =
        player ?: ExoPlayer.Builder(context.applicationContext)
            // Pede o "foco de áudio": se receberes uma chamada ou outra app tocar
            // música, o nosso player pausa; quando acabar, retoma.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Pausa sozinho se desligares os auscultadores / o Bluetooth
            .setHandleAudioBecomingNoisy(true)
            // Mantém CPU e Wi-Fi acordados enquanto toca com o ecrã desligado
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also { player = it }

    /**
     * Modo "só som": com a app minimizada não há ecrã para mostrar o vídeo,
     * por isso desligamos a faixa de vídeo. O ExoPlayer deixa de a descarregar,
     * o que poupa muitos dados móveis e bateria. Ao voltar à app, liga-se outra vez.
     */
    fun setVideoEnabled(enabled: Boolean) {
        val p = player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled)
            .build()
    }

    fun release() {
        player?.release()
        player = null
        owner = null
    }
}
