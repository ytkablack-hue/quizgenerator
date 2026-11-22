package kz.quizgenerator
internal enum class QuestionOutcome(val label: String) {CORRECT("Верно"),INCORRECT("Ошибка"),SKIPPED("Пропущен"),UNVERIFIED("Без подтверждённого ключа")}
internal object StudyResultRules {
    fun outcome(question: QuizQuestion,answer: Set<Int>): QuestionOutcome = when {
        !question.evidence.confirmed || question.correctIndices.isEmpty()->QuestionOutcome.UNVERIFIED
        answer.isEmpty()->QuestionOutcome.SKIPPED
        answer==question.correctIndices.toSet()->QuestionOutcome.CORRECT
        else->QuestionOutcome.INCORRECT
    }
    fun mistakes(quiz: Quiz,answers: List<Set<Int>>): Quiz? {
        require(quiz.questions.size==answers.size)
        val selected=quiz.questions.filterIndexed {i,q->outcome(q,answers[i]) in setOf(QuestionOutcome.INCORRECT,QuestionOutcome.SKIPPED)}
        return if(selected.isEmpty())null else quiz.copy(title=quiz.title+" · Повтор ошибок",questions=selected)
    }
}
