package br.com.feira.nfce.notas.consulta

import br.com.feira.nfce.notas.parser.Chave
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class NfceQrUrlException(message: String) : IllegalArgumentException(message)

/** Holds a validated request URL without including its QR payload in toString(). */
class ValidatedNfceQr internal constructor(
    internal val requestUrl: String,
    val accessKey: String,
    val homologation: Boolean,
    val usesHttps: Boolean
)

object NfceQrUrlValidator {
    const val AUTHORIZED_HOST = "sistemas.sefaz.am.gov.br"
    private const val PRODUCTION_PATH = "/nfceweb/consultarNFCe.jsp"
    private const val HOMOLOGATION_PATH = "/nfceweb-hom/consultarNFCe.jsp"

    fun validate(raw: String): ValidatedNfceQr {
        val uri = try {
            // NFC-e QR payloads commonly contain literal pipes in the query value.
            URI(raw.trim().replace("|", "%7C"))
        } catch (_: Exception) {
            throw NfceQrUrlException("O QR Code não contém uma URL válida.")
        }
        val usesHttps = uri.scheme.equals("https", ignoreCase = true)
        if (!usesHttps && !uri.scheme.equals("http", ignoreCase = true)) {
            throw NfceQrUrlException("A URL do QR Code precisa usar HTTP ou HTTPS.")
        }
        if (!(uri.host ?: "").equals(AUTHORIZED_HOST, ignoreCase = true) || uri.userInfo != null || uri.fragment != null) {
            throw NfceQrUrlException("O domínio do QR Code não é autorizado para NFC-e do Amazonas.")
        }
        if (uri.port != -1 && uri.port != 443) {
            throw NfceQrUrlException("A porta da URL do QR Code não é permitida.")
        }
        val homologation = uri.path == HOMOLOGATION_PATH
        if (uri.path != PRODUCTION_PATH && !homologation) {
            throw NfceQrUrlException("O endereço não corresponde à consulta de NFC-e do Amazonas.")
        }

        val parameters = uri.rawQuery.orEmpty().split('&').mapNotNull { parameter ->
            if (parameter.isEmpty()) return@mapNotNull null
            val parts = parameter.split('=', limit = 2)
            decode(parts[0]) to decode(parts.getOrElse(1) { "" })
        }
        val pValues = parameters.filter { it.first == "p" }.map { it.second }
        if (pValues.size != 1 || pValues.single().isBlank()) {
            throw NfceQrUrlException("O QR Code não contém um parâmetro de consulta válido.")
        }
        val accessKey = pValues.single().substringBefore('|')
        if (!accessKey.matches(Regex("[0-9]{44}")) || accessKey.take(2) != "13" || accessKey.substring(20, 22) != "65" || !Chave.validar(accessKey)) {
            throw NfceQrUrlException("O QR Code não contém uma chave válida de NFC-e do Amazonas.")
        }
        return ValidatedNfceQr(uri.toASCIIString(), accessKey, homologation, usesHttps)
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    } catch (_: Exception) {
        throw NfceQrUrlException("Os parâmetros do QR Code são inválidos.")
    }
}
