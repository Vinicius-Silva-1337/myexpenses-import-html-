package br.com.feira.nfce.categorias.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import br.com.feira.nfce.categorias.data.entities.PalavraChaveEntity

@Dao
interface PalavraChaveDao {
    @Query("SELECT * FROM palavras_chave WHERE ativa = 1") suspend fun ativas(): List<PalavraChaveEntity>
    @Query("SELECT * FROM palavras_chave ORDER BY termoNormalizado") fun observarTodas(): Flow<List<PalavraChaveEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun inserir(entity: PalavraChaveEntity): Long
    @Query("UPDATE palavras_chave SET termo = :termo, termoNormalizado = :normalizado, prioridade = :prioridade, tipoCorrespondencia = :tipo WHERE id = :id AND origem = 'USUARIO'") suspend fun atualizar(id: Long, termo: String, normalizado: String, prioridade: Int, tipo: String): Int
    @Query("UPDATE palavras_chave SET ativa = :ativa WHERE id = :id") suspend fun definirAtiva(id: Long, ativa: Boolean): Int
    @Query("SELECT COUNT(*) FROM palavras_chave WHERE categoriaId = :categoriaId") suspend fun contarDaCategoria(categoriaId: Long): Int
    @Query("UPDATE palavras_chave SET categoriaId = :destino WHERE categoriaId = :origem") suspend fun moverCategoria(origem: Long, destino: Long): Int
}
