package kz.quizgenerator

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal class NetworkFailure(val status: Int, val code: String, message: String): IllegalStateException(message)

/** Never logs bodies, credentials or URLs containing provider keys. */
internal object NetworkJson {
    fun request(url: String, body: JSONObject? = null, bearer: String? = null, method: String? = null, readTimeoutMs: Int = 35_000): JSONObject {
        require(URL(url).protocol == "https") { "Нужен защищённый HTTPS-сервер" }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            bearer?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            connection.requestMethod = method ?: if (body == null) "GET" else "POST"
            body?.let {
                val encoded = it.toString().toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(encoded.size)
                connection.outputStream.use { output -> output.write(encoded) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytesBounded(262_144) } ?: byteArrayOf()
            val result = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrDefault(JSONObject())
            if (status !in 200..299) {
                val firebase = result.optJSONObject("error")?.optString("message").orEmpty().substringBefore(" :")
                throw NetworkFailure(status, firebase, when {
                    firebase in setOf("INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD", "EMAIL_NOT_FOUND") -> "Неверная почта или пароль"
                    firebase in setOf("EMAIL_EXISTS", "ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL") -> "Для этой почты уже есть аккаунт. Войдите по почте и подключите Google в профиле"
                    firebase in setOf("OPERATION_NOT_ALLOWED", "INVALID_IDP_RESPONSE") -> "Вход этим способом временно недоступен"
                    firebase == "FEDERATED_USER_ID_ALREADY_LINKED" -> "Этот Google-аккаунт уже связан с другим профилем"
                    firebase == "INVALID_EMAIL" -> "Проверьте адрес электронной почты"
                    firebase == "USER_DISABLED" -> "Аккаунт отключён"
                    firebase == "TOKEN_EXPIRED" || firebase == "INVALID_ID_TOKEN" -> "Сессия истекла. Войдите снова"
                    firebase.startsWith("WEAK_PASSWORD") -> "Используйте более надёжный пароль"
                    status == 401 -> "Войдите в аккаунт для использования ИИ"
                    status == 403 -> "Подтвердите электронную почту и обновите вход"
                    status == 429 || firebase == "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Лимит запросов исчерпан. Попробуйте позже"
                    status == 503 -> "Онлайн-сервис временно недоступен. Локальные тесты доступны"
                    else -> "Не удалось выполнить запрос. Попробуйте позже"
                })
            }
            return result
        } finally { connection.disconnect() }
    }
}

internal fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= limit) { "Ответ сервера слишком большой" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
