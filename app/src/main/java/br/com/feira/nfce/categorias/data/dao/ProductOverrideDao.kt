package br.com.feira.nfce.categorias.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import br.com.feira.nfce.categorias.data.entities.ProductOverrideEntity

@Dao
interface ProductOverrideDao {
    @Query("SELECT * FROM product_overrides WHERE descricaoNormalizada = :normalizada LIMIT 1") suspend fun buscar(normalizada: String): ProductOverrideEntity?
    @Query("SELECT * FROM product_overrides ORDER BY dataCriacao DESC") fun observarTodas(): Flow<List<ProductOverrideEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun salvar(entity: ProductOverrideEntity): Long
    @Query("DELETE FROM product_overrides WHERE id = :id") suspend fun excluir(id: Long): Int
    @Query("UPDATE product_overrides SET categoriaId = :destino WHERE categoriaId = :origem") suspend fun moverCategoria(origem: Long, destino: Long): Int
}
