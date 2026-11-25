package br.com.feira.nfce.categorias.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "palavras_chave",
    foreignKeys = [ForeignKey(entity = CategoriaEntity::class, parentColumns = ["id"], childColumns = ["categoriaId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["termoNormalizado", "categoriaId"], unique = true), Index("categoriaId")]
)
data class PalavraChaveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val termo: String,
    val termoNormalizado: String,
    val categoriaId: Long,
    val prioridade: Int,
    val tipoCorrespondencia: String,
    val origem: String = "BASE",
    val ativa: Boolean = true
)
