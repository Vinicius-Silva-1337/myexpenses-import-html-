package br.com.feira.nfce.notas.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pagamentos",
    foreignKeys = [ForeignKey(entity = NotaFiscalEntity::class, parentColumns = ["id"], childColumns = ["notaId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("notaId")]
)
data class PagamentoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notaId: Long,
    val forma: String,
    val valor: String
)
