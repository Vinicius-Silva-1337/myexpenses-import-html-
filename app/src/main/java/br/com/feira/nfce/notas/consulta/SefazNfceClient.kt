package br.com.feira.nfce.notas.consulta

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

data class SefazHttpInspection(
    val statusCode: Int,
    val contentType: String?,
    val responseBytes: Int,
    val redirected: Boolean,
    val htmlInspection: NfceHtmlInspection?,
    val redirectBlocked: Boolean = false,
    val finalUsesHttps: Boolean? = null
)

class SefazNfceClient(
    private val httpClient: OkHttpClient = defaultClient()
) {
    suspend fun fetch(qr: ValidatedNfceQr): SefazHttpInspection = withContext(Dispatchers.IO) {
        fetchFollowingAuthorizedRedirects(qr)
    }

    private fun fetchFollowingAuthorizedRedirects(original: ValidatedNfceQr): SefazHttpInspection {
        var currentUrl = original.requestUrl
        var currentUsesHttps = original.usesHttps
        var redirected = false
        repeat(MAX_REDIRECTS + 1) { followed ->
            val request = Request.Builder().url(currentUrl).get().build()
            val response = httpClient.newCall(request).execute()
            if (response.code !in 300..399) {
                return response.use { inspectResponse(it, original.accessKey) }.copy(redirected = redirected, finalUsesHttps = currentUsesHttps)
            }
            redirected = true
            val code = response.code
            val contentType = response.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase()
            val destination = response.header("Location")?.let { response.request.url.resolve(it)?.toString() }
            response.close()
            val target = destination?.let { runCatching { NfceQrUrlValidator.validate(it) }.getOrNull() }
            val safe = target != null &&
                target.accessKey == original.accessKey &&
                target.homologation == original.homologation &&
                !(original.usesHttps && !target.usesHttps)
            if (!safe || followed == MAX_REDIRECTS) {
                return SefazHttpInspection(code, contentType, 0, redirected, null, redirectBlocked = true)
            }
            currentUrl = target.requestUrl
            currentUsesHttps = target.usesHttps
        }
        error("Fluxo de redirecionamento inválido")
    }

    internal fun inspectResponse(response: Response, expectedAccessKey: String? = null): SefazHttpInspection {
        val contentType = response.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase()
        val redirected = response.code in 300..399
        val body = response.body
        if (body.contentLength() == 0L) return SefazHttpInspection(response.code, contentType, 0, redirected, null)

        val bytes = readAtMost(body.byteStream(), MAX_RESPONSE_BYTES + 1)
        if (bytes.size > MAX_RESPONSE_BYTES) throw IOException("A resposta da consulta excede o limite permitido.")
        if (bytes.isEmpty() || redirected) {
            return SefazHttpInspection(response.code, contentType, bytes.size, redirected, null)
        }
        val charset: Charset = runCatching { body.contentType()?.charset(Charsets.UTF_8) }.getOrNull() ?: Charsets.UTF_8
        val html = bytes.toString(charset)
        return SefazHttpInspection(response.code, contentType, bytes.size, false, SefazHtmlInspector.inspect(html, contentType, expectedAccessKey))
    }

    private fun readAtMost(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
        val buffer = ByteArray(8 * 1024)
        input.use { stream ->
            while (output.size() < maxBytes) {
                val read = stream.read(buffer, 0, minOf(buffer.size, maxBytes - output.size()))
                if (read < 0) break
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    companion object {
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        private const val MAX_REDIRECTS = 3

        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
