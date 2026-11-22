package kz.quizgenerator
import java.io.File
internal object BatchPublication {
    fun quiz(job: BatchJob,existing: Quiz?): Quiz {
        val incoming=job.quiz()
        if(existing==null)return incoming
        require(existing.id==job.id)
        val present=existing.questions.map {it.id}.toSet()
        return existing.copy(title=if(job.status==BatchStatus.COMPLETE && existing.title==job.title+" · частичный")job.title else existing.title,
            questions=existing.questions+incoming.questions.filter {it.id !in present && it.id !in job.publishedIds})
    }
    fun saveQuiz(job: BatchJob,repository: QuizRepository,store: BatchJobStore): Quiz {
        val f=File(repository.workspaceRoot,"quiz_banks/"+job.id+".json")
        val existing=if(f.exists())QuizJson.parse(f.readText()) else null
        val saved=repository.saveQuiz(quiz(job,existing))
        store.update(job.id){it.copy(publishedIds=it.publishedIds+job.questions().map {q->q.id})}
        repository.sets.linkQuiz(job.setId,saved.id)
        return saved
    }
}
