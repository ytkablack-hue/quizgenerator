package kz.quizgenerator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class StudyLogicTest {
    @get:Rule val folder = TemporaryFolder()

    private val quiz = Quiz("physics", "Физика", listOf(
        QuizQuestion("q1", "Вопрос 1", listOf("Ом", "Ампер", "Вольт"), 1, "Пояснение"),
        QuizQuestion("q2", "Вопрос 2", listOf("Да", "Нет"), null, null)
    ))

    @Test fun shufflingDoesNotChangeCorrectAnswerAndIsRepeatable() {
        val settings = StudySettings(shuffleQuestions = true, shuffleOptions = true)
        val prepared = StudyLogic.prepare(quiz, settings, 17)
        assertEquals(prepared, StudyLogic.prepare(quiz, settings, 17))
        assertEquals(listOf("Ампер"), prepared.questions.mapNotNull { it.correctIndex?.let(it.options::get) })
        assertNull(prepared.questions.first { it.id == "q2" }.correctIndex)
        assertEquals(1, StudyLogic.prepare(quiz, settings.copy(limit = 1), 17).questions.size)
    }

    @Test fun invalidManualQuestionsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            StudyLogic.manualQuiz("Физика", listOf(quiz.questions.first().copy(options = listOf("Ом", ""))))
        }
        assertEquals("Физика", StudyLogic.manualQuiz("  Физика  ", quiz.questions).title)
    }

    @Test fun savesAndReloadsAttemptCardsExamsAndPreferences() {
        val score = StudyLogic.complete("attempt", quiz, listOf(0, null), 123)
        assertEquals(QuizScore(0, 1, 0, 1), score.score)
        assertEquals(Mistake("Вопрос 1", "Ом", "Ампер", "Пояснение"), score.mistakes.single())
        val data = StudyData(
            listOf(Flashcard("card", "Единица тока?", "Ампер")),
            listOf(Exam("exam", "Экзамен", LocalDate.of(2026, 12, 5), listOf("Ток"), listOf(quiz.id))),
            listOf(score),
            StudySettings(minutes = 20, shuffleOptions = true, dark = true)
        )
        val file = folder.root.resolve("study.json")
        assertEquals(StudyData(), StudyStore(file).load())
        StudyStore(file).save(data)
        val updated = data.copy(cards = data.cards.map { it.copy(back = "А") })
        StudyStore(file).save(updated)
        assertEquals(updated, StudyStore(file).load())
        assertTrue(folder.root.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    @Test fun planPrioritizesWeakLinkedMaterialAndStaysBeforeExam() {
        val today = LocalDate.of(2026, 11, 18)
        val easy = quiz.copy(id = "easy", title = "Простой материал")
        val exam = Exam("id", "Физика", today.plusDays(5), listOf("Ток"), listOf(easy.id, quiz.id))
        val attempts = listOf(StudyLogic.complete("id", quiz, listOf(0, null), 1))
        val plan = StudyLogic.plan(exam, listOf(easy, quiz), attempts, today)
        assertEquals(quiz.title, plan.first().topic)
        assertTrue(plan.all { it.date >= today && it.date <= exam.date })
        assertTrue(StudyLogic.plan(exam.copy(date = today.minusDays(1)), listOf(quiz), attempts, today).isEmpty())
    }
}
