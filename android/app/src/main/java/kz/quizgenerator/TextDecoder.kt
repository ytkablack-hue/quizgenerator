package kz.quizgenerator

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale

internal data class DecodedText(val text: String, val charset: String, val confident: Boolean)

/**
 * Detects the encodings produced by Word and Windows Notepad without silently
 * replacing broken characters. UTF-8 is tried strictly first; legacy single-byte
 * encodings are ranked by how much readable Russian/Kazakh they produce.
 */
internal object TextDecoder {
    val choices = listOf("UTF-8", "UTF-16LE", "UTF-16BE", "Windows-1251")

    private val commonWords = listOf(
        "что", "это", "как", "для", "или", "при", "что", "если", "чем", "его",
        "она", "они", "быть", "может", "все", "этот", "эта", "были", "будет",
        "және", "бұл", "үшін", "мен", "қала", "болып", "деп", "бар", "жоқ"
    )

    fun decode(bytes: ByteArray, forced: String? = null): DecodedText {
        if (bytes.isEmpty()) return DecodedText("", "UTF-8", true)
        if (forced != null) {
            val charset = charsetFor(forced)
            val text = strict(bytes, 0, bytes.size, charset) ?: String(bytes, charset)
            return DecodedText(stripBom(text), forced, true)
        }

        when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                return DecodedText(decodeStrict(bytes, 3, "UTF-8"), "UTF-8", true)
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                return DecodedText(decodeStrict(bytes, 2, "UTF-16LE"), "UTF-16LE", true)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                return DecodedText(decodeStrict(bytes, 2, "UTF-16BE"), "UTF-16BE", true)
        }

        val utf8 = strict(bytes, 0, bytes.size, StandardCharsets.UTF_8)
        if (utf8 != null && utf8.none { it == '\u0000' }) {
            return DecodedText(stripBom(utf8), "UTF-8", true)
        }

        val utf16 = guessUtf16(bytes)
        if (utf16 != null) return utf16

        val names = mutableListOf("Windows-1251", "KOI8-R")
        // UTF-16 Cyrillic text has 0x04 as the high (LE) or low (BE) byte, so it
        // rarely triggers the ASCII zero-byte heuristic above.
        if (bytes.size % 2 == 0 && bytes.count { it == 0x04.toByte() } > bytes.size / 20) {
            names += listOf("UTF-16LE", "UTF-16BE")
        }
        val candidates = names.map { name ->
            val text = String(bytes, charsetFor(name))
            name to (text to score(text))
        }.sortedByDescending { it.second.second }
        val best = candidates.first()
        val second = candidates.getOrNull(1)
        val confident = best.second.second > 0.35 &&
            (second == null || best.second.second - second.second.second > 0.08)
        return DecodedText(stripBom(best.second.first), best.first, confident)
    }

    fun charsetFor(name: String): Charset = when (name) {
        "UTF-16LE" -> StandardCharsets.UTF_16LE
        "UTF-16BE" -> StandardCharsets.UTF_16BE
        "Windows-1251" -> charset("windows-1251")
        "KOI8-R" -> charset("KOI8-R")
        else -> StandardCharsets.UTF_8
    }

    private fun charset(name: String): Charset =
        runCatching { Charset.forName(name) }.getOrDefault(StandardCharsets.UTF_8)

    private fun strict(bytes: ByteArray, offset: Int, length: Int, charset: Charset): String? =
        runCatching {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, length))
                .toString()
        }.getOrNull()

    private fun decodeStrict(bytes: ByteArray, offset: Int, name: String): String =
        strict(bytes, offset, bytes.size - offset, charsetFor(name)) ?: ""

    private fun guessUtf16(bytes: ByteArray): DecodedText? {
        if (bytes.size < 4 || bytes.size % 2 != 0) return null
        var evenZero = 0
        var oddZero = 0
        for (index in bytes.indices) {
            if (bytes[index] == 0.toByte()) {
                if (index % 2 == 0) evenZero++ else oddZero++
            }
        }
        val pairs = bytes.size / 2
        val name = when {
            oddZero > pairs * 0.6 && evenZero < pairs * 0.1 -> "UTF-16LE"
            evenZero > pairs * 0.6 && oddZero < pairs * 0.1 -> "UTF-16BE"
            else -> return null
        }
        val text = strict(bytes, 0, bytes.size, charsetFor(name)) ?: return null
        return DecodedText(stripBom(text), name, true)
    }

    private fun stripBom(text: String): String = text.removePrefix("\uFEFF")

    private fun score(text: String): Double {
        val lower = " ${text.lowercase(Locale.ROOT)} "
        var hits = 0
        for (word in commonWords) {
            var from = 0
            while (true) {
                val at = lower.indexOf(word, from)
                if (at < 0) break
                hits += if (word.length >= 3) 3 else 1
                from = at + word.length
            }
        }
        var letters = 0
        var cyrillic = 0
        var controls = 0
        for (char in text) {
            when {
                char.isLetter() -> {
                    letters++
                    if (char in '\u0400'..'\u04FF') cyrillic++
                }
                char.isISOControl() && char != '\n' && char != '\r' && char != '\t' -> controls++
            }
        }
        if (letters == 0) return -1.0
        val ratio = cyrillic.toDouble() / letters
        return ratio + hits * 0.02 - controls * 0.2
    }
}
