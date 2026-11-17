package kz.quizgenerator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QuizScorerTest {
    private val quiz = Quiz(
        id = "test", title = "Проверка", questions = listOf(
            QuizQuestion("one", "Первый", listOf("Да", "Нет"), 0, null),
            QuizQuestion("two", "Второй", listOf("Да", "Нет"), 1, null),
            QuizQuestion("unknown", "Без ключа", listOf("Да", "Нет"), null, null)
        )
    )

    @Test fun countsCorrectIncorrectAndUngrading() {
        assertEquals(QuizScore(1, 1, 0, 1), QuizScorer.score(quiz, listOf(0, 0, 1)))
    }

    @Test fun countsSkippedSeparately() {
        assertEquals(QuizScore(1, 0, 1, 1), QuizScorer.score(quiz, listOf(0, null, null)))
    }

    @Test fun excludesQuestionsWithoutAnswerKey() {
        val result = QuizScorer.score(quiz, listOf(0, 1, 0))
        assertEquals(2, result.gradedTotal)
        assertEquals(1, result.ungraded)
    }

    @Test fun rejectsAnswerLengthMismatch() {
        assertThrows(IllegalArgumentException::class.java) {
            QuizScorer.score(quiz, listOf(0))
        }
    }
}
