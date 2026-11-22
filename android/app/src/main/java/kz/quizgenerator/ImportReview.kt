package kz.quizgenerator
internal object ImportReview {
    fun insert(questions: List<QuizQuestion>,question: QuizQuestion): Pair<List<QuizQuestion>,Int> {
        val index=question.sourceNumber?.let {number->questions.indexOfFirst {it.sourceNumber!=null && it.sourceNumber>number}.takeIf {it>=0}} ?: questions.size
        return questions.toMutableList().apply {add(index,question)} to index
    }
    fun repair(issue: ImportProblem,title: String): QuizQuestion {
        require(issue.source.length<=65536) {"Фрагмент слишком длинный для одного вопроса. Скопируйте нужный текст из сохранённого оригинала"}
        return (runCatching {QuizTextParser.parse(issue.source,title).quiz.questions.single()}.getOrNull()
            ?: emptyQuestion().copy(text=issue.source)).copy(sourceNumber=issue.number)
    }
}
