package br.com.feira.nfce.notas.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "notas_fiscais", indices = [Index(value = ["userId", "chave"], unique = true)])
data class NotaFiscalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chave: String,
    @ColumnInfo(defaultValue = "1") val userId: Long = 1,
    val cnpj: String,
    val emitente: String,
    val numero: String?,
    val serie: String?,
    val dataEmissao: String?,
    val total: String,
    val somaItensConfere: Boolean,
    val somaItens: String,
    val qtdItensConfere: Boolean?,
    val chaveValida: Boolean,
    val importadaEm: Long,
    val totalProdutos: String? = null,
    val descontos: String? = null,
    val acrescimos: String? = null,
    val valorPago: String? = null,
    val troco: String? = null,
    val quantidadeItensInformada: Int? = null
)
