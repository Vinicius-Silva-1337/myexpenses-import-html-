package br.com.feira.nfce.notas.parser

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Base64

object ParserMhtml {
    fun extrairHtml(bytes: ByteArray): String {
        val raw = Decodificador.decodificar(bytes)
        val boundary = Regex("(?im)^Content-Type:\\s*multipart/[^;]+;[^\\r\\n]*boundary=\"?([^\";\\r\\n]+)").find(raw)?.groupValues?.get(1)
        if (boundary == null) {
            if (raw.contains("<html", true) || raw.contains("<!doctype", true)) return raw
            throw ParserNfceException("mhtml_sem_html", "O arquivo MHTML não contém uma parte HTML.")
        }
        val parts = raw.substringAfter("\r\n\r\n", raw.substringAfter("\n\n", ""))
            .split("--$boundary")
        val htmlPart = parts.firstOrNull { part ->
            part.substringBefore("\r\n\r\n", part.substringBefore("\n\n", ""))
                .contains("Content-Type: text/html", true)
        } ?: throw ParserNfceException("mhtml_sem_html", "O arquivo MHTML não contém uma parte HTML.")
        val separator = if ("\r\n\r\n" in htmlPart) "\r\n\r\n" else "\n\n"
        val header = htmlPart.substringBefore(separator)
        val body = htmlPart.substringAfter(separator, "").trimEnd('\r', '\n', '-')
        val charsetName = Regex("(?i)charset=\"?([^\";\\s]+)").find(header)?.groupValues?.get(1) ?: "utf-8"
        val charset = runCatching { Charset.forName(charsetName) }.getOrDefault(StandardCharsets.UTF_8)
        val decoded = when {
            header.contains("Content-Transfer-Encoding: base64", true) -> runCatching { Base64.getMimeDecoder().decode(body).toString(charset) }.getOrElse {
                throw ParserNfceException("mhtml_invalido", "Não foi possível decodificar o conteúdo MHTML.")
            }
            header.contains("Content-Transfer-Encoding: quoted-printable", true) -> quotedPrintable(body.toByteArray(StandardCharsets.ISO_8859_1)).toString(charset)
            else -> body
        }
        return decoded
    }

    private fun quotedPrintable(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(input.size)
        var i = 0
        while (i < input.size) {
            val current = input[i].toInt() and 0xff
            if (current == '='.code && i + 1 < input.size) {
                if (input[i + 1] == '\r'.code.toByte() && i + 2 < input.size && input[i + 2] == '\n'.code.toByte()) {
                    i += 3
                    continue
                }
                if (input[i + 1] == '\n'.code.toByte()) {
                    i += 2
                    continue
                }
                if (i + 2 < input.size) {
                    val hex = String(input, i + 1, 2, StandardCharsets.US_ASCII).toIntOrNull(16)
                    if (hex != null) {
                        out.write(hex)
                        i += 3
                        continue
                    }
                }
            }
            out.write(current)
            i++
        }
        return out.toByteArray()
    }
}
