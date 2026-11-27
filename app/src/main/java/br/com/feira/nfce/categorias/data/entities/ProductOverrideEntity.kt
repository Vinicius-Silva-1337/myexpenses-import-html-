package br.com.feira.nfce.categorias.data.entities

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_overrides",
    foreignKeys = [ForeignKey(entity = CategoriaEntity::class, parentColumns = ["id"], childColumns = ["categoriaId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["userId", "descricaoNormalizada"], unique = true), Index("categoriaId")]
)
data class ProductOverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val descricaoOriginal: String,
    @ColumnInfo(defaultValue = "1") val userId: Long = 1,
    val descricaoNormalizada: String,
    val categoriaId: Long,
    val dataCriacao: Long
)
