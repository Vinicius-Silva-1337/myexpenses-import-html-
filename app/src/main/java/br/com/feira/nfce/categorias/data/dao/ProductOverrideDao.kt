package br.com.feira.nfce.categorias.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import br.com.feira.nfce.categorias.data.entities.ProductOverrideEntity

@Dao
interface ProductOverrideDao {
    @Query("SELECT * FROM product_overrides WHERE userId=:userId AND descricaoNormalizada = :normalizada LIMIT 1") suspend fun buscar(normalizada: String,userId:Long): ProductOverrideEntity?
    @Query("SELECT * FROM product_overrides WHERE userId=:userId ORDER BY dataCriacao DESC") fun observarTodas(userId:Long): Flow<List<ProductOverrideEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun salvar(entity: ProductOverrideEntity): Long
    @Query("DELETE FROM product_overrides WHERE id = :id AND userId=:userId") suspend fun excluir(id: Long,userId:Long): Int
    @Query("UPDATE product_overrides SET categoriaId = :destino WHERE categoriaId = :origem AND userId=:userId") suspend fun moverCategoria(origem: Long, destino: Long,userId:Long): Int
}
