package br.com.feira.nfce.notas.parser

import java.math.BigDecimal

object Numeros {
    private val decimal = Regex("^[+-]?(?:\\d{1,3}(?:\\.\\d{3})+|\\d+)(?:,\\d+|\\.\\d+)?$")

    fun brasileiro(texto: String): BigDecimal {
        val limpo = texto.trim().replace("R$", "", ignoreCase = true)
            .replace("\u00a0", "").replace(" ", "")
        require(decimal.matches(limpo)) { "Número inválido" }
        val normalizado = if (',' in limpo) limpo.replace(".", "").replace(',', '.') else limpo
        return BigDecimal(normalizado)
    }
}
