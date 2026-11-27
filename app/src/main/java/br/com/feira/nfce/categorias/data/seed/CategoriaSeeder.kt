package br.com.feira.nfce.categorias.data.seed

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import br.com.feira.nfce.categorias.data.AppDatabase
import br.com.feira.nfce.categorias.data.entities.CategoriaEntity
import br.com.feira.nfce.categorias.data.entities.PalavraChaveEntity
import br.com.feira.nfce.categorias.domain.Normalizador

class CategoriaSeeder(private val context: Context, private val database: AppDatabase) {
    private data class SeedWord(val termo: String, val categoria: String, val tipo: String, val prioridade: Int)

    suspend fun inicializar(userId: Long) = database.withTransaction {
        val categorias = database.categoriaDao()
        (FINANCE_CATEGORIES + DEFAULT_CATEGORIES).distinct().forEach { name ->
            if (categorias.porNome(name,userId) == null) categorias.inserir(CategoriaEntity(nome = name, userId = userId, origem = "PADRAO"))
        }
        val words: List<SeedWord> = context.assets.open("palavras_chave.json").bufferedReader().use { reader ->
            Gson().fromJson(reader, object : TypeToken<List<SeedWord>>() {}.type)
        } ?: emptyList()
        words.forEach { word ->
            val category = categorias.porNome(word.categoria,userId) ?: return@forEach
            database.palavraChaveDao().inserir(
                PalavraChaveEntity(
                    termo = word.termo,
                    userId = userId,
                    termoNormalizado = Normalizador.normalizar(word.termo),
                    categoriaId = category.id,
                    prioridade = word.prioridade,
                    tipoCorrespondencia = word.tipo.uppercase(),
                    origem = "BASE"
                )
            )
        }
    }

    companion object {
        val FINANCE_CATEGORIES = listOf("Alimentação", "Transporte", "Casa", "Educação", "Saúde", "Lazer", "Outros", "Salário")
        val DEFAULT_CATEGORIES = listOf(
            "Alimentos", "Bebidas", "Limpeza", "Higiene pessoal", "Saúde e medicamentos",
            "Eletrônicos", "Vestuário", "Casa e decoração", "Transporte", "Outros", "Sem categoria"
        )
    }
}
