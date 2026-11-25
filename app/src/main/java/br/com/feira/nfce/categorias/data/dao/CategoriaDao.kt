package br.com.feira.nfce.categorias.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import br.com.feira.nfce.categorias.data.entities.CategoriaEntity

@Dao
interface CategoriaDao {
    @Query("SELECT * FROM categorias ORDER BY nome COLLATE NOCASE") fun observarTodas(): Flow<List<CategoriaEntity>>
    @Query("SELECT * FROM categorias WHERE ativa = 1 ORDER BY nome COLLATE NOCASE") suspend fun ativas(): List<CategoriaEntity>
    @Query("SELECT * FROM categorias WHERE id = :id") suspend fun porId(id: Long): CategoriaEntity?
    @Query("SELECT * FROM categorias WHERE nome = :nome COLLATE NOCASE LIMIT 1") suspend fun porNome(nome: String): CategoriaEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun inserir(entity: CategoriaEntity): Long
    @Query("UPDATE categorias SET nome = :nome, nomeNormalizado = :normalizado, descricao = :descricao WHERE id = :id AND origem = 'PERSONALIZADA'") suspend fun renomear(id: Long, nome: String, normalizado: String, descricao: String?): Int
    @Query("UPDATE categorias SET ativa = :ativa WHERE id = :id AND origem = 'PERSONALIZADA'") suspend fun definirAtiva(id: Long, ativa: Boolean): Int
}
