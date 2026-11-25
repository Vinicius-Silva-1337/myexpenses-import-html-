package br.com.feira.nfce.notas.parser

import br.com.feira.nfce.notas.model.ItemNota
import br.com.feira.nfce.notas.model.NotaFiscal
import br.com.feira.nfce.notas.model.Pagamento
import br.com.feira.nfce.notas.model.Validacao
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ParserNfceException(val codigo: String, message: String, val linha: Int? = null) : Exception(message)

object ParserAm {
    private val cnpjPattern = Regex("CNPJ\\s*:?\\s*([0-9.\\-/]+)", RegexOption.IGNORE_CASE)
    private val keyPattern = Regex("(?<!\\d)(\\d{44})(?!\\d)")
    private val emissionPattern = Regex("Emiss[aã]o\\s*:?\\s*(\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2}:\\d{2})", RegexOption.IGNORE_CASE)

    fun parse(bytes: ByteArray): NotaFiscal = parseHtml(Decodificador.decodificar(bytes))

    fun parseHtml(html: String): NotaFiscal {
        if (html.isBlank()) throw ParserNfceException("html_sem_itens", "O arquivo está vazio.")
        val document = Jsoup.parse(html)
        val rows = document.select("table#tabResult tr")
        if (rows.isEmpty()) throw ParserNfceException("layout_desconhecido", "A tabela de produtos não foi encontrada.")
        val itemRows = rows.withIndex().filter { (_, row) -> row.selectFirst("span.txtTit") != null }
        if (itemRows.isEmpty()) throw ParserNfceException("html_sem_itens", "Nenhum produto foi encontrado.")

        val items = itemRows.map { (index, row) -> parseItem(row, index + 1) }
        val totalRegion = document.selectFirst("div#totalNota")
            ?: throw ParserNfceException("layout_desconhecido", "O total da nota não foi encontrado.")
        val totalEl = totalRegion.selectFirst("span.totalNumb.txtMax")
            ?: totalRegion.selectFirst("span.totalNumb")
            ?: throw ParserNfceException("layout_desconhecido", "O valor total da nota não foi encontrado.")
        val total = number(totalEl.text(), "layout_desconhecido")

        val emitter = document.selectFirst("div.txtTopo")?.nfceText()
            ?: document.selectFirst("#conteudo .txtCenter #u20")?.nfceText()
            ?: throw ParserNfceException("layout_desconhecido", "O emitente não foi encontrado.")
        val cnpjBlock = document.select("div.text").firstOrNull { it.text().contains("CNPJ", true) }
            ?: document.select("#conteudo .txtCenter .text").firstOrNull { it.text().contains("CNPJ", true) }
            ?: throw ParserNfceException("layout_desconhecido", "O CNPJ do emitente não foi encontrado.")
        val cnpj = cnpjPattern.find(cnpjBlock.text())?.groupValues?.get(1)?.filter(Char::isDigit)
            ?: throw ParserNfceException("layout_desconhecido", "O CNPJ do emitente é inválido.")
        if (cnpj.length != 14) throw ParserNfceException("layout_desconhecido", "O CNPJ do emitente é inválido.")

        val keyText = document.selectFirst("span.chave")?.text().orEmpty()
        val keyFromSelector = Chave.digitos(keyText).takeIf { it.length == 44 }
        val key = keyFromSelector ?: keyPattern.find(keyText)?.groupValues?.get(1)
            ?: throw ParserNfceException("chave_ausente", "A chave de acesso não foi encontrada.")
        val keyParts = Chave.decompor(key)
        val infosText = document.selectFirst("#infos")?.text().orEmpty()
        val emission = emissionPattern.find(infosText)?.groupValues?.get(1)?.let { raw ->
            runCatching {
                LocalDateTime.parse(raw, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
                    .atOffset(ZoneOffset.ofHours(-4))
            }.getOrNull()
        }
        val count = Regex("(?i)(\\d+)\\s+item(?:s)?").find(infosText)?.groupValues?.get(1)?.toIntOrNull()
        val sum = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.valorTotal) }
        val paymentRows = totalRegion.select("label.tx").mapNotNull { label ->
            val form = label.nfceText().takeIf { it.isNotBlank() && !it.contains("troco", true) && !it.contains("valor pago", true) }
                ?: return@mapNotNull null
            val valueEl = label.parent()?.selectFirst("span.totalNumb")
                ?: label.nextElementSibling()?.takeIf { it.`is`("span.totalNumb") }
                ?: label.parent()?.nextElementSibling()?.selectFirst("span.totalNumb")
            valueEl?.let { Pagamento(form, runCatching { Numeros.brasileiro(it.text()) }.getOrNull() ?: return@mapNotNull null) }
        }.distinct()

        return NotaFiscal(
            chave = key,
            cnpj = cnpj,
            emitente = emitter,
            numero = keyParts?.get("numero"),
            serie = keyParts?.get("serie"),
            dataEmissao = emission,
            total = total,
            pagamentos = paymentRows,
            itens = items,
            validacao = Validacao(
                somaItensConfereComTotal = sum.subtract(total).abs() <= BigDecimal("0.01"),
                somaItens = sum,
                qtdItensConfere = count?.let { it == items.size },
                chaveValida = Chave.validar(key)
            )
        )
    }

    private fun parseItem(row: Element, rowNumber: Int): ItemNota = try {
        val description = row.selectFirst("span.txtTit")?.nfceText()
            ?: throw IllegalArgumentException("Descrição ausente")
        val quantity = row.selectFirst("span.Rqtd")?.text()?.removePrefixIgnoreCase("Qtde.:")
            ?: throw IllegalArgumentException("Quantidade ausente")
        val unit = row.selectFirst("span.RUN")?.text()?.removePrefixIgnoreCase("UN:")
            ?: throw IllegalArgumentException("Unidade ausente")
        val unitPrice = row.selectFirst("span.RvlUnit")?.text()?.removePrefixIgnoreCase("Vl. Unit.:")
            ?: throw IllegalArgumentException("Valor unitário ausente")
        val itemTotal = row.selectFirst("span.valor")?.text()
            ?: throw IllegalArgumentException("Valor total ausente")
        val code = row.selectFirst("span.RCod")?.text()?.removePrefixIgnoreCase("Código:")?.trim()?.ifBlank { null }
        ItemNota(
            descricao = description,
            codigo = code,
            quantidade = Numeros.brasileiro(quantity),
            unidade = unit.trim(),
            valorUnitario = Numeros.brasileiro(unitPrice),
            valorTotal = Numeros.brasileiro(itemTotal)
        )
    } catch (error: Exception) {
        throw ParserNfceException("linha_invalida", "Não foi possível interpretar a linha de produto $rowNumber.", rowNumber)
    }

    private fun number(text: String, code: String): BigDecimal = try {
        Numeros.brasileiro(text)
    } catch (_: Exception) {
        throw ParserNfceException(code, "Um valor numérico da nota é inválido.")
    }

    private fun Element.nfceText(): String = text().replace('\u00a0', ' ').trim().replace(Regex("\\s+"), " ")
    private fun String.removePrefixIgnoreCase(prefix: String): String =
        trim().replace(Regex("^${Regex.escape(prefix)}\\s*", RegexOption.IGNORE_CASE), "")
}
