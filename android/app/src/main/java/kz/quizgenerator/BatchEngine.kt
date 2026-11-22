package kz.quizgenerator

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

internal class AiServerFailure(val code: Int,message: String): IllegalStateException(message)
/** One durable delivery at a time. There is deliberately no automatic retry. */
internal class BatchEngine(private val store: BatchJobStore,private val clock: ()->Long=System::currentTimeMillis) {
    suspend fun step(id: String,send: suspend (String,JSONObject,String)->JSONObject): BatchJob {
        var selected=-1
        val sending=store.update(id) {job->
            if(job.status !in listOf(BatchStatus.READY,BatchStatus.RUNNING) || job.parts.any {it.status in listOf(BatchPartStatus.SENDING,BatchPartStatus.UNKNOWN,BatchPartStatus.PARTIAL)})job
            else {
                val i=job.parts.indexOfFirst {it.status==BatchPartStatus.WAITING}
                if(i<0)job.copy(status=BatchStatus.COMPLETE)
                else if(clock()<maxOf(job.nextAt,store.readyAt()))job.copy(nextAt=maxOf(job.nextAt,store.readyAt()))
                else {store.reserveAt(clock());selected=i;job.copy(status=BatchStatus.RUNNING,nextAt=clock()+22000,notice="",parts=job.parts.mapIndexed {n,p->if(n==i)p.copy(status=BatchPartStatus.SENDING) else p})}
            }
        }
        if(selected<0)return sending
        val part=sending.parts[selected]
        try {
            val response=send(part.route,JSONObject(part.payload),part.requestId)
            var complete=true
            val accepted=when(sending.kind) {
                BatchKind.QUIZ->{val quiz=QuizJson.parse(response.toString());require(quiz.questions.size<=part.expected) {"ИИ вернул лишние вопросы"}
                    val seen=sending.questions().map(::identity).toMutableSet()
                    val unique=quiz.questions.filter {seen.add(identity(it))}.mapIndexed {i,q->q.copy(id=UUID.nameUUIDFromBytes((sending.id+":"+selected+":"+i).toByteArray()).toString(),evidence=AnswerEvidence.CLOUD_AI)}
                    require(unique.isNotEmpty()) {"Не получены новые уникальные вопросы"};complete=unique.size==part.expected
                    QuizJson.serialize(quiz.copy(questions=unique,generatedByAi=true))
                }
                BatchKind.ANSWERS->{StudyContract.answers(response,JSONObject(part.payload));response.toString()}
                BatchKind.LECTURE->{StudyContract.lecture(response,JSONObject(part.payload));response.toString()}
            }
            return store.update(id) {current->
                val parts=current.parts.toMutableList().also {it[selected]=part.copy(status=if(complete)BatchPartStatus.DONE else BatchPartStatus.PARTIAL,response=accepted)}
                val status=when {current.status==BatchStatus.CANCELLED->BatchStatus.CANCELLED;!complete->BatchStatus.REVIEW;parts.all {it.status==BatchPartStatus.DONE}->BatchStatus.COMPLETE;current.status==BatchStatus.PAUSED->BatchStatus.PAUSED;else->BatchStatus.RUNNING}
                current.copy(parts=parts,status=status,notice=if(complete)"" else "Партия неполная или содержит дубликаты. Сохранено только фактическое число уникальных вопросов; откройте частичный результат")
            }
        } catch(e: CancellationException) {
            withContext(NonCancellable+Dispatchers.IO){markUnknown(id,selected,"Запрос прерван. Сервер мог его выполнить; автоматического повтора нет")};throw e
        } catch(e: Exception) {
            val code=(e as? AiServerFailure)?.code
            val status=when(code){429->BatchStatus.LIMIT;401,403->BatchStatus.AUTH;404->BatchStatus.UNAVAILABLE;else->if(e is IllegalArgumentException || e is org.json.JSONException)BatchStatus.REVIEW else BatchStatus.UNCERTAIN}
            return store.update(id) {job->job.copy(status=if(job.status==BatchStatus.CANCELLED)BatchStatus.CANCELLED else status,
                notice=when(status){BatchStatus.LIMIT->"Лимит ИИ исчерпан. Результаты сохранены; продолжите позже";BatchStatus.AUTH->"Для продолжения подтвердите вход и почту";BatchStatus.UNAVAILABLE->"Этот серверный маршрут ещё не развёрнут. Готовые результаты сохранены";BatchStatus.REVIEW->e.message?.take(240) ?: "Неполный ответ: требуется проверка";else->"Исход запроса неизвестен. Ответ мог быть выполнен и учтён в квоте. Автоматического повтора нет"},
                parts=job.parts.mapIndexed {i,p->if(i==selected)p.copy(status=if(status in listOf(BatchStatus.LIMIT,BatchStatus.AUTH,BatchStatus.UNAVAILABLE))BatchPartStatus.WAITING else BatchPartStatus.UNKNOWN) else p})}
        }
    }
    private fun markUnknown(id: String,index: Int,message: String)=store.update(id){j->j.copy(status=if(j.status==BatchStatus.CANCELLED)BatchStatus.CANCELLED else BatchStatus.UNCERTAIN,notice=message,parts=j.parts.mapIndexed {i,p->if(i==index && p.status==BatchPartStatus.SENDING)p.copy(status=BatchPartStatus.UNKNOWN) else p})}
    fun pause(id: String,cancel: Boolean=false)=store.update(id){it.copy(status=if(cancel)BatchStatus.CANCELLED else BatchStatus.PAUSED,notice=if(cancel)"Отменено. Уже полученные результаты сохранены" else "Приостановлено")}
    fun resume(id: String)=store.update(id){require(it.status !in listOf(BatchStatus.COMPLETE,BatchStatus.CANCELLED));require(it.parts.none {p->p.status in listOf(BatchPartStatus.UNKNOWN,BatchPartStatus.SENDING,BatchPartStatus.PARTIAL)}) {"Сначала проверьте неопределённый исход"};it.copy(status=BatchStatus.READY,notice="")}
    fun retryUnknown(id: String)=store.update(id){j->require(j.status !in listOf(BatchStatus.COMPLETE,BatchStatus.CANCELLED));val i=j.parts.indexOfFirst {it.status==BatchPartStatus.UNKNOWN};require(i>=0 && j.parts[i].response==null)
        j.copy(status=BatchStatus.READY,notice="Разрешён один новый запрос вместо неопределённого",parts=j.parts.mapIndexed {n,p->if(n==i)p.copy(status=BatchPartStatus.WAITING,requestId=UUID.randomUUID().toString()) else p})}
    private fun identity(q: QuizQuestion)=q.text.lowercase().replace(Regex("[\\s\\p{Punct}]+")," ").trim()
}

internal object BatchAnswerMerge {
    fun apply(job: BatchJob,current: Quiz,confirmId: String?=null,confirmed: Set<Int>?=null): Quiz {
        require(job.kind==BatchKind.ANSWERS);val original=QuizJson.parse(requireNotNull(job.sourceQuiz));require(original.id==current.id)
        val source=original.questions.associateBy {it.id};val suggestions=job.proposals().associateBy {it.getString("id")}
        return current.copy(questions=current.questions.map {q->
            val old=source[q.id];val suggestion=suggestions[q.id]
            if(q.evidence.confirmed || old==null || suggestion==null || AnswerPolicy.fingerprint(q)!=AnswerPolicy.fingerprint(old))q
            else if(confirmId==q.id) {val indices=requireNotNull(confirmed);require(indices.isNotEmpty() && indices.all {it in q.options.indices});q.copy(correctIndex=indices.sorted().first(),additionalCorrectIndices=indices.sorted().drop(1),explanation=suggestion.getString("explanation"),evidence=AnswerEvidence.MANUAL)}
            else if(confirmId!=null)q
            else {val a=suggestion.getJSONArray("indices");val indices=List(a.length()){a.getInt(it)}
                q.copy(correctIndex=indices.firstOrNull(),additionalCorrectIndices=indices.drop(1),explanation=(if(suggestion.getBoolean("uncertain"))"Ответ требует проверки. " else "")+suggestion.getString("explanation"),evidence=if(indices.isEmpty())AnswerEvidence.UNKNOWN else AnswerEvidence.CLOUD_AI)}
        })
    }
}
