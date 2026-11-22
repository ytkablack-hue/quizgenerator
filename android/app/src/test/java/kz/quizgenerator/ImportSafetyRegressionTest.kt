package kz.quizgenerator

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset

class ImportSafetyRegressionTest {
    @Test fun forcedAndBomDecodingNeverReplaceBrokenCharacters() {
        assertThrows(IllegalArgumentException::class.java) { TextDecoder.decode(byteArrayOf(0x98.toByte(), 0)) }
        val bad = byteArrayOf(0xc3.toByte(), 0x28)
        assertThrows(IllegalArgumentException::class.java) { TextDecoder.decode(bad, "UTF-8") }
        assertThrows(IllegalArgumentException::class.java) { TextDecoder.decode(byteArrayOf(0xef.toByte(),0xbb.toByte(),0xbf.toByte()) + bad) }
        for (bom in listOf(byteArrayOf(0xff.toByte(),0xfe.toByte()), byteArrayOf(0xfe.toByte(),0xff.toByte()))) {
            assertThrows(IllegalArgumentException::class.java) { TextDecoder.decode(bom + byteArrayOf(0x41)) }
        }
        assertThrows(IllegalArgumentException::class.java) { TextDecoder.decode(bad, "not-a-charset") }
    }
    @Test fun cyrillicUtf16WhichIsAlsoValidUtf8IsNotCorrupted() {
        for (charset in listOf(Charsets.UTF_16LE, Charsets.UTF_16BE)) {
            val text = "АБВГДЕ"
            assertEquals(text, TextDecoder.decode(text.toByteArray(charset)).text)
        }
    }
    @Test fun legacyDetectionRequiresReview() {
        val text = "Что такое ток? Это движение заряженных частиц"
        val decoded = TextDecoder.decode(text.toByteArray(Charset.forName("windows-1251")))
        assertEquals(text, decoded.text)
        assertFalse(decoded.confident)
        assertEquals(text, TextDecoder.decode(text.toByteArray(Charset.forName("windows-1251")), "Windows-1251").text)
    }
    @Test fun keysBeforeQuestionsAreApplied() {
        val parsed = QuizTextParser.parse("Ответы:\n1:Б\n\n1. Вопрос?\nА) Нет\nБ) Да", "Тест")
        assertEquals(1, parsed.quiz.questions.single().correctIndex)
    }
    @Test fun partialAndConflictingKeysNeverBecomeConfirmed() {
        for (suffix in listOf("Ответ: А,Z", "Ответ: А\nОтветы: 1-Б")) {
            val q = QuizTextParser.parse("1. Вопрос?\nА) Да\nБ) Нет\n" + suffix,"Тест").quiz.questions.single()
            assertEquals(AnswerEvidence.INFERRED,q.evidence)
            assertEquals(QuizScore(0,0,0,1),QuizScorer.score(Quiz("x","x",listOf(q)),listOf(0)))
        }
    }
    @Test fun duplicateLabelsDoNotSilentlyChooseFirstAnswer() {
        val p=QuizTextParser.parse("1. Вопрос?\nА) Да\nА) Нет\nОтвет: А", "Тест")
        assertNull(p.quiz.questions.single().correctIndex)
        assertTrue(p.warnings.isNotEmpty())
    }
    @Test fun jsonMultiKeysRejectCoercionAndConflicts() {
        for (key in listOf("[1.5]","[\"1\"]","[true]","[1]")) {
            val json="""{"id":"q","title":"t","questions":[{"id":"1","text":"x","options":["a","b"],"correctIndex":0,"correctIndices":$key}]}"""
            assertThrows(IllegalArgumentException::class.java) { QuizJson.parse(json) }
        }
    }
}
