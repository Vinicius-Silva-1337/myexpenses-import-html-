package br.com.feira.nfce.notas.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.feira.nfce.categorias.data.AppDatabase
import br.com.feira.nfce.categorias.data.entities.CategoriaEntity
import br.com.feira.nfce.categorias.data.entities.ProductOverrideEntity
import br.com.feira.nfce.categorias.domain.Categorizador
import br.com.feira.nfce.categorias.domain.Normalizador
import br.com.feira.nfce.notas.model.ItemNota
import br.com.feira.nfce.notas.model.NotaFiscal
import br.com.feira.nfce.notas.model.Pagamento
import br.com.feira.nfce.notas.model.Validacao
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotaPersistenciaTest {
    private lateinit var context: Context
    private lateinit var databaseName: String
    private var db: AppDatabase? = null

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "nota-persistencia-${UUID.randomUUID()}.db"
        db = abrirBanco()
    }

    @After fun cleanup() {
        db?.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun gravaConsultaQrReabreEvitaDuplicidadeEExcluiApenasNotaSelecionada() = runBlocking {
        var current = db!!
        val customCategoryId = current.categoriaDao().inserir(CategoriaEntity(nome = "Categoria preservada", origem = "PERSONALIZADA"))
        val overrideKey = Normalizador.normalizar("Produto aprendido")
        val overrideId = current.productOverrideDao().salvar(
            ProductOverrideEntity(
                descricaoOriginal = "Produto aprendido",
                descricaoNormalizada = overrideKey,
                categoriaId = customCategoryId,
                dataCriacao = 1
            )
        )
        current.palavraChaveDao().inserir(
            br.com.feira.nfce.categorias.data.entities.PalavraChaveEntity(
                termo = "palavra preservada", termoNormalizado = "PALAVRA PRESERVADA",
                categoriaId = customCategoryId, prioridade = 1, tipoCorrespondencia = "EXATA", origem = "USUARIO"
            )
        )

        val repository = repository(current)
        val first = nota("chave-qrcode-1")
        val second = nota("chave-qrcode-2")
        assertTrue(repository.salvarConsultaQr(first))
        assertFalse(repository.salvarConsultaQr(first))
        assertTrue(repository.salvarConsultaQr(second))
        assertEquals(2, current.notaDao().observarNotas().first().size)
        assertEquals(2, current.notaDao().observarNotas().first().first().itens.size)
        assertEquals("NAO_CLASSIFICADO", current.notaDao().observarNotas().first().first().itens.first().categoriaOrigem)
        assertEquals(0, current.notaDao().observarNotas().first().first().itens.count { it.categoriaId != null })

        current.close()
        db = null
        current = abrirBanco()
        db = current
        var notes = current.notaDao().observarNotas().first()
        assertEquals(2, notes.size)
        val firstStored = notes.first { it.nota.chave == first.chave }
        assertEquals("45.00", firstStored.nota.totalProdutos)
        assertEquals(2, firstStored.itens.size)
        assertEquals("3.00", firstStored.pagamentos.single().valor)

        assertTrue(repository(current).excluirNota(firstStored.nota.id))
        notes = current.notaDao().observarNotas().first()
        assertEquals(listOf(second.chave), notes.map { it.nota.chave })
        assertEquals(2, notes.single().itens.size)
        assertEquals(overrideId, current.productOverrideDao().buscar(overrideKey)?.id)
        assertEquals("Categoria preservada", current.categoriaDao().porId(customCategoryId)?.nome)
        assertEquals(1, current.palavraChaveDao().observarTodas().first().size)
    }

    private fun abrirBanco(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .build()

    private fun repository(database: AppDatabase) = NotaRepository(
        database,
        Categorizador(database.categoriaDao(), database.palavraChaveDao(), database.productOverrideDao())
    )

    private fun nota(key: String) = NotaFiscal(
        chave = key,
        cnpj = "12345678000190",
        emitente = "Loja de teste",
        numero = "123",
        serie = "1",
        dataEmissao = OffsetDateTime.parse("2026-11-25T10:00:00-04:00"),
        total = BigDecimal("45.00"),
        pagamentos = listOf(Pagamento("Dinheiro", BigDecimal("3.00"))),
        itens = listOf(
            ItemNota("Produto um", "1", BigDecimal("2"), "UN", BigDecimal("10.00"), BigDecimal("20.00")),
            ItemNota("Produto dois", "2", BigDecimal("1"), "KG", BigDecimal("25.00"), BigDecimal("25.00"))
        ),
        validacao = Validacao(true, BigDecimal("45.00"), true, true),
        totalProdutos = BigDecimal("45.00"),
        descontos = BigDecimal("0.00"),
        valorPago = BigDecimal("45.00"),
        quantidadeItensInformada = 2
    )
}
