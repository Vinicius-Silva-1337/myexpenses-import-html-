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
        seeder.inicializar()
        val counts = db.categoriaDao().observarTodas().first().size to db.palavraChaveDao().ativas().size
        val custom = db.categoriaDao().inserir(CategoriaEntity(nome = "Minha categoria", origem = "PERSONALIZADA"))
        seeder.inicializar()
        assertEquals(counts.first + 1, db.categoriaDao().observarTodas().first().size)
        assertEquals(counts.second, db.palavraChaveDao().ativas().size)
        assertEquals("Minha categoria", db.categoriaDao().porId(custom)?.nome)
    }

    @Test fun learnedCorrectionUpdatesExistingOverrideAndWinsOnNextLookup() = runBlocking {
        CategoriaSeeder(context, db).inicializar()
        val category = db.categoriaDao().porNome("Limpeza")!!
        val normalized = Normalizador.normalizar("Amaciante exemplo")
        val firstId = db.productOverrideDao().salvar(ProductOverrideEntity(descricaoOriginal = "Amaciante exemplo", descricaoNormalizada = normalized, categoriaId = category.id, dataCriacao = 1))
        db.productOverrideDao().salvar(ProductOverrideEntity(id = firstId, descricaoOriginal = "AMACIANTE EXEMPLO", descricaoNormalizada = normalized, categoriaId = category.id, dataCriacao = 1))
        assertEquals(firstId, db.productOverrideDao().buscar(normalized)?.id)
        val result = Categorizador(db.categoriaDao(), db.palavraChaveDao(), db.productOverrideDao()).categorizar("Amaciante exemplo")
        assertEquals("OVERRIDE", result.origem)
        assertEquals(category.id, result.categoriaId)
        db.productOverrideDao().excluir(firstId)
        assertNull(db.productOverrideDao().buscar(normalized))
    }

    @Test fun parserToCategoryCorrectionPersistsInvoiceAndDoesNotNeedCategoryDatabaseInParser() = runBlocking {
        val seeder = CategoriaSeeder(context, db)
        seeder.inicializar()
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
        val review = repo.categorizar(note)
        assertEquals("Sem categoria", review.single().resultado.categoriaNome)
        val custom = db.categoriaDao().inserir(CategoriaEntity(nome = "Uso teste", origem = "PERSONALIZADA"))
        review[0].resultado = br.com.feira.nfce.categorias.domain.ResultadoCategorizacao(custom, "Uso teste", true, "MANUAL")
        assertTrue(repo.salvar(note, review, setOf(0)))
        assertEquals(1, db.notaDao().itensDaNota(1).size)
        assertEquals(custom, db.productOverrideDao().buscar(Normalizador.normalizar("PRODUTO DESCONHECIDO"))?.categoriaId)
        assertFalse(repo.salvar(note, review, setOf(0)))
    }
}
