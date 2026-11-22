package kz.quizgenerator

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class ActiveAttempt(val quiz: Quiz, val answers: List<Int?>, val index: Int,
    val attemptId: String, val elapsedDeadline: Long, val wallDeadline: Long, val bootCount: Int, val selections: List<Set<Int>>? = null,
    val examMode: Boolean=true,val hints: Boolean=false,val revealAnswers: Boolean=true,
    val startedAt: Long=0,val startedElapsed: Long=0,val startedBootCount: Int=bootCount,val bookmarks: Set<String> = emptySet())

internal class ActiveAttemptStore(private val file: File) {
    private class State {val lock=Any();val generation=java.util.concurrent.atomic.AtomicLong()}
    private val state=states.computeIfAbsent(file.canonicalPath){State()}
    fun ticket(): Long=state.generation.get()
    fun save(value: ActiveAttempt,ticket: Long=this.ticket(),checkCancelled: ()->Unit={}): Boolean = synchronized(state.lock) {
        checkCancelled()
        if(ticket!=state.generation.get())return@synchronized false
        atomicWrite(file, JSONObject().put("quiz", JSONObject(QuizJson.serialize(value.quiz)))
            .put("answers", JSONArray(value.answers.map { it ?: JSONObject.NULL }))
            .put("selections",value.selections?.let { JSONArray(it.map { set -> JSONArray(set.sorted()) }) }).put("index", value.index)
            .put("attemptId", value.attemptId).put("elapsedDeadline", value.elapsedDeadline)
            .put("wallDeadline", value.wallDeadline).put("bootCount", value.bootCount)
            .put("examMode",value.examMode).put("hints",value.hints).put("revealAnswers",value.revealAnswers)
            .put("startedAt",value.startedAt).put("startedElapsed",value.startedElapsed).put("startedBootCount",value.startedBootCount)
            .put("bookmarks",JSONArray(value.bookmarks.sorted())).toString())
        true
    }
    fun load(): ActiveAttempt? {
        if (!file.exists()) return null
        val root = JSONObject(file.readText())
        val quiz = QuizJson.parse(root.getJSONObject("quiz").toString())
        val values = root.getJSONArray("answers")
        require(values.length() == quiz.questions.size)
        val answers = List(values.length()) { i -> if (values.isNull(i)) null else values.get(i).let { n -> require(n is Number && n.toDouble()==n.toInt().toDouble() && n.toInt() in quiz.questions[i].options.indices);n.toInt() } }
        val selections=root.optJSONArray("selections")?.let { all ->
            require(all.length()==quiz.questions.size)
            List(all.length()) { i ->
                val values=all.getJSONArray(i);require(values.length()<=quiz.questions[i].options.size)
                (0 until values.length()).map { j ->
                    val n=values.get(j);require(n is Number && n.toDouble()==n.toInt().toDouble() && n.toInt() in quiz.questions[i].options.indices)
                    n.toInt()
                }.toSet().also { require(it.size==values.length()) }
            }
        }
        val marked=root.optJSONArray("bookmarks")?.let {a->
            require(a.length()<=quiz.questions.size);List(a.length()){a.getString(it)}.toSet().also {ids->require(ids.size==a.length() && ids.all {id->quiz.questions.any {it.id==id}})}
        }.orEmpty()
        val index = root.getInt("index").also { require(it in quiz.questions.indices) }
        return ActiveAttempt(quiz, answers, index, root.getString("attemptId"), root.getLong("elapsedDeadline"), root.getLong("wallDeadline"), root.getInt("bootCount"),selections,root.optBoolean("examMode",true),root.optBoolean("hints",false),root.optBoolean("revealAnswers",true),
            root.optLong("startedAt").also {require(it>=0)},root.optLong("startedElapsed").also {require(it>=0)},root.optInt("startedBootCount",root.getInt("bootCount")),marked)
    }
    fun clear() {state.generation.incrementAndGet();synchronized(state.lock){check(!file.exists() || file.delete()) {"Не удалось закрыть попытку"}}}
    private companion object {val states=java.util.concurrent.ConcurrentHashMap<String,State>()}
}
