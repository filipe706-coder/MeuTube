package pt.meutube.data

import okhttp3.OkHttpClient
import okhttp3.Request.Builder
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/**
 * O extractor não sabe fazer pedidos à internet sozinho: pede-nos a nós.
 * Sempre que precisa de falar com o YouTube, chama execute() com um
 * Request (URL, método, cabeçalhos, corpo) e nós devolvemos a Response.
 *
 * É só uma "ponte" entre o extractor e o OkHttp.
 */
object OkHttpDownloader : Downloader() {

    // Fingimos ser um Firefox normal, como fazem o NewPipe e o FreeTube
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        // Corpo do pedido (só existe em POST, p.ex. nas chamadas à API interna)
        val body = request.dataToSend()?.toRequestBody()
            ?: if (request.httpMethod() == "POST") ByteArray(0).toRequestBody() else null

        val builder = Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
            .header("User-Agent", USER_AGENT)

        // Copiar os cabeçalhos que o extractor quer enviar
        for ((name, values) in request.headers()) {
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }

        client.newCall(builder.build()).execute().use { response ->
            // 429 = o YouTube acha que somos um robô e quer um captcha
            if (response.code == 429) {
                throw ReCaptchaException("O YouTube pediu verificação (captcha)", request.url())
            }
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string(),
                response.request.url.toString(),
            )
        }
    }
}
