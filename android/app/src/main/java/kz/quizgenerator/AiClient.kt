package kz.quizgenerator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

internal data class AiReview(val summary: String, val steps: List<String>)

internal class AiClient(private val settings: StudySettings, private val authToken: suspend () -> String = { error("Войдите в аккаунт для использования ИИ") }, private val endpoint: String = ProductionConfig.SERVER) {
        suspend fun extract(mime: String, data: String): String = request("extract",
        JSONObject().put("mimeType", mime).put("data", data)).getString("text").also {
        require(it.isNotBlank() && it.length <= 12_000) { "Не удалось распознать текст или документ слишком длинный" }
    }

    suspend fun configured(): Boolean = request("status").getBoolean("configured")
    suspend fun capabilities(): Set<String> {val value=request("status");if(!value.optBoolean("configured"))return emptySet();val a=value.optJSONArray("capabilities") ?: return setOf("quizzes","flashcards","review","extract");return (0 until a.length()).map {a.getString(it)}.toSet()}
    suspend fun batch(route: String,body: JSONObject,requestId: String): JSONObject {require(route in listOf("quizzes","lectures","answers"));require(UUID.fromString(requestId).toString()==requestId);return request(route,body,requestId)}

    suspend fun quiz(topic: String, material: String, difficulty: String, count: Int): Quiz {
        val result = request("quizzes", input(topic, material, difficulty, count))
        val quiz = parsed {QuizJson.parse(result.toString())}.let {parsed->parsed.copy(questions=parsed.questions.map {it.copy(evidence=AnswerEvidence.CLOUD_AI)})}
        require(quiz.generatedByAi && quiz.questions.size == count && quiz.questions.all {
            it.correctIndex != null && !it.explanation.isNullOrBlank()
        }) { "Сервер вернул неполный тест" }
        return quiz
    }

    suspend fun cards(topic: String, material: String, difficulty: String, count: Int): List<Flashcard> {
        val response=request("flashcards", input(topic, material, difficulty, count))
        val result = parsed {response.getJSONArray("cards")}
        require(result.length() == count) { "Сервер вернул неполный набор карточек" }
        val cards = List(result.length()) {
            val item = result.getJSONObject(it)
            parsed {Flashcard(UUID.randomUUID().toString(), item.getString("front").trim(), item.getString("back").trim(), true,evidence=AnswerEvidence.CLOUD_AI)}
        }
        require(cards.all { it.front.isNotBlank() && it.back.isNotBlank() }) { "В наборе есть пустая карточка" }
        return cards
    }

    suspend fun review(attempt: Attempt): AiReview {
        return reviewMaterial(reviewText(attempt))
    }

    suspend fun reviewMaterial(material: String): AiReview {
        val result = request("review", JSONObject().put("material", material))
        return parsed {val steps=result.getJSONArray("nextSteps");AiReview(result.getString("summary"),List(steps.length(),steps::getString))}
    }

    private fun <T> parsed(block: ()->T): T = try {block()} catch(_: org.json.JSONException) {error("ИИ вернул некорректный формат ответа. Попробуйте позже") }

    private fun input(topic: String, material: String, difficulty: String, count: Int) =
        AiGeneration.input(topic,material,difficulty,count)

    private suspend fun request(route: String, body: JSONObject? = null,requestId: String?=null): JSONObject = withContext(Dispatchers.IO) {
        if(body!=null)AiRequestRules.validate(route,body)
        val base = baseUri(endpoint)
        val token = authToken()
        val url = URI.create(base.toString().trimEnd('/') + "/api/v1/ai/$route").toURL()
        val connection = url.openConnection() as HttpURLConnection
        kotlinx.coroutines.suspendCancellableCoroutine<JSONObject> {pending ->
        pending.invokeOnCancellation {connection.disconnect()}
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 35000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.setRequestProperty("X-Request-ID", requestId ?: UUID.randomUUID().toString())
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
                throw AiServerFailure(status,when (status) {
                    401 -> "Войдите в аккаунт снова"
                    403 -> "Подтвердите электронную почту в личном кабинете"
                    404 -> "Новый серверный маршрут пока недоступен"
                    409 -> "Этот запрос уже принят сервером; результат не возвращён. Нужна проверка перед повтором"
                    429 -> "Лимит ИИ исчерпан. Попробуйте позже"
                    502, 503, 504 -> "ИИ временно недоступен. Попробуйте позже"
                    else -> detail?.ifBlank { null } ?: "Сервер вернул ошибку $status"
                })
            }
            if(pending.isActive)pending.resumeWith(Result.success(parsed {JSONObject(data)}))
        } catch(e: Exception) {if(pending.isActive)pending.resumeWith(Result.failure(e))} finally {
            connection.disconnect()
        }
        }
    }

    companion object {
        internal fun reviewText(attempt: Attempt): String = buildString {
            appendLine(attempt.title)
            attempt.mistakes.forEach {appendLine(it.question+" — ответ: "+(it.given ?: "пропущен")+"; верно: "+it.correct+". "+it.explanation.orEmpty())}
        }
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
