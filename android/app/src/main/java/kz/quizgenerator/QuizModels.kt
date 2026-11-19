package kz.quizgenerator

data class QuizQuestion(
    val id: String,
    val text: String,
    val options: List<String>,
    val correctIndex: Int?,
    val explanation: String?,
    val sourceExcerpt: String? = null
)

data class Quiz(
    val id: String,
    val title: String,
    val questions: List<QuizQuestion>,
    val generatedByAi: Boolean = false
)

data class QuizScore(
    val correct: Int,
    val incorrect: Int,
    val skipped: Int,
    val ungraded: Int
) {
    val gradedTotal: Int get() = correct + incorrect + skipped
}

object QuizScorer {
    /** A question with no verified answer key must never be marked right or wrong. */
    fun score(quiz: Quiz, answers: List<Int?>): QuizScore {
        require(answers.size == quiz.questions.size) { "Ответов должно быть столько же, сколько вопросов" }
        var correct = 0
        var incorrect = 0
        var skipped = 0
        var ungraded = 0

        quiz.questions.forEachIndexed { index, question ->
            when {
                question.correctIndex == null -> ungraded++
                answers[index] == null -> skipped++
                answers[index] == question.correctIndex -> correct++
                else -> incorrect++
            }
        }

        return QuizScore(correct, incorrect, skipped, ungraded)
    }
}
