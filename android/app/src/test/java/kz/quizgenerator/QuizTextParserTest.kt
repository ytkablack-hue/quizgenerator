package kz.quizgenerator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizTextParserTest {
    @Test fun parsesRussianLettersAndExplicitAnswer() {
        val source = """
            1. Единица измерения силы тока?
            А) Ампер
            Б) Вольт
            В) Ом
            Ответ: А
            Пояснение: Сила тока измеряется в амперах.

            2. Выбери число.
            А) Один
            Б) Два
        """.trimIndent()
        val result = QuizTextParser.parse(source, "Физика")
        assertEquals(2, result.quiz.questions.size)
        assertEquals(0, result.quiz.questions[0].correctIndex)
        assertEquals("Сила тока измеряется в амперах.", result.quiz.questions[0].explanation)
        assertNull(result.quiz.questions[1].correctIndex)
    }

    @Test fun preservesKazakhAndSupportsMarkedOption() {
        val source = """
            Сұрақ 1: Қазақстанның астанасы?
            A) Алматы
            *B) Астана
            C) Көкшетау
        """.trimIndent()
        val result = QuizTextParser.parse(source, "Қазақша")
        assertEquals(1, result.quiz.questions[0].correctIndex)
        assertEquals("Көкшетау", result.quiz.questions[0].options[2])
    }

    @Test fun unresolvedAnswerIsNeverGuessed() {
        val result = QuizTextParser.parse("""
            1) О каком свойстве идёт речь?
            A. Плотность
            B. Объём
            Ответ: Z
        """.trimIndent(), "Без ключа")
        assertNull(result.quiz.questions.single().correctIndex)
        assertTrue(result.warnings.any { it.contains("не распознан") })
    }

    @Test fun malformedQuestionIsSkippedAndReported() {
        val result = QuizTextParser.parse("""
            1. Нет вариантов?
            A) Да
            2. Нормальный вопрос?
            А) Да
            Б) Нет
        """.trimIndent(), "Пример")
        assertEquals(1, result.quiz.questions.size)
        assertTrue(result.warnings.any { it.contains("пропущен") })
    }

    @Test fun rejectsFileWithoutRecognizableQuestions() {
        assertThrows(IllegalArgumentException::class.java) {
            QuizTextParser.parse("Просто длинный конспект без тестовых вопросов", "Конспект")
        }
    }
}
