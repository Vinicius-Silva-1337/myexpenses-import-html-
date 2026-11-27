package br.com.feira.nfce.notas.parser

object Chave {
    fun digitos(chave: String): String = chave.filter(Char::isDigit)

    fun validar(chave: String): Boolean {
        val digits = digitos(chave)
        if (digits.length != 44) return false
        val base = digits.dropLast(1)
        var peso = 2
        var soma = 0
        for (digit in base.reversed()) {
            soma += digit.digitToInt() * peso
            peso = if (peso == 9) 2 else peso + 1
        }
        val resto = soma % 11
        val dv = if (resto < 2) 0 else 11 - resto
        return dv == digits.last().digitToInt()
    }

    fun decompor(chave: String): Map<String, String>? {
        val value = digitos(chave)
        if (value.length != 44) return null
        return mapOf(
            "uf" to value.substring(0, 2),
            "aamm" to value.substring(2, 6),
            "cnpj" to value.substring(6, 20),
            "modelo" to value.substring(20, 22),
            "serie" to value.substring(22, 25).toInt().toString(),
            "numero" to value.substring(25, 34).toIntOrNull()?.toString().orEmpty(),
            "tipoEmissao" to value.substring(34, 35),
            "codigoNumerico" to value.substring(35, 43),
            "dv" to value.substring(43)
        )
    }
}
