package br.com.feira.nfce.categorias.data.entities

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale

@Entity(tableName = "categorias", indices = [Index(value = ["userId", "nomeNormalizado"], unique = true)])
data class CategoriaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    @ColumnInfo(defaultValue = "1") val userId: Long = 1,
    val nomeNormalizado: String = nome.trim().uppercase(Locale.ROOT),
    val descricao: String? = null,
    val origem: String = "PADRAO",
    val ativa: Boolean = true,
    val legacyId: Long? = null
)
