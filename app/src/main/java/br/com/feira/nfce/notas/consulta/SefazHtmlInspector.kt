package br.com.feira.nfce.notas.consulta

import br.com.feira.nfce.notas.parser.ParserAm
import org.jsoup.Jsoup

data class NfceHtmlInspection(
    val isHtml: Boolean,
    val hasNfceStructure: Boolean,
    val productCount: Int,
    val completeProductCount: Int,
    val parserAccepted: Boolean
)

object SefazHtmlInspector {
    private val htmlStart = Regex("(?is)^\\s*(?:<!doctype\\s+html|<html\\b|<head\\b|<body\\b)")

    fun inspect(html: String, contentType: String?): NfceHtmlInspection {
        val mime = contentType.orEmpty().substringBefore(';').trim().lowercase()
        val isHtml = mime == "text/html" || mime == "application/xhtml+xml" || htmlStart.containsMatchIn(html)
        if (!isHtml || html.isBlank()) return NfceHtmlInspection(isHtml, false, 0, 0, false)

        val document = Jsoup.parse(html)
        val rows = document.select("table#tabResult tr").filter { it.selectFirst("span.txtTit") != null }
        val completeRows = rows.count { row ->
            row.selectFirst("span.Rqtd") != null &&
                row.selectFirst("span.RUN") != null &&
                row.selectFirst("span.RvlUnit") != null &&
                row.selectFirst("span.valor") != null
        }
        val emitter = document.selectFirst("div.txtTopo") ?: document.selectFirst("#conteudo .txtCenter #u20")
        val cnpj = document.select("div.text, #conteudo .txtCenter .text").any { it.text().contains("CNPJ", ignoreCase = true) }
        val hasNfceStructure = rows.isNotEmpty() &&
            completeRows == rows.size &&
            document.selectFirst("div#totalNota span.totalNumb") != null &&
            emitter != null && cnpj && document.selectFirst("#infos") != null
        val parserAccepted = runCatching { ParserAm.parseHtml(html) }.isSuccess
        return NfceHtmlInspection(isHtml, hasNfceStructure, rows.size, completeRows, parserAccepted)
    }
}
