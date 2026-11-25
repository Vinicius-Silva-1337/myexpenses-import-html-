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
    @Query("SELECT * FROM notas_fiscais WHERE chave = :chave LIMIT 1") suspend fun porChave(chave: String): NotaFiscalEntity?
    @Transaction @Query("SELECT * FROM notas_fiscais ORDER BY importadaEm DESC") fun observarNotas(): Flow<List<NotaComItens>>
    @Query("SELECT * FROM itens_nota WHERE notaId = :notaId ORDER BY id") suspend fun itensDaNota(notaId: Long): List<ItemNotaEntity>
    @Query("SELECT COUNT(*) FROM itens_nota WHERE categoriaId = :categoriaId") suspend fun contarItensDaCategoria(categoriaId: Long): Int
    @Query("UPDATE itens_nota SET categoriaId = :destino WHERE categoriaId = :origem") suspend fun moverCategoria(origem: Long, destino: Long): Int
}
