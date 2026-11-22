package kz.quizgenerator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class TextEncodingTest {
    private val russian = """
        1. Столица Казахстана?
        А) Астана
        Б) Алматы
        Ответ: А
    """.trimIndent()

    private val kazakh = """
        Сұрақ 1: Қазақстанның астанасы?
        Ә) Алматы
        Ғ) Астана
        Жауабы: Ғ
    """.trimIndent()

    private fun assertParses(text: String) {
        val quiz = QuizTextParser.parse(text, "Тест").quiz
        assertEquals(1, quiz.questions.size)
        assertEquals("Астана", quiz.questions.single().options[0])
    }

    @Test fun utf8AndBom() {
        val plain = TextDecoder.decode(russian.toByteArray(Charsets.UTF_8))
        assertEquals("UTF-8", plain.charset)
        assertParses(plain.text)

        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + russian.toByteArray(Charsets.UTF_8)
        val decoded = TextDecoder.decode(bom)
        assertEquals("UTF-8", decoded.charset)
        assertTrue(!decoded.text.startsWith("\uFEFF"))
        assertParses(decoded.text)
    }

    @Test fun utf16LittleEndianWithBom() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + russian.toByteArray(Charsets.UTF_16LE)
        val decoded = TextDecoder.decode(bytes)
        assertEquals("UTF-16LE", decoded.charset)
        assertParses(decoded.text)
    }

    @Test fun utf16BigEndianWithBom() {
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + russian.toByteArray(Charsets.UTF_16BE)
        val decoded = TextDecoder.decode(bytes)
        assertEquals("UTF-16BE", decoded.charset)
        assertParses(decoded.text)
    }

    @Test fun utf16LittleEndianWithoutBomIsDetected() {
        val bytes = russian.toByteArray(Charsets.UTF_16LE)
        val decoded = TextDecoder.decode(bytes)
        assertEquals("UTF-16LE", decoded.charset)
        assertParses(decoded.text)
    }

    @Test fun windows1251RussianAndKazakh() {
        val cp1251 = Charset.forName("windows-1251")
        val russianDecoded = TextDecoder.decode(russian.toByteArray(cp1251))
        assertEquals("Windows-1251", russianDecoded.charset)
        assertParses(russianDecoded.text)

        // Windows-1251 has no Kazakh-specific letters, so such a file is lossy by
        // definition. The decoder must stay deterministic instead of crashing.
        val kazakhDecoded = TextDecoder.decode(kazakh.toByteArray(cp1251))
        assertEquals("Windows-1251", kazakhDecoded.charset)
        assertTrue(kazakhDecoded.text.none { it == '\uFFFD' })
    }

    @Test fun kazakhLettersSurviveUtf8AndUtf16() {
        for (charset in listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE)) {
            val decoded = TextDecoder.decode(kazakh.toByteArray(charset))
            val quiz = QuizTextParser.parse(decoded.text, "Қазақша").quiz
            assertEquals(1, quiz.questions.single().correctIndex)
            assertEquals("Астана", quiz.questions.single().options[1])
        }
    }

    @Test fun forcedCharsetOverridesDetection() {
        val bytes = russian.toByteArray(Charset.forName("windows-1251"))
        val decoded = TextDecoder.decode(bytes, "Windows-1251")
        assertEquals("Windows-1251", decoded.charset)
        assertParses(decoded.text)
    }

    @Test fun brokenUtf8IsNotSilentlyReplaced() {
        val bytes = byteArrayOf(0xD0.toByte(), 0x3F.toByte(), 0x41.toByte())
        val decoded = TextDecoder.decode(bytes)
        // The decoder must return a deterministic fallback, never a string with replacement chars.
        assertTrue(decoded.text.none { it == '\uFFFD' })
    }
}
