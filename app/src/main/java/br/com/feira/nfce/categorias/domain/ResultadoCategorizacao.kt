package br.com.feira.nfce.categorias.domain

data class ResultadoCategorizacao(
    val categoriaId: Long?,
    val categoriaNome: String,
    val identificado: Boolean,
    val origem: String,
    val termoEncontrado: String? = null,
    val ambiguo: Boolean = false
)
