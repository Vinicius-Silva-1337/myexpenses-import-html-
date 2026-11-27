package br.com.feira.nfce.notas.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class NotaComItens(
    @androidx.room.Embedded val nota: NotaFiscalEntity,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "notaId") val itens: List<ItemNotaEntity>,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "notaId") val pagamentos: List<PagamentoEntity>
)

@Dao
interface NotaDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun inserirNota(nota: NotaFiscalEntity): Long
    @Insert suspend fun inserirItens(itens: List<ItemNotaEntity>)
    @Insert suspend fun inserirPagamentos(pagamentos: List<PagamentoEntity>)
    @Query("SELECT * FROM notas_fiscais WHERE userId=:userId AND chave = :chave LIMIT 1") suspend fun porChave(chave: String,userId:Long): NotaFiscalEntity?
    @Query("SELECT * FROM notas_fiscais WHERE userId=:userId AND id=:id LIMIT 1") suspend fun porId(id:Long,userId:Long):NotaFiscalEntity?
    @Query("DELETE FROM notas_fiscais WHERE id = :notaId AND userId=:userId") suspend fun excluirNota(notaId: Long,userId:Long): Int
    @Transaction @Query("SELECT * FROM notas_fiscais WHERE userId=:userId ORDER BY importadaEm DESC") fun observarNotas(userId:Long): Flow<List<NotaComItens>>
    @Query("SELECT * FROM itens_nota WHERE notaId = :notaId ORDER BY id") suspend fun itensDaNota(notaId: Long): List<ItemNotaEntity>
    @Query("SELECT COUNT(*) FROM itens_nota i JOIN notas_fiscais n ON n.id=i.notaId WHERE i.categoriaId = :categoriaId AND n.userId=:userId") suspend fun contarItensDaCategoria(categoriaId: Long,userId:Long): Int
    @Query("UPDATE itens_nota SET categoriaId = :destino WHERE categoriaId = :origem AND notaId IN (SELECT id FROM notas_fiscais WHERE userId=:userId)") suspend fun moverCategoria(origem: Long, destino: Long,userId:Long): Int
    @Query("SELECT * FROM notas_fiscais WHERE userId=:userId AND id=:invoiceId LIMIT 1") suspend fun invoiceForUser(invoiceId:Long,userId:Long):NotaFiscalEntity?
}
