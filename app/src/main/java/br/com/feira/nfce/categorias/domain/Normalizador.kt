package br.com.feira.nfce.categorias.domain

import java.text.Normalizer
import java.util.Locale

object Normalizador {
    fun normalizar(texto: String): String = Normalizer.normalize(texto.trim().uppercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^A-Z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}
