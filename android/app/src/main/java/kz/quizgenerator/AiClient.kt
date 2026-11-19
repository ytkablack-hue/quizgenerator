package kz.quizgenerator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

internal data class AiReview(val summary: String, val steps: List<String>)

internal class AiClient(private val settings: StudySettings) {
    suspend fun configured(): Boolean = request("status").getBoolean("configured")

    suspend fun quiz(topic: String, material: String, difficulty: String, count: Int): Quiz {
        val result = request("quizzes", input(topic, material, difficulty, count))
        val quiz = QuizJson.parse(result.toString())
        require(quiz.generatedByAi && quiz.questions.size == count && quiz.questions.all {
            it.correctIndex != null && !it.explanation.isNullOrBlank()
        }) { "Сервер вернул неполный тест" }
        return quiz
    }

    suspend fun cards(topic: String, material: String, difficulty: String, count: Int): List<Flashcard> {
        val result = request("flashcards", input(topic, material, difficulty, count)).getJSONArray("cards")
        require(result.length() == count) { "Сервер вернул неполный набор карточек" }
        val cards = List(result.length()) {
            val item = result.getJSONObject(it)
            Flashcard(UUID.randomUUID().toString(), item.getString("front").trim(), item.getString("back").trim(), true)
        }
        require(cards.all { it.front.isNotBlank() && it.back.isNotBlank() }) { "В наборе есть пустая карточка" }
        return cards
    }

    suspend fun review(attempt: Attempt): AiReview {
        val material = buildString {
            appendLine(attempt.title)
            attempt.mistakes.forEach {
                appendLine("${it.question} — ответ: ${it.given ?: "пропущен"}; верно: ${it.correct}. ${it.explanation.orEmpty()}")
            }
        }.take(12_000)
        val result = request("review", JSONObject().put("material", material))
        val steps = result.getJSONArray("nextSteps")
        return AiReview(result.getString("summary"), List(steps.length(), steps::getString))
    }

    private fun input(topic: String, material: String, difficulty: String, count: Int) =
        JSONObject().put("topic", topic.trim()).put("material", material.trim())
            .put("difficulty", difficulty).put("count", count)

    private suspend fun request(route: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val base = baseUri(settings.server)
        val url = URI.create(base.toString().trimEnd('/') + "/api/v1/ai/$route").toURL()
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 35000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            if (settings.accessToken.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer ${settings.accessToken}")
            }
            if (body != null) {
                val encoded = body.toString().toByteArray(Charsets.UTF_8)
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(encoded.size)
                connection.outputStream.use { it.write(encoded) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val data = stream?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val size = input.read(buffer)
                    if (size < 0) break
                    require(output.size() + size <= 262_144) { "Слишком большой ответ сервера" }
                    output.write(buffer, 0, size)
                }
                output.toString("UTF-8")
            }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(data).optString("detail").take(180) }.getOrNull()
                error(detail?.ifBlank { null } ?: "Сервер вернул ошибку $status")
            }
            JSONObject(data)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        internal fun baseUri(value: String): URI {
            val uri = runCatching { URI(value.trim()) }.getOrNull()
            require(uri != null && uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                (uri.scheme == "https" || (uri.scheme == "http" && uri.host in listOf("localhost", "127.0.0.1", "10.0.2.2")))) {
                "Введите HTTPS-адрес сервера. HTTP доступен для локального тестирования."
            }
            return uri
        }
    }
}
