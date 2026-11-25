package br.com.feira.nfce.notas.parser

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.math.BigDecimal

class ParserAmTest {
    @Test fun parseOneItemKeepsOriginalDescriptionAndReportedTotal() {
        val note = ParserAm.parseHtml(html(listOf(item("Pão &amp; Café Á", "0007", "2,500", "KG", "4,1450", "10,37")), total = "10,38"))
        assertEquals("Pão & Café Á", note.itens.single().descricao)
        assertEquals("0007", note.itens.single().codigo)
        assertEquals(BigDecimal("4.1450"), note.itens.single().valorUnitario)
        assertEquals(BigDecimal("10.37"), note.itens.single().valorTotal)
        assertEquals(BigDecimal("10.38"), note.total)
        assertTrue(note.validacao.somaItensConfereComTotal)
    }

    @Test fun parsesUnitsAndFractionalQuantitiesWithoutRecalculatingTotals() {
        val items = listOf(item("PRODUTO A", "1", "0,250", "KG", "10,000", "2,49"), item("PRODUTO B", "2", "3", "UN", "2,00", "5,99"))
        val parsed = ParserAm.parseHtml(html(items, "8,48"))
        assertEquals(listOf("KG", "UN"), parsed.itens.map { it.unidade })
        assertEquals(BigDecimal("0.250"), parsed.itens[0].quantidade)
        assertEquals(BigDecimal("5.99"), parsed.itens[1].valorTotal)
    }

    @Test fun handlesLargeValuesAndArbitraryDecimalScale() {
        val parsed = ParserAm.parseHtml(html(listOf(item("ITEM", "X", "1,234000", "CX", "2.899,9000", "2.899,90")), "2.899,90"))
        assertEquals(BigDecimal("1.234000"), parsed.itens.single().quantidade)
        assertEquals(BigDecimal("2899.9000"), parsed.itens.single().valorUnitario)
        assertEquals(BigDecimal("2899.90"), parsed.total)
    }

    @Test fun supportsThreeHundredRowsDynamically() {
        val items = (1..300).map { item("PRODUTO $it", it.toString(), "1", "UN", "1,00", "1,00") }
        val parsed = ParserAm.parseHtml(html(items, "300,00"))
        assertEquals(300, parsed.itens.size)
        assertEquals("PRODUTO 300", parsed.itens.last().descricao)
    }

    @Test fun rowWithMissingRequiredFieldFailsWithItsRowNumber() {
        val bad = "<tr><td><span class='txtTit'>Broken</span><span class='Rqtd'>Qtde.: 1</span></td></tr>"
        val error = try { ParserAm.parseHtml(html(listOf(item("OK", "1", "1", "UN", "1,00", "1,00")) + bad, "1,00")); fail("expected parse error"); null }
        catch (e: ParserNfceException) { e }
        assertEquals("linha_invalida", error?.codigo)
        assertEquals(2, error?.linha)
    }

    @Test fun validatesAccessKeyAndCounts() {
        val key = validKey()
        val parsed = ParserAm.parseHtml(html(listOf(item("UM", "1", "1", "UN", "1,00", "1,00")), "1,00", key, "1 item"))
        assertTrue(parsed.validacao.chaveValida)
        assertEquals(true, parsed.validacao.qtdItensConfere)
        assertFalse(Chave.validar(key.dropLast(1) + if (key.last() == '0') "1" else "0"))
    }

    @Test fun decoderUsesStrictUtf8ThenWindows1252() {
        val html = "<html>café €</html>"
        assertEquals(html, Decodificador.decodificar(html.toByteArray(StandardCharsets.UTF_8)))
        assertEquals("<html>café €</html>", Decodificador.decodificar("<html>café €</html>".toByteArray(Charset.forName("windows-1252"))))
    }

    @Test fun paymentsAreReadAndMismatchedTotalsRemainAvailableAsWarning() {
        val document = html(listOf(item("ITEM", "1", "1", "UN", "1,00", "1,00")), "9,00")
            .replace("<div id=\"totalNota\"><span class=\"totalNumb txtMax\">9,00</span></div>", "<div id=\"totalNota\"><label class=\"tx\">PIX</label><span class=\"totalNumb\">9,00</span><span class=\"totalNumb txtMax\">9,00</span></div>")
        val parsed = ParserAm.parseHtml(document)
        assertEquals(BigDecimal("9.00"), parsed.total)
        assertEquals("PIX", parsed.pagamentos.single().forma)
        assertEquals(BigDecimal("9.00"), parsed.pagamentos.single().valor)
        assertFalse(parsed.validacao.somaItensConfereComTotal)
    }

    @Test fun mhtmlExtractsQuotedPrintableAndBase64Html() {
        val qp = "MIME-Version: 1.0\r\nContent-Type: multipart/related; boundary=\"x\"\r\n\r\n--x\r\nContent-Type: text/html; charset=utf-8\r\nContent-Transfer-Encoding: quoted-printable\r\n\r\n<html>caf=C3=A9</html>\r\n--x--"
        assertEquals("<html>café</html>", ParserMhtml.extrairHtml(qp.toByteArray()))
        val base64 = java.util.Base64.getEncoder().encodeToString("<html>ok</html>".toByteArray())
        val encoded = "MIME-Version: 1.0\r\nContent-Type: multipart/related; boundary=x\r\n\r\n--x\r\nContent-Type: text/html; charset=utf-8\r\nContent-Transfer-Encoding: base64\r\n\r\n$base64\r\n--x--"
        assertEquals("<html>ok</html>", ParserMhtml.extrairHtml(encoded.toByteArray()))
    }

    private fun item(description: String, code: String, quantity: String, unit: String, price: String, total: String) =
        "<tr><td><span class='txtTit'>$description</span><span class='RCod'>Código: $code</span><span class='Rqtd'>Qtde.: $quantity</span><span class='RUN'>UN: $unit</span><span class='RvlUnit'>Vl. Unit.: $price</span><span class='valor'>$total</span></td></tr>"

    private fun html(items: List<String>, total: String, key: String = validKey(), countText: String = "${items.size} itens") = """
        <html><body>
        <div class="txtTopo">Mercado Exemplo Ltda</div>
        <div class="text">CNPJ: 12.345.678/0001-90</div>
        <table id="tabResult">${items.joinToString("")}</table>
        <div id="totalNota"><span class="totalNumb txtMax">$total</span></div>
        <div id="infos">Emissão: 20/11/2026 18:56:11 · $countText</div>
        <span class="chave">$key</span>
        </body></html>
    """.trimIndent()

    private fun validKey(): String {
        val base = "13" + "2611" + "12345678000190" + "65" + "001" + "000000001" + "1" + "12345678"
        var weight = 2
        val sum = base.reversed().sumOf { digit -> (digit.digitToInt() * weight).also { weight = if (weight == 9) 2 else weight + 1 } }
        val remainder = sum % 11
        val dv = if (remainder < 2) 0 else 11 - remainder
        return base + dv
    }
}
