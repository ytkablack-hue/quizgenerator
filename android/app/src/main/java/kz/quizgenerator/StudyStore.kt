package kz.quizgenerator

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate

internal fun atomicWrite(file: File, content: String) {
    val folder = checkNotNull(file.parentFile)
    check(folder.isDirectory || folder.mkdirs()) { "Не удалось создать папку данных" }
    val temporary = File.createTempFile(".testhelper-", ".tmp", folder)
    try {
        temporary.writeText(content, Charsets.UTF_8)
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        temporary.delete()
    }
}

internal class StudyStore(private val file: File) {
    fun load(): StudyData {
        if (!file.exists()) return StudyData()
        val root = JSONObject(file.readText(Charsets.UTF_8))
        val settings = root.optJSONObject("settings") ?: JSONObject()
        return StudyData(
            cards = root.readObjects("cards") { Flashcard(getString("id"), getString("front"), getString("back"), optBoolean("generatedByAi")) },
            exams = root.readObjects("exams") {
                Exam(getString("id"), getString("title"), LocalDate.parse(getString("date")), strings("topics"), strings("quizIds"))
            },
            attempts = root.readObjects("attempts") {
                Attempt(getString("id"), getString("quizId"), getString("title"), getLong("completedAt"),
                    QuizScore(getInt("correct"), getInt("incorrect"), getInt("skipped"), getInt("ungraded")),
                    readObjects("mistakes") { Mistake(getString("question"), nullableText("given"), getString("correct"), nullableText("explanation")) })
            },
            settings = with(settings) {
                StudySettings(optInt("limit"), optBoolean("shuffleQuestions"), optBoolean("shuffleOptions"),
                    optBoolean("showHints", true), optBoolean("showAnswers", true), optInt("minutes"),
                    optBoolean("dark"), optString("server"), optString("accessToken"))
            }
        )
    }

    fun save(data: StudyData) {
        val root = JSONObject()
        root.put("cards", data.cards.asJson { JSONObject().put("id", id).put("front", front).put("back", back).put("generatedByAi", generatedByAi) })
        root.put("exams", data.exams.asJson {
            JSONObject().put("id", id).put("title", title).put("date", date.toString())
                .put("topics", JSONArray(topics)).put("quizIds", JSONArray(quizIds))
        })
        root.put("attempts", data.attempts.asJson {
            JSONObject().put("id", id).put("quizId", quizId).put("title", title).put("completedAt", completedAt)
                .put("correct", score.correct).put("incorrect", score.incorrect).put("skipped", score.skipped).put("ungraded", score.ungraded)
                .put("mistakes", mistakes.asJson {
                    JSONObject().put("question", question).put("given", given ?: JSONObject.NULL)
                        .put("correct", correct).put("explanation", explanation ?: JSONObject.NULL)
                })
        })
        root.put("settings", with(data.settings) {
            JSONObject().put("limit", limit).put("shuffleQuestions", shuffleQuestions).put("shuffleOptions", shuffleOptions)
                .put("showHints", showHints).put("showAnswers", showAnswers).put("minutes", minutes).put("dark", dark).put("server", server).put("accessToken", accessToken)
        })
        atomicWrite(file, root.toString())
    }

    private fun JSONObject.nullableText(key: String) = if (isNull(key)) null else getString(key)
    private fun JSONObject.strings(key: String): List<String> =
        getJSONArray(key).let { array -> List(array.length(), array::getString) }

    private fun <T> JSONObject.readObjects(key: String, transform: JSONObject.() -> T): List<T> {
        if (!has(key)) return emptyList()
        val array = getJSONArray(key)
        return List(array.length()) { array.getJSONObject(it).transform() }
    }

    private fun <T> List<T>.asJson(transform: T.() -> JSONObject): JSONArray =
        JSONArray().also { array -> forEach { array.put(it.transform()) } }
}
