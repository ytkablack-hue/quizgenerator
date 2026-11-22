package kz.quizgenerator

data class QuizQuestion(
    val id: String,
    val text: String,
    val options: List<String>,
    val correctIndex: Int?,
    val explanation: String?,
    val sourceExcerpt: String? = null,
    val evidence: AnswerEvidence = if (correctIndex == null) AnswerEvidence.UNKNOWN else AnswerEvidence.SOURCE,
    /**
     * Extra correct options for multiple-answer questions. [correctIndex] stays the primary
     * (first) answer so existing single-answer code keeps working. Never store an index here
     * that is already equal to [correctIndex].
     */
    val additionalCorrectIndices: List<Int> = emptyList()
) {
    /** Authoritative answer key. Single-answer questions yield exactly one element. */
    val correctIndices: List<Int>
        get() = (listOfNotNull(correctIndex) + additionalCorrectIndices).distinct().sorted()

    val isMultiAnswer: Boolean get() = correctIndices.size > 1
}

data class ImportProblem(val number: Int?, val reason: String, val source: String)

data class Quiz(
    val id: String,
    val title: String,
    val questions: List<QuizQuestion>,
    val generatedByAi: Boolean = false,
    val importProblems: List<ImportProblem> = emptyList()
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
    fun score(quiz: Quiz, answers: List<Int?>): QuizScore = scoreSelections(quiz, answers.map { setOfNotNull(it) })

    fun scoreSelections(quiz: Quiz, answers: List<Set<Int>>): QuizScore {
        require(answers.size == quiz.questions.size) { "Ответов должно быть столько же, сколько вопросов" }
        var correct = 0
        var incorrect = 0
        var skipped = 0
        var ungraded = 0

        quiz.questions.forEachIndexed { index, question ->
            require(answers[index].all { it in question.options.indices }) { "Несуществующий вариант ответа" }
            val key = question.correctIndices
            when {
                key.isEmpty() || !question.evidence.confirmed -> ungraded++
                answers[index].isEmpty() -> skipped++
                answers[index] == key.toSet() -> correct++
                else -> incorrect++
            }
        }

        return QuizScore(correct, incorrect, skipped, ungraded)
    }
}
