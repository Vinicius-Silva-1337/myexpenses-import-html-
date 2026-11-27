package br.com.feira.nfce.categorias.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import br.com.feira.nfce.categorias.data.entities.CategoriaEntity

@Dao
interface CategoriaDao {
    @Query("SELECT * FROM categorias WHERE userId=:userId ORDER BY nome COLLATE NOCASE") fun observarTodas(userId: Long): Flow<List<CategoriaEntity>>
    @Query("SELECT * FROM categorias WHERE userId=:userId AND ativa = 1 ORDER BY nome COLLATE NOCASE") suspend fun ativas(userId: Long): List<CategoriaEntity>
    @Query("SELECT * FROM categorias WHERE id = :id AND userId=:userId") suspend fun porId(id: Long,userId:Long): CategoriaEntity?
    @Query("SELECT * FROM categorias WHERE userId=:userId AND nome = :nome COLLATE NOCASE LIMIT 1") suspend fun porNome(nome: String,userId:Long): CategoriaEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun inserir(entity: CategoriaEntity): Long
    @Query("UPDATE categorias SET nome = :nome, nomeNormalizado = :normalizado, descricao = :descricao WHERE id = :id AND userId=:userId AND origem = 'PERSONALIZADA'") suspend fun renomear(id: Long,userId:Long, nome: String, normalizado: String, descricao: String?): Int
    @Query("UPDATE categorias SET ativa = :ativa WHERE id = :id AND userId=:userId AND origem = 'PERSONALIZADA'") suspend fun definirAtiva(id: Long,userId:Long, ativa: Boolean): Int
}
