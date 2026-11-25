package br.com.feira.nfce.categorias.domain

import br.com.feira.nfce.categorias.data.entities.CategoriaEntity
import br.com.feira.nfce.categorias.data.entities.PalavraChaveEntity

object RegrasCorrespondencia {
    data class Correspondencia(val palavra: PalavraChaveEntity, val categoria: CategoriaEntity, val pontos: Triple<Int, Int, Int>)

    fun encontrar(descricaoNormalizada: String, termos: List<PalavraChaveEntity>, categorias: List<CategoriaEntity>): List<Correspondencia> {
        val categoryById = categorias.associateBy { it.id }
        val words = termos.filter { it.ativa }.mapNotNull { term ->
            val normalized = Normalizador.normalizar(term.termoNormalizado)
            if (normalized.isBlank()) return@mapNotNull null
            val isPhrase = term.tipoCorrespondencia.equals("FRASE", true)
            val matches = if (isPhrase) {
                " $descricaoNormalizada ".contains(" $normalized ")
            } else {
                val token = normalized.split(' ').singleOrNull() ?: return@mapNotNull null
                descricaoNormalizada.split(' ').contains(token)
            }
            if (!matches) return@mapNotNull null
            val category = categoryById[term.categoriaId] ?: return@mapNotNull null
            if (!category.ativa) return@mapNotNull null
            val tokenCount = normalized.split(' ').size
            val typeRank = if (isPhrase) 1 else 0
            Correspondencia(term, category, Triple(typeRank, tokenCount, term.prioridade))
        }
        return words.sortedWith(compareByDescending<Correspondencia> { it.pontos.first }
            .thenByDescending { it.pontos.second }
            .thenByDescending { it.pontos.third }
            .thenBy { it.palavra.termoNormalizado })
    }
}
