package br.com.feira.nfce.notas.data

import androidx.room.withTransaction
import br.com.feira.nfce.categorias.data.AppDatabase
import br.com.feira.nfce.categorias.data.entities.ProductOverrideEntity
import br.com.feira.nfce.categorias.domain.Categorizador
import br.com.feira.nfce.categorias.domain.Normalizador
import br.com.feira.nfce.categorias.domain.ResultadoCategorizacao
import br.com.feira.nfce.notas.model.NotaFiscal
import java.math.BigDecimal
import java.time.Instant
import java.time.format.DateTimeFormatter

data class ItemEmRevisao(
    val descricao: String,
    val codigo: String?,
    val quantidade: BigDecimal,
    val unidade: String,
    val valorUnitario: BigDecimal,
    val valorTotal: BigDecimal,
    var resultado: ResultadoCategorizacao
)

class NotaRepository(private val db: AppDatabase, private val categorizador: Categorizador) {
    suspend fun categorizar(nota: NotaFiscal): List<ItemEmRevisao> = nota.itens.map { item ->
        ItemEmRevisao(item.descricao, item.codigo, item.quantidade, item.unidade, item.valorUnitario, item.valorTotal, categorizador.categorizar(item.descricao))
    }

    suspend fun salvar(nota: NotaFiscal, itens: List<ItemEmRevisao>, salvarAprendizado: Set<Int>): Boolean = db.withTransaction {
        val dao = db.notaDao()
        if (dao.porChave(nota.chave) != null) return@withTransaction false
        val notaId = dao.inserirNota(
            NotaFiscalEntity(
                chave = nota.chave, cnpj = nota.cnpj, emitente = nota.emitente,
                numero = nota.numero, serie = nota.serie,
                dataEmissao = nota.dataEmissao?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                total = nota.total.toPlainString(),
                somaItensConfere = nota.validacao.somaItensConfereComTotal,
                somaItens = nota.validacao.somaItens.toPlainString(),
                qtdItensConfere = nota.validacao.qtdItensConfere,
                chaveValida = nota.validacao.chaveValida,
                importadaEm = Instant.now().toEpochMilli()
            )
        )
        if (notaId <= 0) return@withTransaction false
        dao.inserirItens(itens.map { item ->
            ItemNotaEntity(
                notaId = notaId,
                descricao = item.descricao,
                codigo = item.codigo,
                quantidade = item.quantidade.toPlainString(),
                unidade = item.unidade,
                valorUnitario = item.valorUnitario.toPlainString(),
                valorTotal = item.valorTotal.toPlainString(),
                categoriaId = item.resultado.categoriaId,
                categoriaOrigem = item.resultado.origem,
                identificacaoAmbigua = item.resultado.ambiguo
            )
        })
        dao.inserirPagamentos(nota.pagamentos.map { PagamentoEntity(notaId = notaId, forma = it.forma, valor = it.valor.toPlainString()) })
        salvarAprendizado.forEach { index ->
            val item = itens.getOrNull(index) ?: return@forEach
            val categoryId = item.resultado.categoriaId ?: return@forEach
            val normalized = Normalizador.normalizar(item.descricao)
            val existing = db.productOverrideDao().buscar(normalized)
            db.productOverrideDao().salvar(
                ProductOverrideEntity(
                    id = existing?.id ?: 0,
                    descricaoOriginal = item.descricao,
                    descricaoNormalizada = normalized,
                    categoriaId = categoryId,
                    dataCriacao = existing?.dataCriacao ?: Instant.now().toEpochMilli()
                )
            )
        }
        true
    }

    suspend fun salvarOverride(descricao: String, categoriaId: Long) {
        val normalized = Normalizador.normalizar(descricao)
        val existing = db.productOverrideDao().buscar(normalized)
        db.productOverrideDao().salvar(ProductOverrideEntity(
            id = existing?.id ?: 0,
            descricaoOriginal = descricao,
            descricaoNormalizada = normalized,
            categoriaId = categoriaId,
            dataCriacao = existing?.dataCriacao ?: Instant.now().toEpochMilli()
        ))
    }
}
