package br.com.feira.nfce.notas.model

import java.math.BigDecimal
import java.time.OffsetDateTime

data class NotaFiscal(
    val chave: String,
    val cnpj: String,
    val emitente: String,
    val numero: String?,
    val serie: String?,
    val dataEmissao: OffsetDateTime?,
    val total: BigDecimal,
    val pagamentos: List<Pagamento>,
    val itens: List<ItemNota>,
    val validacao: Validacao
)

data class ItemNota(
    val descricao: String,
    val codigo: String?,
    val quantidade: BigDecimal,
    val unidade: String,
    val valorUnitario: BigDecimal,
    val valorTotal: BigDecimal
)

data class Pagamento(val forma: String, val valor: BigDecimal)

data class Validacao(
    val somaItensConfereComTotal: Boolean,
    val somaItens: BigDecimal,
    val qtdItensConfere: Boolean?,
    val chaveValida: Boolean
)
