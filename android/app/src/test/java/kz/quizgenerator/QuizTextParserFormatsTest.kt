package kz.quizgenerator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizTextParserFormatsTest {
    private fun parse(source: String) = QuizTextParser.parse(source.trimIndent(), "Тест")

    @Test fun formatATagsWithAnswerText() {
        val result = parse(
            """
            <question> Что изучает физика?
            <variant> Природные явления
            <variant> Только живые организмы
            <variant> Строение клетки
            Ответ: Природные явления
            """
        )
        val question = result.quiz.questions.single()
        assertEquals(3, question.options.size)
        assertEquals(0, question.correctIndex)
        assertEquals(AnswerEvidence.SOURCE, question.evidence)
    }

    @Test fun formatBNumberedQuestionsWithRussianLetters() {
        val result = parse(
            """
            1. Что такое электрический ток?
            А) Направленное движение зарядов
            Б) Колебание температуры
            В) Изменение массы

            2. Что такое напряжение?
            А) Работа тока
            Б) Разность потенциалов
            В) Сопротивление
            """
        )
        assertEquals(2, result.quiz.questions.size)
        assertEquals(listOf("Направленное движение зарядов", "Колебание температуры", "Изменение массы"),
            result.quiz.questions[0].options)
        assertNull(result.quiz.questions[0].correctIndex)
        assertEquals("Что такое напряжение?", result.quiz.questions[1].text)
    }

    @Test fun formatCEnglishQuestionAndLatinLetters() {
        val result = parse(
            """
            Question 1: What is voltage?
            A. Electric potential difference
            B. Electrical resistance
            C. Electric charge
            """
        )
        val question = result.quiz.questions.single()
        assertEquals(3, question.options.size)
        assertEquals("Electric potential difference", question.options[0])
        assertNull(question.correctIndex)
    }

    @Test fun formatDNumericOptionsDoNotBecomeQuestions() {
        val result = parse(
            """
            Вопрос 1. Столица Казахстана?
            1) Астана
            2) Алматы
            3) Шымкент
            4) Караганда

            Вопрос 2. Сколько дней в неделе?
            1) 5
            2) 6
            3) 7
            4) 8
            """
        )
        assertEquals(2, result.quiz.questions.size)
        assertEquals(4, result.quiz.questions[0].options.size)
        assertEquals("Астана", result.quiz.questions[0].options[0])
        assertEquals("8", result.quiz.questions[1].options[3])
    }

    @Test fun formatEUnlabeledOptionsAreImportedWithWarning() {
        val result = parse(
            """
            Что является единицей электрического сопротивления?

            Ом
            Вольт
            Ампер
            Ватт
            """
        )
        val question = result.quiz.questions.single()
        assertEquals(4, question.options.size)
        assertEquals("Ом", question.options[0])
        assertTrue(result.warnings.any { it.contains("без обозначений") })
    }

    @Test fun formatFAnswerBlockAtTheEnd() {
        val result = parse(
            """
            1. Что такое ток?
            А) Движение зарядов
            Б) Температура
            В) Масса

            2. Единица напряжения?
            А) Вольт
            Б) Ампер
            В) Ом

            Ответы:
            1 — А
            2 — А
            """
        )
        assertEquals(2, result.quiz.questions.size)
        assertEquals(0, result.quiz.questions[0].correctIndex)
        assertEquals(0, result.quiz.questions[1].correctIndex)
        assertTrue(result.quiz.questions.all { it.evidence == AnswerEvidence.SOURCE })
    }

    @Test fun answerBlockInsideDocumentAndMissingKeysArePreserved() {
        val result = parse(
            """
            1. Первый?
            А) Да
            Б) Нет
            Ответы: 1-Б

            2. Второй?
            А) Один
            Б) Два
            """
        )
        assertEquals(2, result.quiz.questions.size)
        assertEquals(1, result.quiz.questions[0].correctIndex)
        assertNull(result.quiz.questions[1].correctIndex)
        assertEquals(AnswerEvidence.UNKNOWN, result.quiz.questions[1].evidence)
    }

    @Test fun kazakhLettersAreRecognisedAsOptionLabels() {
        val result = parse(
            """
            Сұрақ 1: Қазақстанның астанасы?
            Ә) Алматы
            Ғ) Астана
            Қ) Шымкент
            Жауабы: Ғ
            """
        )
        val question = result.quiz.questions.single()
        assertEquals(3, question.options.size)
        assertEquals(1, question.correctIndex)
        assertEquals("Астана", question.options[1])
    }

    @Test fun latinAndCyrillicLookalikeLabelsResolve() {
        val result = parse(
            """
            Question 1: Capital of Kazakhstan?
            A) Astana
            B) Almaty
            C) Shymkent
            Answer: В
            """
        )
        assertEquals(1, result.quiz.questions.single().correctIndex)
    }

    @Test fun multilineOptionKeepsContinuation() {
        val result = parse(
            """
            1. Вопрос?
            А) Первая строка
            продолжение варианта
            Б) Другой вариант
            """
        )
        val question = result.quiz.questions.single()
        assertEquals("Первая строка продолжение варианта", question.options[0])
    }

    @Test fun multipleCorrectAnswersAreStoredSafely() {
        val result = parse(
            """
            1. Какие числа чётные?
            А) 2
            Б) 3
            В) 4
            Ответ: А, В
            """
        )
        val question = result.quiz.questions.single()
        assertEquals(listOf(0, 2), question.correctIndices)
        assertEquals(0, question.correctIndex)
        assertTrue(question.isMultiAnswer)
        // A strict multiple-answer key requires the complete set.
        assertEquals(QuizScore(0, 1, 0, 0), QuizScorer.score(result.quiz, listOf(0)))
    }

    @Test fun mixedTaggedAndNumberedFormats() {
        val result = parse(
            """
            1. Первый вопрос?
            A) One
            Б) Two
            <variant> Three
            Ответ: 2

            <question> Второй вопрос?
            <variant> Alpha
            <variant> Beta
            """
        )
        assertEquals(2, result.quiz.questions.size)
        assertEquals(1, result.quiz.questions[0].correctIndex)
        assertEquals(3, result.quiz.questions[0].options.size)
        assertNull(result.quiz.questions[1].correctIndex)
    }

    @Test fun ambiguousContinuationIsReportedInsteadOfInventingOptions() {
        val result = parse(
            """
            1. Выберите правильный ответ
            продолжение условия
            ещё продолжение
            А) Да
            Б) Нет
            """
        )
        val question = result.quiz.questions.single()
        assertEquals(2, question.options.size)
        assertTrue(question.text.contains("продолжение условия"))
        assertTrue(result.warnings.any { it.contains("не удалось надёжно отделить") })
    }

    @Test fun duplicateQuestionsAreRemovedAndReported() {
        val result = parse(
            """
            1. Один и тот же вопрос?
            А) Да
            Б) Нет

            2. Один и тот же вопрос?
            А) Да
            Б) Нет
            """
        )
        assertEquals(1, result.quiz.questions.size)
        assertTrue(result.warnings.any { it.contains("повторяющихся") })
    }

    @Test fun twelveOptionsAndNoKeysArePreserved() {
        val source = buildString {
            appendLine("1. Вопрос с двенадцатью вариантами?")
            for (i in 1..12) appendLine("${('A'.code + i - 1).toChar()}) Вариант $i")
        }
        val question = QuizTextParser.parse(source, "Тест").quiz.questions.single()
        assertEquals(12, question.options.size)
        assertEquals("Вариант 12", question.options.last())
        assertNull(question.correctIndex)
        assertNotNull(question.id)
    }
}
