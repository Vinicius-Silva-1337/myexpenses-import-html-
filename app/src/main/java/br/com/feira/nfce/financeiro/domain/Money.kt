package br.com.feira.nfce.financeiro.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object Money {
    fun parseSignedCents(raw: String): Long? = runCatching {
        var value=raw.trim().replace("R$", "").replace(" ", "")
        if(',' in value) value=value.replace(".", "").replace(',', '.')
        BigDecimal(value).setScale(2,RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    }.getOrNull()
    fun parseCents(raw:String):Long?=parseSignedCents(raw)?.takeIf{it>0}
    fun decimal(cents:Long)=BigDecimal.valueOf(cents,2)
    fun format(cents:Long):String {
        val formatter=NumberFormat.getNumberInstance(Locale("pt","BR")).apply { minimumFractionDigits=2;maximumFractionDigits=2;isGroupingUsed=true }
        return "R$ ${formatter.format(decimal(cents))}"
    }
}
