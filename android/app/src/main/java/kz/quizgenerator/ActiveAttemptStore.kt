package kz.quizgenerator

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class ActiveAttempt(val quiz: Quiz, val answers: List<Int?>, val index: Int,
    val attemptId: String, val elapsedDeadline: Long, val wallDeadline: Long, val bootCount: Int, val selections: List<Set<Int>>? = null)

internal class ActiveAttemptStore(private val file: File) {
    fun save(value: ActiveAttempt) {
        atomicWrite(file, JSONObject().put("quiz", JSONObject(QuizJson.serialize(value.quiz)))
            .put("answers", JSONArray(value.answers.map { it ?: JSONObject.NULL }))
            .put("selections",value.selections?.let { JSONArray(it.map { set -> JSONArray(set.sorted()) }) }).put("index", value.index)
            .put("attemptId", value.attemptId).put("elapsedDeadline", value.elapsedDeadline)
            .put("wallDeadline", value.wallDeadline).put("bootCount", value.bootCount).toString())
    }
    fun load(): ActiveAttempt? {
        if (!file.exists()) return null
        val root = JSONObject(file.readText())
        val quiz = QuizJson.parse(root.getJSONObject("quiz").toString())
        val values = root.getJSONArray("answers")
        require(values.length() == quiz.questions.size)
        val answers = List(values.length()) { i -> if (values.isNull(i)) null else values.getInt(i).also { require(it in quiz.questions[i].options.indices) } }
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
        val index = root.getInt("index").also { require(it in quiz.questions.indices) }
        return ActiveAttempt(quiz, answers, index, root.getString("attemptId"), root.getLong("elapsedDeadline"), root.getLong("wallDeadline"), root.getInt("bootCount"),selections)
    }
    fun clear() { check(!file.exists() || file.delete()) { "Не удалось закрыть попытку" } }
}
