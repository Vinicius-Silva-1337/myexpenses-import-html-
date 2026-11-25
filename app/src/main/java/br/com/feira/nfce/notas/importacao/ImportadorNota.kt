package br.com.feira.nfce.notas.importacao

import br.com.feira.nfce.notas.model.NotaFiscal
import br.com.feira.nfce.notas.parser.ParserAm
import br.com.feira.nfce.notas.parser.ParserMhtml

object ImportadorNota {
    fun parse(bytes: ByteArray, displayName: String? = null): NotaFiscal {
        val isMhtml = displayName?.substringAfterLast('.', "")?.lowercase() == "mhtml" ||
            displayName?.substringAfterLast('.', "")?.lowercase() == "mht" ||
            String(bytes.take(256).toByteArray(), Charsets.ISO_8859_1).contains("multipart/", true)
        return ParserAm.parseHtml(if (isMhtml) ParserMhtml.extrairHtml(bytes) else br.com.feira.nfce.notas.parser.Decodificador.decodificar(bytes))
    }
}
