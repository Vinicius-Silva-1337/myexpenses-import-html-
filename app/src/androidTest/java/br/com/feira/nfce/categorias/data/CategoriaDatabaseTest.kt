package br.com.feira.nfce.categorias.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import br.com.feira.nfce.categorias.data.entities.CategoriaEntity
import br.com.feira.nfce.categorias.data.seed.CategoriaSeeder
import br.com.feira.nfce.categorias.domain.Categorizador
import br.com.feira.nfce.categorias.domain.Normalizador
import br.com.feira.nfce.categorias.data.entities.ProductOverrideEntity
import br.com.feira.nfce.notas.data.ItemEmRevisao
import br.com.feira.nfce.notas.data.NotaRepository
import br.com.feira.nfce.notas.data.NotaFiscalEntity
import br.com.feira.nfce.notas.data.ItemNotaEntity
import br.com.feira.nfce.notas.data.PagamentoEntity
import br.com.feira.nfce.notas.model.ItemNota
import br.com.feira.nfce.notas.model.NotaFiscal
import br.com.feira.nfce.notas.model.Validacao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.math.BigDecimal
import java.time.OffsetDateTime

@RunWith(AndroidJUnit4::class)
class CategoriaDatabaseTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    @Test fun seedingIsIdempotentAndPreservesCustomRows() = runBlocking {
        val seeder = CategoriaSeeder(context, db)
        seeder.inicializar(1)
        val counts = db.categoriaDao().observarTodas(1).first().size to db.palavraChaveDao().ativas(1).size
        val custom = db.categoriaDao().inserir(CategoriaEntity(nome = "Minha categoria", origem = "PERSONALIZADA"))
        seeder.inicializar(1)
        assertEquals(counts.first + 1, db.categoriaDao().observarTodas(1).first().size)
        assertEquals(counts.second, db.palavraChaveDao().ativas(1).size)
        assertEquals("Minha categoria", db.categoriaDao().porId(custom,1)?.nome)
    }

    @Test fun learnedCorrectionUpdatesExistingOverrideAndWinsOnNextLookup() = runBlocking {
        CategoriaSeeder(context, db).inicializar(1)
        val category = db.categoriaDao().porNome("Limpeza",1)!!
        val normalized = Normalizador.normalizar("Amaciante exemplo")
        val firstId = db.productOverrideDao().salvar(ProductOverrideEntity(descricaoOriginal = "Amaciante exemplo", descricaoNormalizada = normalized, categoriaId = category.id, dataCriacao = 1))
        db.productOverrideDao().salvar(ProductOverrideEntity(id = firstId, descricaoOriginal = "AMACIANTE EXEMPLO", descricaoNormalizada = normalized, categoriaId = category.id, dataCriacao = 1))
        assertEquals(firstId, db.productOverrideDao().buscar(normalized,1)?.id)
        val result = Categorizador(db.categoriaDao(), db.palavraChaveDao(), db.productOverrideDao()).categorizar("Amaciante exemplo",1)
        assertEquals("OVERRIDE", result.origem)
        assertEquals(category.id, result.categoriaId)
        db.productOverrideDao().excluir(firstId,1)
        assertNull(db.productOverrideDao().buscar(normalized,1))
    }

    @Test fun parserToCategoryCorrectionPersistsInvoiceAndDoesNotNeedCategoryDatabaseInParser() = runBlocking {
        val seeder = CategoriaSeeder(context, db)
        seeder.inicializar(1)
        val parserKey = "13261112345678000190650010000000011123456780"
        val note = NotaFiscal(
            chave = parserKey,
            cnpj = "12345678000190",
            emitente = "Mercado Sintético",
            numero = "1", serie = "1",
            dataEmissao = OffsetDateTime.parse("2026-11-20T18:56:11-04:00"),
            total = BigDecimal("12.50"), pagamentos = emptyList(),
            itens = listOf(ItemNota("PRODUTO DESCONHECIDO", "A1", BigDecimal("1"), "UN", BigDecimal("12.50"), BigDecimal("12.50"))),
            validacao = Validacao(true, BigDecimal("12.50"), true, true)
        )
        val classifier = Categorizador(db.categoriaDao(), db.palavraChaveDao(), db.productOverrideDao())
        val repo = NotaRepository(db, classifier)
        val review = repo.categorizar(note,1)
        assertEquals("Sem categoria", review.single().resultado.categoriaNome)
        val custom = db.categoriaDao().inserir(CategoriaEntity(nome = "Uso teste", origem = "PERSONALIZADA"))
        review[0].resultado = br.com.feira.nfce.categorias.domain.ResultadoCategorizacao(custom, "Uso teste", true, "MANUAL")
        assertTrue(repo.salvar(note, review, setOf(0),1))
        assertEquals(1, db.notaDao().itensDaNota(1).size)
        assertEquals(custom, db.productOverrideDao().buscar(Normalizador.normalizar("PRODUTO DESCONHECIDO"),1)?.categoriaId)
        assertFalse(repo.salvar(note, review, setOf(0),1))
    }

    @Test fun deletingInvoiceCascadesOnlyItsItemsAndPayments() = runBlocking {
        CategoriaSeeder(context, db).inicializar(1)
        val category = db.categoriaDao().porNome("Limpeza",1)!!
        val beforeCategories = db.categoriaDao().observarTodas(1).first().size
        val beforeKeywords = db.palavraChaveDao().observarTodas(1).first().size
        val overrideId = db.productOverrideDao().salvar(
            ProductOverrideEntity(
                descricaoOriginal = "Detergente aprendido",
                descricaoNormalizada = Normalizador.normalizar("Detergente aprendido"),
                categoriaId = category.id,
                dataCriacao = 1
            )
        )

        fun invoice(key: String) = NotaFiscalEntity(
            chave = key, cnpj = "12345678000190", emitente = "Mercado", numero = key,
            serie = "1", dataEmissao = null, total = "10.00", somaItensConfere = true,
            somaItens = "10.00", qtdItensConfere = true, chaveValida = true, importadaEm = 1
        )
        fun item(invoiceId: Long, description: String) = ItemNotaEntity(
            notaId = invoiceId, descricao = description, codigo = null, quantidade = "1",
            unidade = "UN", valorUnitario = "5.00", valorTotal = "5.00",
            categoriaId = category.id, categoriaOrigem = "REGRA", identificacaoAmbigua = false
        )

        val dao = db.notaDao()
        val deletedId = dao.inserirNota(invoice("chave-excluir"))
        val retainedId = dao.inserirNota(invoice("chave-manter"))
        dao.inserirItens(listOf(item(deletedId, "Produto 1"), item(deletedId, "Produto 2"), item(retainedId, "Produto de outra nota")))
        dao.inserirPagamentos(listOf(PagamentoEntity(notaId = deletedId, forma = "Dinheiro", valor = "10.00")))

        val repository = NotaRepository(db, Categorizador(db.categoriaDao(), db.palavraChaveDao(), db.productOverrideDao()))
        assertTrue(repository.excluirNota(deletedId,1))

        val remaining = dao.observarNotas(1).first()
        assertEquals(listOf(retainedId), remaining.map { it.nota.id })
        assertEquals("Produto de outra nota", remaining.single().itens.single().descricao)
        assertTrue(dao.itensDaNota(deletedId).isEmpty())
        assertEquals(0L, db.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM pagamentos WHERE notaId = ?", arrayOf(deletedId.toString())
        ).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) })
        assertEquals(beforeCategories, db.categoriaDao().observarTodas(1).first().size)
        assertEquals(beforeKeywords, db.palavraChaveDao().observarTodas(1).first().size)
        assertEquals(overrideId, db.productOverrideDao().buscar(Normalizador.normalizar("Detergente aprendido"),1)?.id)
    }
}
