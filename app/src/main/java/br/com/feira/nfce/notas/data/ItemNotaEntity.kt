package br.com.feira.nfce.notas.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import br.com.feira.nfce.categorias.data.entities.CategoriaEntity

@Entity(
    tableName = "itens_nota",
    foreignKeys = [
        ForeignKey(entity = NotaFiscalEntity::class, parentColumns = ["id"], childColumns = ["notaId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoriaEntity::class, parentColumns = ["id"], childColumns = ["categoriaId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("notaId"), Index("categoriaId")]
)
data class ItemNotaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notaId: Long,
    val descricao: String,
    val codigo: String?,
    val quantidade: String,
    val unidade: String,
    val valorUnitario: String,
    val valorTotal: String,
    val categoriaId: Long?,
    val categoriaOrigem: String,
    val identificacaoAmbigua: Boolean
)
