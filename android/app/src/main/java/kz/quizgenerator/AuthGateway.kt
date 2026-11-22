package kz.quizgenerator

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal interface AuthSessionStore {
    fun load(): JSONObject?
    fun save(value: JSONObject?)
}
internal interface AuthGateway {
    fun config(): JSONObject
    fun firebase(action: String, apiKey: String, body: JSONObject): JSONObject
    fun refresh(apiKey: String, refreshToken: String): JSONObject
    fun delete(idToken: String)
}
internal class ExpiredAuth: IllegalStateException("Сессия истекла. Войдите снова")
internal class FirebaseRestGateway: AuthGateway {
    override fun config() = NetworkJson.request(ProductionConfig.SERVER + "/api/v1/auth/config", readTimeoutMs=15_000)
    override fun firebase(action: String, apiKey: String, body: JSONObject) = NetworkJson.request(
        "https://identitytoolkit.googleapis.com/v1/accounts:"+action+"?key="+URLEncoder.encode(apiKey,"UTF-8"),body,readTimeoutMs=15_000)
    override fun refresh(apiKey: String, refreshToken: String): JSONObject {
        val c=URL("https://securetoken.googleapis.com/v1/token?key="+URLEncoder.encode(apiKey,"UTF-8")).openConnection() as HttpURLConnection
        try {
            c.connectTimeout=8_000;c.readTimeout=15_000;c.instanceFollowRedirects=false
            c.requestMethod="POST";c.doOutput=true
            c.setRequestProperty("Content-Type","application/x-www-form-urlencoded")
            val bytes=("grant_type=refresh_token&refresh_token="+URLEncoder.encode(refreshToken,"UTF-8")).toByteArray()
            c.setFixedLengthStreamingMode(bytes.size);c.outputStream.use { it.write(bytes) }
            val status=c.responseCode
            val stream=if(status in 200..299)c.inputStream else c.errorStream
            val body=stream?.use { JSONObject(String(it.readBytesBounded(65_536),Charsets.UTF_8)) } ?: JSONObject()
            if(status !in 200..299) {
                val code=body.optJSONObject("error")?.optString("message").orEmpty()
                if(code in setOf("INVALID_GRANT","INVALID_REFRESH_TOKEN","TOKEN_EXPIRED","USER_DISABLED","USER_NOT_FOUND")) throw ExpiredAuth()
                throw NetworkFailure(status,code,"Не удалось обновить вход. Локальные данные доступны без сети")
            }
            return body
        } finally { c.disconnect() }
    }
    override fun delete(idToken: String) { NetworkJson.request(ProductionConfig.SERVER+"/api/v1/account",bearer=idToken,method="DELETE") }
}

internal object AuthFields {
    fun email(value: String): String = value.trim().also {
        require(it.length in 3..254 && Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(it)) { "Проверьте электронную почту" }
    }
    fun registration(email: String, password: String, repeated: String) {
        AuthFields.email(email)
        require(password.length in 8..128) { "Пароль должен содержать от 8 до 128 символов" }
        require(password==repeated) { "Пароли не совпадают" }
    }
}
