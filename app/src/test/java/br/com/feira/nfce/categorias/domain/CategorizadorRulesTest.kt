package br.com.feira.nfce.categorias.domain

import org.junit.Assert.*
import org.junit.Test
import br.com.feira.nfce.categorias.data.entities.CategoriaEntity
import br.com.feira.nfce.categorias.data.entities.PalavraChaveEntity

class CategorizadorRulesTest {
    private val groceries = CategoriaEntity(1, "Alimentos")
    private val cleaning = CategoriaEntity(2, "Limpeza")
    private val pharmacy = CategoriaEntity(3, "Saúde e medicamentos")
    private val custom = CategoriaEntity(4, "Meus itens", origem = "PERSONALIZADA")

    @Test fun normalizesCaseAccentsAndRepeatedWhitespace() {
        assertEquals("AGUA SANITARIA", Normalizador.normalizar("  água   sanitária "))
        assertEquals("FEIJAO CARIOCA", Normalizador.normalizar("Feijão Carioca"))
    }

    @Test fun recognizesWholePhraseAndDoesNotMatchInsideOtherWords() {
        val terms = listOf(word("AGUA SANITARIA", cleaning, "FRASE", 20), word("AGUA", groceries, "PALAVRA", 10))
        val found = RegrasCorrespondencia.encontrar("AGUA SANITARIA QUALYBRILL 1L", terms, listOf(groceries, cleaning))
        assertEquals(2, found.first().categoria.id)
        assertTrue(RegrasCorrespondencia.encontrar("DESAGUAMENTO", listOf(word("AGUA", groceries, "PALAVRA", 10)), listOf(groceries)).isEmpty())
    }

    @Test fun specificPhrasesOutrankGenericWordsAndEqualConflictsRemainVisible() {
        val terms = listOf(word("AGUA SANITARIA", cleaning, "FRASE", 10), word("AGUA", groceries, "PALAVRA", 100))
        assertEquals(2, RegrasCorrespondencia.encontrar("AGUA SANITARIA", terms, listOf(groceries, cleaning)).first().categoria.id)
        val conflict = RegrasCorrespondencia.encontrar("CARGA", listOf(word("CARGA", groceries, "PALAVRA", 10), word("CARGA", cleaning, "PALAVRA", 10)), listOf(groceries, cleaning))
        assertEquals(2, conflict.size)
        assertEquals(conflict[0].pontos, conflict[1].pontos)
    }

    @Test fun supportsProductsFromDifferentRetailSegmentsAndCustomCategories() {
        val entries = listOf(
            "DIPIRONA" to pharmacy, "GASOLINA" to cleaning, "CAFE EXPRESSO" to groceries,
            "CABO USB" to cleaning, "CAMISETA" to groceries, "CIMENTO" to custom
        )
        entries.forEach { (description, category) ->
            val term = word(description, category, if (' ' in description) "FRASE" else "PALAVRA", 10)
            val result = RegrasCorrespondencia.encontrar(description, listOf(term), listOf(groceries, cleaning, pharmacy, custom))
            assertEquals(description, category.id, result.firstOrNull()?.categoria?.id)
        }
    }

    private fun word(term: String, category: CategoriaEntity, type: String, priority: Int) = PalavraChaveEntity(
        termo = term, termoNormalizado = Normalizador.normalizar(term), categoriaId = category.id,
        prioridade = priority, tipoCorrespondencia = type
    )
}
