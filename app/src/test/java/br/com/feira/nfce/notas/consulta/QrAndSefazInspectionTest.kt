package br.com.feira.nfce.notas.consulta

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class QrAndSefazInspectionTest {
    @Test fun acceptsAmNfceQueryOverHttpsOrHttpOnTheOfficialHost() {
        val key = validAmNfceKey()
        val validated = NfceQrUrlValidator.validate("https://${NfceQrUrlValidator.AUTHORIZED_HOST}/nfceweb/consultarNFCe.jsp?p=$key|2|1")
        val legacyQr = NfceQrUrlValidator.validate("http://${NfceQrUrlValidator.AUTHORIZED_HOST}/nfceweb/consultarNFCe.jsp?p=$key|2|1")

        assertEquals(key, validated.accessKey)
        assertFalse(validated.homologation)
        assertFalse(validated.toString().contains(key))
        assertTrue(validated.usesHttps)
        assertFalse(legacyQr.usesHttps)
    }

    @Test fun connectionFailureIsRaisedForGenericUiErrorHandling() = runBlocking {
        val client = SefazNfceClient(
            OkHttpClient.Builder().addInterceptor { throw UnknownHostException("simulated offline") }.build()
        )
        val qr = NfceQrUrlValidator.validate(
            "https://${NfceQrUrlValidator.AUTHORIZED_HOST}/nfceweb/consultarNFCe.jsp?p=${validAmNfceKey()}"
        )

        assertThrows(UnknownHostException::class.java) { runBlocking { client.fetch(qr) } }
        Unit
    }

    @Test fun rejectsHttpWrongHostWrongPathAndInvalidAccessKeyBeforeNetwork() {
        val key = validAmNfceKey()
        val httpWrongHost = "http://example.invalid/nfceweb/consultarNFCe.jsp?p=$key"
        val wrongHost = "https://example.invalid/nfceweb/consultarNFCe.jsp?p=$key"
        val wrongPath = "https://${NfceQrUrlValidator.AUTHORIZED_HOST}/outro?p=$key"
        val unsafeScheme = "file://${NfceQrUrlValidator.AUTHORIZED_HOST}/nfceweb/consultarNFCe.jsp?p=$key"
        val invalidKey = "${key.dropLast(1)}${if (key.last() == '0') '1' else '0'}"

        assertThrows(NfceQrUrlException::class.java) { NfceQrUrlValidator.validate(httpWrongHost) }
        assertThrows(NfceQrUrlException::class.java) { NfceQrUrlValidator.validate(wrongHost) }
        assertThrows(NfceQrUrlException::class.java) { NfceQrUrlValidator.validate(wrongPath) }
        assertThrows(NfceQrUrlException::class.java) { NfceQrUrlValidator.validate(unsafeScheme) }
        assertThrows(NfceQrUrlException::class.java) {
            NfceQrUrlValidator.validate("https://${NfceQrUrlValidator.AUTHORIZED_HOST}/nfceweb/consultarNFCe.jsp?p=$invalidKey")
        }
        assertThrows(NfceQrUrlException::class.java) { NfceQrUrlValidator.validate("https://example.invalid/not-a-qr") }
    }

    @Test fun recognizesExpectedSefazAmHtmlAndParserSelectors() {
        val inspection = SefazHtmlInspector.inspect(expectedNfceHtml(), "text/html; charset=ISO-8859-1")

        assertTrue(inspection.isHtml)
        assertTrue(inspection.hasNfceStructure)
        assertEquals(2, inspection.productCount)
        assertEquals(2, inspection.completeProductCount)
        assertTrue(inspection.parserAccepted)
    }

    @Test fun rejectsHttp200ErrorPageAndStructurallyDifferentProductRows() {
        val errorPage = SefazHtmlInspector.inspect("<html><body>Consulta indisponível</body></html>", "text/html")
        val changedLayout = SefazHtmlInspector.inspect(changedProductLayout(), "text/html")
        val plainText = SefazHtmlInspector.inspect("Consulta indisponível", "text/plain")

        assertTrue(errorPage.isHtml)
        assertFalse(errorPage.hasNfceStructure)
        assertFalse(errorPage.parserAccepted)
        assertTrue(changedLayout.isHtml)
        assertFalse(changedLayout.hasNfceStructure)
        assertEquals(0, changedLayout.productCount)
        assertFalse(plainText.isHtml)
    }

    @Test fun reportsHttpErrorsRedirectsAndEmptyBodiesWithoutFollowingRedirects() {
        val client = SefazNfceClient()
        val htmlError = response(503, "<html><body>Indisponível</body></html>", "text/html")
        val redirect = response(302, "", "text/html", "https://evil.invalid/")
        val empty = response(200, "", "text/html")

        val errorResult = client.inspectResponse(htmlError)
        assertEquals(503, errorResult.statusCode)
        assertTrue(errorResult.htmlInspection?.isHtml == true)
        assertFalse(errorResult.htmlInspection?.hasNfceStructure == true)
        val redirectResult = client.inspectResponse(redirect)
        assertTrue(redirectResult.redirected)
        assertEquals(null, redirectResult.htmlInspection)
        val emptyResult = client.inspectResponse(empty)
        assertEquals(0, emptyResult.responseBytes)
        assertEquals(null, emptyResult.htmlInspection)
    }

    private fun response(code: Int, body: String, contentType: String, location: String? = null) = Response.Builder()
        .request(Request.Builder().url("https://example.invalid/").build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("test")
        .apply { header("Content-Type", contentType); if (location != null) header("Location", location) }
        .body(body.toResponseBody(contentType.toMediaType()))
        .build()

    private fun expectedNfceHtml(): String {
        val key = validAmNfceKey()
        fun item(description: String, code: String) = """
            <tr><td><span class="txtTit">$description</span><span class="RCod">Código: $code</span>
            <span class="Rqtd">Qtde.: 1</span><span class="RUN">UN: UN</span>
            <span class="RvlUnit">Vl. Unit.: 5,00</span><span class="valor">5,00</span></td></tr>
        """.trimIndent()
        return """
            <!doctype html><html><body>
            <div id="conteudo"><div class="txtCenter"><div id="u20">Loja de teste</div><div class="text">CNPJ: 12345678000190</div></div></div>
            <table id="tabResult">${item("ITEM TESTE A", "001")}${item("ITEM TESTE B", "002")}</table>
            <div id="totalNota"><span class="totalNumb txtMax">10,00</span></div>
            <div id="infos">Emissão: 20/11/2026 18:56:11 · 2 itens</div>
            <span class="chave">$key</span></body></html>
        """.trimIndent()
    }

    private fun changedProductLayout() = """
        <!doctype html><html><body><table><tr><td><span class="struct-class-1"><strong>PRODUTO_FICTICIO</strong><br/>
        <span>Quantidade</span><span>Unidade</span><span>Valor unitário</span><span class="valor">12,34</span>
        </span></td><td>12,34</td></tr></table></body></html>
    """.trimIndent()

    private fun validAmNfceKey(): String {
        val base = "13" + "2611" + "12345678000190" + "65" + "001" + "000000001" + "1" + "12345678"
        var weight = 2
        val sum = base.reversed().sumOf { digit -> (digit.digitToInt() * weight).also { weight = if (weight == 9) 2 else weight + 1 } }
        val remainder = sum % 11
        return base + if (remainder < 2) 0 else 11 - remainder
    }
}
