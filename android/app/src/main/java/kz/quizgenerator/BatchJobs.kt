package kz.quizgenerator

import java.io.File
import java.util.UUID
import java.security.MessageDigest
import java.text.BreakIterator
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal enum class BatchKind {QUIZ,ANSWERS,LECTURE}
internal enum class BatchStatus {READY,RUNNING,PAUSED,LIMIT,AUTH,UNAVAILABLE,UNCERTAIN,REVIEW,CANCELLED,COMPLETE}
internal enum class BatchPartStatus {WAITING,SENDING,DONE,PARTIAL,UNKNOWN}
internal data class BatchPart(val requestId: String,val route: String,val payload: String,val expected: Int,val status: BatchPartStatus=BatchPartStatus.WAITING,val response: String?=null)
internal data class BatchJob(val id: String,val uid: String,val setId: String,val kind: BatchKind,val title: String,val sourceRef: String?,val target: Int,
    val parts: List<BatchPart>,val sourceQuiz: String?=null,val status: BatchStatus=BatchStatus.READY,val notice: String="",val nextAt: Long=0,val consent: String="",val consentedAt: Long=0,val createdAt: Long=System.currentTimeMillis(),val publishedIds: Set<String> = emptySet()) {
    val done get()=parts.count {it.status==BatchPartStatus.DONE}
    fun fingerprint(): String=BatchPlanning.hash(JSONObject().put("kind",kind.name).put("source",sourceRef).put("target",target).put("set",setId).put("parts",JSONArray(parts.map {JSONObject().put("route",it.route).put("body",it.payload).put("expected",it.expected)})).toString())
    fun accepted(now: Long)=copy(consent=fingerprint(),consentedAt=now)
    fun questions(): List<QuizQuestion> = if(kind!=BatchKind.QUIZ)emptyList() else parts.mapNotNull {it.response}.flatMap {QuizJson.parse(it).questions}
    fun proposals(): List<JSONObject> = if(kind!=BatchKind.ANSWERS)emptyList() else parts.mapNotNull {part->part.response?.let {StudyContract.answers(JSONObject(it),JSONObject(part.payload))}}.flatten()
    val actual: Int get()=when(kind){BatchKind.QUIZ->questions().size;BatchKind.ANSWERS->proposals().size;BatchKind.LECTURE->done}
    fun quiz(): Quiz {val q=questions();require(q.isNotEmpty());return Quiz(id,title+if(status!=BatchStatus.COMPLETE)" · частичный" else "",q,generatedByAi=true)}
    fun lecture(): Lecture {val values=parts.filter {it.response!=null};require(values.isNotEmpty());val body=values.mapIndexed {i,p->"# Часть "+(i+1)+" из "+parts.size+"\n"+StudyContract.lecture(JSONObject(p.response!!),JSONObject(p.payload))}.joinToString("\n\n")
        return Lecture(id,setId,title,body,sourceRef,BatchPlanning.hash(parts.joinToString {it.payload}),status==BatchStatus.COMPLETE)}
}
internal class BatchJobStore(private val root: File,val uid: String) {
    private val directory=File(root,"batch_jobs")
    private fun file(id: String): File {require(UUID.fromString(id).toString()==id);return File(directory,"$id.json").also {require(it.canonicalFile.parentFile==directory.canonicalFile)}}
    fun save(j: BatchJob) = synchronized(lock) {
        require(j.uid==uid && j.parts.size in 1..(if(j.kind==BatchKind.ANSWERS)5000 else 100) && j.title.isNotBlank() && j.title.length<=180 && j.consentedAt>0 && j.consent==j.fingerprint()) {"План изменился: требуется новое согласие"}
        require(j.target==j.parts.sumOf {it.expected}) {"Количество в плане не совпадает с партиями"}
        require(UUID.fromString(j.setId).toString()==j.setId);require(j.parts.map {it.requestId}.distinct().size==j.parts.size)
        j.parts.forEach {require(UUID.fromString(it.requestId).toString()==it.requestId);require(it.route in listOf("quizzes","lectures","answers"));require(it.payload.toByteArray().size<=65536);AiRequestRules.validate(it.route,JSONObject(it.payload))}
        val value=JSONObject().put("version",1).put("id",j.id).put("uid",j.uid).put("setId",j.setId).put("kind",j.kind.name).put("title",j.title).put("sourceRef",j.sourceRef).put("target",j.target).put("sourceQuiz",j.sourceQuiz)
            .put("publishedIds",JSONArray(j.publishedIds.sorted())).put("status",j.status.name).put("notice",j.notice).put("nextAt",j.nextAt).put("consent",j.consent).put("consentedAt",j.consentedAt).put("createdAt",j.createdAt)
            .put("parts",JSONArray(j.parts.map {JSONObject().put("requestId",it.requestId).put("route",it.route).put("payload",it.payload).put("expected",it.expected).put("status",it.status.name).put("response",it.response)})).toString()
        require(value.toByteArray().size<=32_000_000);atomicWrite(file(j.id),value)
    }
    fun load(id: String): BatchJob = synchronized(lock) {val f=file(id);require(f.length() in 1..32_000_000);val v=JSONObject(f.readText());require(v.getInt("version")==1 && v.getString("id")==id && v.getString("uid")==uid) {"Задание другого аккаунта"}
        val a=v.getJSONArray("parts");require(a.length() in 1..5000);val parts=List(a.length()){val p=a.getJSONObject(it);BatchPart(p.getString("requestId"),p.getString("route"),p.getString("payload"),p.getInt("expected"),BatchPartStatus.valueOf(p.getString("status")),p.opt("response") as? String)}
        BatchJob(id,uid,v.getString("setId"),BatchKind.valueOf(v.getString("kind")),v.getString("title"),v.opt("sourceRef") as? String,v.getInt("target"),parts,v.opt("sourceQuiz") as? String,BatchStatus.valueOf(v.getString("status")),v.optString("notice"),v.optLong("nextAt"),v.getString("consent"),v.getLong("consentedAt"),v.getLong("createdAt"),v.optJSONArray("publishedIds")?.let {ids->(0 until ids.length()).map {ids.getString(it)}.toSet()}.orEmpty()).also {require(it.consent==it.fingerprint())}
    }
    fun update(id: String,change: (BatchJob)->BatchJob): BatchJob = synchronized(lock) {change(load(id)).also(::save)}
    fun readyAt(): Long = synchronized(lock) {val f=File(root,"batch-rate.json");if(!f.exists())0 else {require(f.length() in 1..256);JSONObject(f.readText()).getLong("nextAt")}}
    fun reserveAt(now: Long) = synchronized(lock) {atomicWrite(File(root,"batch-rate.json"),JSONObject().put("nextAt",now+22000).toString())}
    fun list(setId: String)=directory.listFiles {f->f.extension=="json"}.orEmpty().map {load(it.nameWithoutExtension)}.filter {it.setId==setId}.sortedByDescending {it.createdAt}
    fun restore(j: BatchJob): BatchJob=if(j.status!=BatchStatus.RUNNING)j else j.copy(status=if(j.parts.any {it.status==BatchPartStatus.SENDING})BatchStatus.UNCERTAIN else BatchStatus.PAUSED,
        parts=j.parts.map {if(it.status==BatchPartStatus.SENDING)it.copy(status=BatchPartStatus.UNKNOWN) else it},notice="Обработка прервана. Проверьте сохранённый результат перед продолжением")
    private companion object {val lock=Any()}
}
internal object BatchPlanning {
    fun hash(value: String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){"%02x".format(it.toInt() and 255)}
    private fun part(route: String,value: JSONObject,expected: Int)=BatchPart(UUID.randomUUID().toString(),route,value.toString(),expected)
    /** Whole explicit questions or Unicode sentences; never an arbitrary character cut. */
    fun chunks(text: String,minParts: Int): List<String> {
        require(text.isNotBlank());val target=minOf(9800,maxOf(256,(text.length+minParts-1)/minParts))
        val tagged=text.lineSequence().any {ParserPatterns.taggedQuestion.matches(it.trim()) || ParserPatterns.namedQuestion.matches(it.trim()) || ParserPatterns.numbered.matchEntire(it.trim())?.let {n->ParserPatterns.looksLikeQuestionText(n.groupValues[3])}==true}
        if(tagged){val p=MaterialSlices.plan(text,target);require(p.none {it.oversized}) {"Слишком большой вопрос: выберите диапазон вручную"};return p.map {it.text(text)}}
        val iterator=BreakIterator.getSentenceInstance(Locale.forLanguageTag("ru"));iterator.setText(text);val units=mutableListOf<String>();var a=iterator.first();var b=iterator.next()
        while(b!=BreakIterator.DONE){units+=text.substring(a,b);a=b;b=iterator.next()}
        val result=mutableListOf<String>();var pending=StringBuilder()
        for(unit in units){require(unit.length<=9800) {"Предложение слишком длинное. Выберите меньший материал вручную"};if(pending.isNotEmpty() && pending.length+unit.length>target){result+=pending.toString();pending=StringBuilder()};pending.append(unit)}
        if(pending.isNotEmpty())result+=pending.toString();require(result.joinToString("")==text);return result.filter {it.isNotBlank()}
    }
    fun quiz(uid: String,setId: String,title: String,mode: AiInputMode,topic: String,text: String,count: Int,difficulty: String,source: String?=null): BatchJob {
        require(count in 1..500);val minimum=(count+24)/25
        val materials=if(mode==AiInputMode.TOPIC)emptyList() else chunks(text,minimum)
        require(mode!=AiInputMode.TOPIC || topic.isNotBlank());require(mode==AiInputMode.TOPIC || materials.size>=minimum) {"Недостаточно отдельных фрагментов для $minimum запросов. Уменьшите число вопросов"}
        val n=maxOf(minimum,materials.size);require(n<=count && n<=100) {"Материал требует слишком много запросов. Выберите часть источника"}
        val counts=if(n==minimum)List(n){minOf(25,count-it*25)} else List(n){count/n+if(it<count%n)1 else 0}
        val focuses=listOf("основы","применение","сравнение","причины","примеры","ошибки","связи","обобщение")
        val parts=counts.mapIndexed {i,c->val t=if(mode==AiInputMode.TOPIC && n>1)topic+" · "+focuses[i%focuses.size]+" · блок ${i+1}/$n" else topic
            part("quizzes",AiGeneration.build(mode,t,materials.getOrNull(i).orEmpty(),materials.getOrNull(i).orEmpty(),"",difficulty,c),c)}
        return BatchJob(UUID.randomUUID().toString(),uid,setId,BatchKind.QUIZ,title,source,count,parts)
    }
    fun lecture(uid: String,setId: String,title: String,mode: AiInputMode,topic: String,text: String,level: String,length: String,source: String?=null): BatchJob {
        require(mode!=AiInputMode.TOPIC || topic.isNotBlank());val materials=if(mode==AiInputMode.TOPIC)List(if(length=="detailed")3 else 1){""} else chunks(text,1)
        require(materials.size in 1..100);val parts=materials.mapIndexed {i,m->part("lectures",JSONObject().put("topic",topic.trim()).put("material",m.trim()).put("level",level).put("length",length).put("sectionIndex",i+1).put("sectionCount",materials.size),1)}
        return BatchJob(UUID.randomUUID().toString(),uid,setId,BatchKind.LECTURE,title,source,parts.size,parts)
    }
    fun answers(uid: String,setId: String,quiz: Quiz): BatchJob {
        val unknown=quiz.questions.filter {!it.evidence.confirmed};require(unknown.isNotEmpty()) {"Все ответы уже подтверждены"}
        val parts=mutableListOf<BatchPart>();var group=mutableListOf<QuizQuestion>()
        fun body(items: List<QuizQuestion>)=JSONObject().put("questions",JSONArray(items.map {q->JSONObject().put("id",q.id).put("text",q.text).put("options",JSONArray(q.options))}))
            .put("context",items.mapNotNull {it.sourceExcerpt?.takeIf {s->s.isNotBlank()}}.distinct().joinToString("\n\n"))
        fun fits(items: List<QuizQuestion>): Boolean {val value=body(items);return items.size<=25 && value.toString().toByteArray().size<=65536 &&
            value.getString("context").length+items.sumOf {q->q.text.length+q.options.sumOf {it.length}}<=12000}
        fun flush(){if(group.isNotEmpty()){parts+=part("answers",body(group),group.size);group=mutableListOf()}}
        for(q in unknown){require(q.text.length<=6000 && q.options.all {it.length<=4000} && fits(listOf(q))) {"Вопрос ${q.id} с контекстом слишком большой для серверного анализа. Выберите меньший контекст вручную"}
            if(!fits(group+q))flush();group+=q
        };flush();require(parts.size<=5000) {"Слишком большой банк: выберите часть вопросов"}
        return BatchJob(UUID.randomUUID().toString(),uid,setId,BatchKind.ANSWERS,quiz.title,"quiz:"+quiz.id,unknown.size,parts,QuizJson.serialize(quiz))
    }
}
