package br.com.feira.nfce.categorias.domain

import br.com.feira.nfce.categorias.data.dao.CategoriaDao
import br.com.feira.nfce.categorias.data.dao.PalavraChaveDao
import br.com.feira.nfce.categorias.data.dao.ProductOverrideDao

class Categorizador(
    private val categorias: CategoriaDao,
    private val palavras: PalavraChaveDao,
    private val overrides: ProductOverrideDao
) {
    suspend fun categorizar(descricao: String): ResultadoCategorizacao {
        val normalized = Normalizador.normalizar(descricao)
        val activeCategories = categorias.ativas()
        val unknown = activeCategories.firstOrNull { it.nome.equals("Sem categoria", true) }
        val override = overrides.buscar(normalized)
        if (override != null) {
            val category = categorias.porId(override.categoriaId)?.takeIf { it.ativa }
            if (category != null) return ResultadoCategorizacao(category.id, category.nome, true, "OVERRIDE", override.descricaoOriginal)
        }
        val matches = RegrasCorrespondencia.encontrar(normalized, palavras.ativas(), activeCategories)
        if (matches.isEmpty()) return ResultadoCategorizacao(unknown?.id, "Sem categoria", false, "NENHUMA")
        val best = matches.first()
        val equallyStrong = matches.takeWhile { it.pontos == best.pontos }
        val categoryIds = equallyStrong.map { it.categoria.id }.distinct()
        if (categoryIds.size > 1) return ResultadoCategorizacao(unknown?.id, "Sem categoria", false, "AMBIGUO", equallyStrong.joinToString { it.palavra.termo }, true)
        return ResultadoCategorizacao(best.categoria.id, best.categoria.nome, true, best.palavra.tipoCorrespondencia.uppercase(), best.palavra.termo)
    }
}
