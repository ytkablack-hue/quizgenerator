package kz.quizgenerator
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal data class StudySet(val id: String,val title: String,val materialIds: List<String> = emptyList(),
    val quizIds: List<String> = emptyList(),val cardIds: List<String> = emptyList(),val notes: String="",
    val lastOpened: Long=0,val archived: Boolean=false)

/** Additive metadata only: archive/rename/link never deletes or rewrites study/quiz/source files. */
internal class StudySetStore(private val root: File) {
    private val file get()=File(root,"sets.json")
    fun list(): List<StudySet> = synchronized(lock) {
        if(!file.exists())return@synchronized emptyList()
        require(file.length() in 1..12_000_000) {"Повреждена библиотека наборов. Файлы материалов не изменены"}
        val value=JSONObject(file.readText(Charsets.UTF_8));require(value.getInt("version")==1) {"Нужна более новая версия приложения"}
        val array=value.getJSONArray("sets");require(array.length()<=5000)
        List(array.length()) {i ->val r=array.getJSONObject(i)
            fun ids(name: String): List<String> {val a=r.optJSONArray(name) ?: JSONArray();require(a.length()<=5000);return List(a.length(),a::getString)}
            StudySet(r.getString("id"),r.getString("title"),ids("materialIds"),ids("quizIds"),ids("cardIds"),r.optString("notes"),r.optLong("lastOpened"),r.optBoolean("archived")).also(::validate)
        }.also {sets ->require(sets.map {it.id}.distinct().size==sets.size)}
    }
    fun create(title: String): StudySet = ensure(StudySet(UUID.randomUUID().toString(),title.trim()))
    fun ensure(value: StudySet): StudySet = synchronized(lock) {
        val all=list();all.firstOrNull {it.id==value.id} ?: value.also {write(all+it)}
    }
    fun update(id: String,change: (StudySet)->StudySet): StudySet = synchronized(lock) {
        val all=list();val old=all.firstOrNull {it.id==id} ?: error("Набор недоступен")
        val next=change(old);require(next.id==id);write(all.map {if(it.id==id)next else it});next
    }
    fun linkMaterial(id: String,materialId: String)=update(id) {it.copy(materialIds=(it.materialIds+materialId).distinct())}
    fun linkQuiz(id: String,quizId: String)=update(id) {it.copy(quizIds=(it.quizIds+quizId).distinct())}
    fun linkCards(id: String,cards: List<String>)=update(id) {it.copy(cardIds=(it.cardIds+cards).distinct())}
    private fun write(sets: List<StudySet>) {
        require(sets.size<=5000);sets.forEach(::validate)
        val json=JSONObject().put("version",1).put("sets",JSONArray(sets.map {set ->JSONObject()
            .put("id",set.id).put("title",set.title).put("materialIds",JSONArray(set.materialIds)).put("quizIds",JSONArray(set.quizIds))
            .put("cardIds",JSONArray(set.cardIds)).put("notes",set.notes).put("lastOpened",set.lastOpened).put("archived",set.archived)})).toString()
        require(json.toByteArray(Charsets.UTF_8).size<=12_000_000) {"Библиотека наборов слишком большая"}
        atomicWrite(file,json)
    }
    private fun validate(set: StudySet) {
        require(runCatching {UUID.fromString(set.id).toString()==set.id}.getOrDefault(false))
        require(set.title.isNotBlank() && set.title.length<=160 && set.notes.length<=20000 && set.lastOpened>=0)
        for(list in listOf(set.materialIds,set.quizIds,set.cardIds))require(list.size<=5000 && list.distinct().size==list.size && list.all {it.isNotBlank() && it.length<=128})
        require(set.materialIds.all {runCatching {UUID.fromString(it).toString()==it}.getOrDefault(false)})
    }
    private companion object {val lock=Any()}
}

internal object WorkspaceIndex {
    /** Virtual views of legacy data; no migration or startup writes. Archived associations stay reserved. */
    fun build(saved: List<StudySet>,quizzes: List<Quiz>,materials: List<MaterialRecord>,cards: List<Flashcard>): List<StudySet> {
        val quizIds=saved.flatMap {it.quizIds}.toSet();val documentIds=saved.flatMap {it.materialIds}.toSet()
        val cardsIds=saved.flatMap {it.cardIds}.toSet()
        val oldQuizzes=quizzes.filter {it.id !in quizIds}.map {StudySet(legacyId("quiz:"+it.id),it.title.take(160),quizIds=listOf(it.id))}
        val oldMaterials=materials.filter {it.id !in documentIds}.map {StudySet(legacyId("material:"+it.id),it.name.substringBeforeLast('.').ifBlank {it.name}.take(160),materialIds=listOf(it.id))}
        val loose=cards.filter {it.id !in cardsIds && (it.sourceQuizId==null || (it.sourceQuizId !in quizIds && quizzes.none {q->q.id==it.sourceQuizId}))}
        return saved+oldQuizzes+oldMaterials+if(loose.isEmpty())emptyList() else listOf(StudySet(legacyId("cards"),"Мои карточки",cardIds=loose.map {it.id}))
    }
    private fun legacyId(value: String)=UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString()
    fun cards(set: StudySet,data: StudyData)=data.cards.filter {it.id in set.cardIds || it.sourceQuizId in set.quizIds}
    fun attempts(set: StudySet,data: StudyData)=data.attempts.filter {it.quizId in set.quizIds}
    fun practice(quiz: Quiz,attempts: List<Attempt>): Quiz? {
        val mistakes=attempts.filter {it.quizId==quiz.id}.flatMap {it.mistakes}.map {it.question}.toSet()
        val questions=quiz.questions.filter {it.text in mistakes && it.evidence.confirmed && it.correctIndices.isNotEmpty()}
        return if(questions.isEmpty())null else quiz.copy(title=quiz.title+" · Повтор ошибок",questions=questions)
    }
}
