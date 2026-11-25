package br.com.feira.nfce.notas.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "notas_fiscais", indices = [Index(value = ["chave"], unique = true)])
data class NotaFiscalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chave: String,
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
    val importadaEm: Long
)
