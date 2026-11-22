package kz.quizgenerator
internal object StudySessionPolicy {
    fun hints(exam: Boolean,requested: Boolean)=!exam && requested
    fun feedback(exam: Boolean,question: QuizQuestion)=!exam && question.evidence.confirmed && question.correctIndices.isNotEmpty()
    fun duration(startWall: Long,startElapsed: Long,startBoot: Int,boot: Int,wall: Long,elapsed: Long,
        wallDeadline: Long=0,elapsedDeadline: Long=0): Long? {
        val endWall=if(wallDeadline>0)minOf(wall,wallDeadline) else wall
        val endElapsed=if(elapsedDeadline>0)minOf(elapsed,elapsedDeadline) else elapsed
        return when {
            startWall<=0 -> null
            startBoot>=0 && startBoot==boot && endElapsed>=startElapsed -> endElapsed-startElapsed
            endWall>=startWall -> endWall-startWall
            else -> null
        }
    }
    fun nextBookmark(questions: List<QuizQuestion>,marked: Set<String>,current: Int): Int? {
        if(questions.isEmpty())return null
        return (1..questions.size).map {(current+it).mod(questions.size)}.firstOrNull {questions[it].id in marked}
    }
    fun bookmark(current: Set<String>,id: String)=if(id in current)current-id else current+id
    fun mistakeCards(quiz: Quiz,answers: List<Set<Int>>,existing: List<Flashcard>): List<Flashcard> =
        StudyResultRules.mistakes(quiz,answers)?.let {QuizCards.create(it,existing)}.orEmpty()
    fun durationLabel(ms: Long?): String = ms?.let {"%02d:%02d".format(java.util.Locale.ROOT,it/60000,(it/1000)%60)} ?: "Не измерено"
}
