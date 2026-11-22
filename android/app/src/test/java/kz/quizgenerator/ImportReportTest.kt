package kz.quizgenerator
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class ImportReportTest {
    @Test fun skippedFragmentAndDuplicateSurviveSaving() {
        val text="1. Сломанный вопрос?\nА) Только один\n2. Хороший вопрос?\nА) Да\nБ) Нет\n3. Хороший вопрос?\nА) Да\nБ) Нет"
        val quiz=QuizTextParser.parse(text,"Импорт").quiz
        assertEquals(1,quiz.questions.size)
        assertEquals(2,quiz.importProblems.size)
        assertTrue(quiz.importProblems.first().source.contains("Только один"))
        assertTrue(quiz.importProblems.last().source.startsWith("3."))
        assertEquals(quiz,QuizJson.parse(QuizJson.serialize(quiz)))
    }
    @Test fun longRawFragmentUsesBoundedJsonPartsWithoutTruncation() {
        val q=QuizQuestion("q","Текст",listOf("Да","Нет"),null,null)
        val quiz=Quiz("q","Импорт",listOf(q),importProblems=listOf(ImportProblem(1,"Проверить","я".repeat(150_000))))
        assertEquals(quiz,QuizJson.parse(QuizJson.serialize(quiz)))
    }
    @Test fun cancellationIsCheckedEvenForSmallImports() {
        assertThrows(CancellationException::class.java) {
            QuizTextParser.parseFrom("Тест", { throw CancellationException() }) { emit -> emit("1. Вопрос?") }
        }
    }
}
