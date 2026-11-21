package kz.quizgenerator

import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

internal data class StudySettings(
    val limit: Int = 0,
    val shuffleQuestions: Boolean = false,
    val shuffleOptions: Boolean = false,
    val showHints: Boolean = true,
    val showAnswers: Boolean = true,
    val minutes: Int = 0,
    val dark: Boolean = false,
    val server: String = ProductionConfig.SERVER,
    val accessToken: String = "",
    val theme: String = if (dark) "dark" else "system"
)

internal data class Flashcard(
    val id: String, val front: String, val back: String, val generatedByAi: Boolean = false,
    val reviews: Int = 0, val lastRating: String? = null, val sourceKey: String? = null,
    val sourceQuizId: String? = null, val evidence: AnswerEvidence = if (generatedByAi) AnswerEvidence.INFERRED else AnswerEvidence.MANUAL
)
internal data class Exam(val id: String, val title: String, val date: LocalDate, val topics: List<String>, val quizIds: List<String>)
internal data class Mistake(val question: String, val given: String?, val correct: String, val explanation: String?)
internal data class Attempt(
    val id: String,
    val quizId: String,
    val title: String,
    val completedAt: Long,
    val score: QuizScore,
    val mistakes: List<Mistake>
)
internal data class StudyData(
    val cards: List<Flashcard> = emptyList(),
    val exams: List<Exam> = emptyList(),
    val attempts: List<Attempt> = emptyList(),
    val settings: StudySettings = StudySettings()
)
internal data class StudyDay(val date: LocalDate, val topic: String)

internal object StudyLogic {
    fun manualQuiz(title: String, questions: List<QuizQuestion>): Quiz {
        require(title.isNotBlank()) { "Введите название теста" }
        require(questions.size in 1..QuizLimits.QUESTIONS) { "Добавьте от 1 до 5000 вопросов" }
        return Quiz(UUID.randomUUID().toString(), title.trim(), questions.map {
            require(it.text.isNotBlank() && it.options.size in 2..QuizLimits.OPTIONS && it.options.none(String::isBlank)) {
                "Вопросу нужны текст и 2–64 непустых варианта"
            }
            require(it.correctIndex == null || it.correctIndex in it.options.indices) { "Неверный ключ" }
            it.copy(text = it.text.trim(), options = it.options.map(String::trim))
        })
    }

    fun prepare(quiz: Quiz, settings: StudySettings, seed: Long): Quiz {
        val random = Random(seed)
        val ordered = if (settings.shuffleQuestions) quiz.questions.shuffled(random) else quiz.questions
        val selected = if (settings.limit > 0) ordered.take(settings.limit) else ordered
        return quiz.copy(questions = if (settings.shuffleOptions) selected.map { question ->
            val order = question.options.indices.shuffled(random)
            question.copy(
                options = order.map(question.options::get),
                correctIndex = question.correctIndex?.let(order::indexOf)
            )
        } else selected)
    }


    fun complete(id: String, quiz: Quiz, answers: List<Int?>, now: Long): Attempt {
        val score = QuizScorer.score(quiz, answers)
        val mistakes = quiz.questions.mapIndexedNotNull { index, question ->
            val correct = question.correctIndex.takeIf { question.evidence in setOf(AnswerEvidence.SOURCE, AnswerEvidence.MANUAL) }
            if (correct == null || answers[index] == correct) null else Mistake(
                question.text, answers[index]?.let(question.options::getOrNull),
                question.options[correct], question.explanation
            )
        }
        return Attempt(id, quiz.id, quiz.title, now, score, mistakes)
    }

    fun plan(exam: Exam, quizzes: List<Quiz>, attempts: List<Attempt>, today: LocalDate): List<StudyDay> {
        if (exam.date.isBefore(today)) return emptyList()
        val linked = quizzes.filter { it.id in exam.quizIds }
        val weak = linked.sortedByDescending { quiz ->
            val recent = attempts.lastOrNull { it.quizId == quiz.id }?.score
            if (recent == null || recent.gradedTotal == 0) 0.0
            else (recent.incorrect + recent.skipped).toDouble() / recent.gradedTotal
        }.map(Quiz::title)
        val topics = (weak + exam.topics).distinct()
        val days = (exam.date.toEpochDay() - today.toEpochDay()).coerceAtMost(30)
        return topics.mapIndexed { index, topic ->
            StudyDay(today.plusDays(days * index / topics.size), topic)
        }
    }
}
