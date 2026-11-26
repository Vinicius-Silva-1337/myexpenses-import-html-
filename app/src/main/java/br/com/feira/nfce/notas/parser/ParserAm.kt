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

    fun parseHtml(html: String, expectedAccessKey: String? = null): NotaFiscal {
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
        val pageKey = keyFromSelector ?: keyPattern.find(keyText)?.groupValues?.get(1)
        val expectedKey = expectedAccessKey?.let(Chave::digitos)?.takeIf { it.length == 44 }
        if (expectedAccessKey != null && expectedKey == null) {
            throw ParserNfceException("chave_url_invalida", "A chave informada pela consulta é inválida.")
        }
        if (pageKey != null && expectedKey != null && pageKey != expectedKey) {
            throw ParserNfceException("chave_divergente", "A chave exibida no HTML não corresponde à chave do QR Code.")
        }
        val key = pageKey ?: expectedKey
            ?: throw ParserNfceException("chave_ausente", "A chave de acesso não foi encontrada.")
        val keyParts = Chave.decompor(key)
        val infosText = document.selectFirst("#infos")?.text().orEmpty()
        val emission = emissionPattern.find(infosText)?.groupValues?.get(1)?.let { raw ->
            runCatching {
                LocalDateTime.parse(raw, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
                    .atOffset(ZoneOffset.ofHours(-4))
            }.getOrNull()
        }
        val count = findSummaryCount(totalRegion)
            ?: Regex("(?i)(\\d+)\\s+item(?:s)?").find(infosText)?.groupValues?.get(1)?.toIntOrNull()
        val sum = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.valorTotal) }
        val totalProducts = summaryAmount(totalRegion) { label ->
            label.contains("valor total dos produtos") || label.contains("total dos produtos") || label.contains("valor produtos")
        }
        val discounts = sumSummaryAmounts(totalRegion) { it.contains("desconto") }
        val additions = sumSummaryAmounts(totalRegion) {
            it.contains("acréscimo") || it.contains("acrescimo") || it.contains("frete") ||
                it.contains("seguro") || it.contains("outras despesas")
        }
        val paid = summaryAmount(totalRegion) { it.contains("valor pago") || it.contains("valor efetivamente pago") }
        val change = summaryAmount(totalRegion) { it.contains("troco") }
        val paymentRows = totalRegion.select("label.tx").mapNotNull { label ->
            val form = label.nfceText().takeIf { it.isNotBlank() && !it.contains("troco", true) && !it.contains("valor pago", true) }
                ?: return@mapNotNull null
            val valueEl = label.parent()?.selectFirst("span.totalNumb")
                ?: label.nextElementSibling()?.takeIf { it.`is`("span.totalNumb") }
                ?: label.parent()?.nextElementSibling()?.selectFirst("span.totalNumb")
            valueEl?.let { Pagamento(form, runCatching { Numeros.brasileiro(it.text()) }.getOrNull() ?: return@mapNotNull null) }
        }.distinct()
        val invalidSummaryFields = invalidSummaryAmounts(totalRegion)
        val invalidPaymentForms = invalidPaymentAmounts(totalRegion)
        val inconsistentItemTotals = items.mapIndexedNotNull { index, item ->
            val computedTotal = item.quantidade.multiply(item.valorUnitario)
            (index + 1).takeIf { computedTotal.subtract(item.valorTotal).abs() > BigDecimal("0.01") }
        }

        val expectedProducts = totalProducts ?: sum
        val adjustedExpectedTotal = expectedProducts
            .subtract(discounts ?: BigDecimal.ZERO)
            .add(additions ?: BigDecimal.ZERO)
        val totalDifference = adjustedExpectedTotal.subtract(total)
        val productsDifference = totalProducts?.let { sum.subtract(it) }
        val productTotalsMatch = productsDifference?.abs()?.let { it <= BigDecimal("0.01") }
        val totalMatches = (productTotalsMatch != false) && totalDifference.abs() <= BigDecimal("0.01")
        val requiredMissing = buildList {
            if (emission == null) add("Data de emissão")
            if (count == null) add("Quantidade total de itens")
        }
        val warnings = buildList {
            if (requiredMissing.isNotEmpty()) add("Campos obrigatórios ausentes: ${requiredMissing.joinToString()}.")
            if (count != null && count != items.size) add("O documento informa $count item(ns), mas ${items.size} produto(s) foram extraídos.")
            if (productTotalsMatch == false) add("A soma dos itens difere do total de produtos informado em R$ ${productsDifference.abs().toPlainString()}.")
            if (totalDifference.abs() > BigDecimal("0.01")) add("A soma dos produtos ajustada por descontos e acréscimos difere do valor a pagar em R$ ${totalDifference.abs().toPlainString()}.")
            if (inconsistentItemTotals.isNotEmpty()) add("Quantidade multiplicada pelo valor unitário diverge do total nos produtos: ${inconsistentItemTotals.joinToString()}.")
            if (invalidSummaryFields.isNotEmpty()) add("Valores financeiros inválidos: ${invalidSummaryFields.joinToString()}.")
            if (invalidPaymentForms.isNotEmpty()) add("Valor inválido no pagamento: ${invalidPaymentForms.joinToString()}.")
            if (!Chave.validar(key)) add("A chave de acesso não passou na validação do dígito verificador.")
            if (paid != null && change != null && paid.subtract(change).subtract(total).abs() > BigDecimal("0.01")) {
                add("Valor pago menos troco difere do valor a pagar.")
            }
        }

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
                somaItensConfereComTotal = totalMatches,
                somaItens = sum,
                qtdItensConfere = count?.let { it == items.size },
                chaveValida = Chave.validar(key),
                somaItensConfereComProdutos = productTotalsMatch,
                diferencaItensTotal = totalDifference,
                camposObrigatoriosAusentes = requiredMissing,
                avisos = warnings
            ),
            totalProdutos = totalProducts,
            descontos = discounts,
            acrescimos = additions,
            valorPago = paid,
            troco = change,
            quantidadeItensInformada = count
        )
    }

    private fun findSummaryCount(region: Element): Int? {
        val flatText = region.nfceText()
        val value = Regex("(?i)(?:qtd\\.?\\s*total\\s*de\\s*itens?|quantidade\\s*total\\s*de\\s*itens?)\\s*:?\\s*(\\d+)")
            .find(flatText)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (value != null) return value

        val label = region.select("label").firstOrNull {
            val text = it.nfceText().lowercase()
            (text.contains("qtd") && text.contains("item")) || text.contains("quantidade total de itens")
        } ?: return null
        val valueElement = label.nextElementSibling()?.takeIf { it.`is`("span.totalNumb") }
            ?: label.parent()?.selectFirst("span.totalNumb")
            ?: return null
        return valueElement.text().trim().toIntOrNull()
    }

    private fun summaryAmount(region: Element, matches: (String) -> Boolean): BigDecimal? =
        region.select("label").firstOrNull { matches(it.nfceText().lowercase()) }?.let(::amountForLabel)

    private fun sumSummaryAmounts(region: Element, matches: (String) -> Boolean): BigDecimal? {
        val values = region.select("label").filter { matches(it.nfceText().lowercase()) }.mapNotNull(::amountForLabel)
        return values.takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO, BigDecimal::add)
    }

    private fun invalidSummaryAmounts(region: Element): List<String> = region.select("label")
        .filter { label ->
            val text = label.nfceText().lowercase()
            text.contains("valor total dos produtos") || text.contains("total dos produtos") || text.contains("valor produtos") ||
                text.contains("desconto") || text.contains("acréscimo") || text.contains("acrescimo") ||
                text.contains("frete") || text.contains("seguro") || text.contains("outras despesas") ||
                text.contains("valor pago") || text.contains("valor efetivamente pago") || text.contains("troco")
        }
        .mapNotNull { label ->
            val value = summaryValueElement(label) ?: return@mapNotNull null
            if (runCatching { Numeros.brasileiro(value.text()) }.isSuccess) return@mapNotNull null
            if (label.nfceText().contains("troco", true) && value.text().trim().equals("NaN", true)) return@mapNotNull null
            label.nfceText().substringBefore(':').trim().ifBlank { "campo financeiro" }
        }
        .distinct()

    private fun invalidPaymentAmounts(region: Element): List<String> = region.select("label.tx")
        .filterNot { it.nfceText().contains("troco", true) || it.nfceText().contains("valor pago", true) }
        .mapNotNull { label ->
            val value = summaryValueElement(label) ?: return@mapNotNull null
            if (runCatching { Numeros.brasileiro(value.text()) }.isSuccess) null else label.nfceText()
        }
        .distinct()

    private fun summaryValueElement(label: Element): Element? =
        label.parent()?.selectFirst("span.totalNumb")
            ?: label.nextElementSibling()?.takeIf { it.`is`("span.totalNumb") }
            ?: label.parent()?.nextElementSibling()?.selectFirst("span.totalNumb")

    private fun amountForLabel(label: Element): BigDecimal? {
        val value = summaryValueElement(label) ?: return null
        return runCatching { Numeros.brasileiro(value.text()) }.getOrNull()
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
