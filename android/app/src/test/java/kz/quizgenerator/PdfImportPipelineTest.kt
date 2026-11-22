package kz.quizgenerator
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class PdfImportPipelineTest {
    @Test fun streamsTextPagesToExistingQuestionParser() {
        val quiz=QuizTextParser.parseFrom("PDF") { emit ->
            PdfTextPages.consume(2, {}, { p -> listOf("Вопрос "+(p+1)+". Единица длины на странице "+(p+1)+"?\nА) Метр\nБ) Килограмм") }, emit)
        }.quiz
        assertEquals(2,quiz.questions.size)
        assertTrue(quiz.questions.all { it.options.size==2 && it.correctIndex==null })
    }
    @Test fun scansAndMixedPagesDoNotBecomeSilentPartialImports() {
        val error=assertThrows(IllegalArgumentException::class.java) {
            PdfTextPages.consume(2, {}, { p -> if(p==0) listOf("Текст") else emptyList() }, {})
        }
        assertTrue(error.message!!.contains("Страница 2"))
    }
    @Test fun boundsAndCancellationAreEnforced() {
        for(count in listOf(0,5001)) assertThrows(IllegalArgumentException::class.java) { PdfTextPages.consume(count, {}, { emptyList() }, {}) }
        assertThrows(CancellationException::class.java) { PdfTextPages.consume(1, { throw CancellationException() }, { error("Should not read") }, {}) }
        assertThrows(IllegalArgumentException::class.java) { PdfTextPages.consume(1, {}, { listOf("a".repeat(8_000_001)) }, {}) }
    }
    @Test fun separateUnlabelledQuestionBlocksAreCandidatesWithPreservedSource() {
        val q=QuizTextParser.parse("Что измеряют в метрах?\nДлину\nМассу\n\nЧто измеряют в килограммах?\nМассу\nВремя","Тест").quiz
        assertEquals(2,q.questions.size);assertEquals(2,q.importProblems.size)
        assertTrue(q.questions.all { it.correctIndex==null })
    }
    @Test fun wordWrapIsConservativeAndSourceIsPreserved() {
        val soft = QuizTextParser.parse("1. Вопрос?\nА) элек\u00ad\nтрический\nБ) Другое", "Тест").quiz
        assertEquals("электрический", soft.questions.single().options.first())
        val hard = QuizTextParser.parse("1. Вопрос?\nА) научно-\nтехнический\nБ) Другое", "Тест").quiz
        assertEquals("научно-технический", hard.questions.single().options.first())
        assertTrue(hard.importProblems.single().source.contains("научно-\nтехнический"))
    }
}
