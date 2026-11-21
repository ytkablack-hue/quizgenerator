package kz.quizgenerator

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class Account(val uid: String, val email: String, val verified: Boolean, val name: String)
internal val LocalAuth = staticCompositionLocalOf<AuthClient> { error("Auth not initialized") }

/** Firebase owns passwords. Only encrypted refresh/ID tokens are retained locally. */
internal class AuthClient(context: Context) {
    private val vault = SessionVault(context.applicationContext)
    private val mutex = Mutex()
    private var config: JSONObject? = null
    private var session: JSONObject? = vault.load()
    var account by mutableStateOf(session?.account())
        private set

    private fun JSONObject.account() = Account(getString("localId"), optString("email"), optBoolean("emailVerified"), optString("displayName"))
    private fun save(value: JSONObject?) {
        vault.save(value)
        session = value
        account = value?.account()
    }
    private fun apiKey(): String {
        if (config == null) config = NetworkJson.request(ProductionConfig.SERVER + "/api/v1/auth/config")
        return config!!.getString("apiKey").also { require(it.isNotBlank()) { "Вход временно недоступен" } }
    }
    private fun firebase(action: String, body: JSONObject) = NetworkJson.request(
        "https://identitytoolkit.googleapis.com/v1/accounts:$action?key=" + apiKey(), body)

    suspend fun signIn(email: String, password: String, register: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(email.isNotBlank() && password.length >= 8) { "Введите почту и пароль от 8 символов" }
            val value = firebase(if (register) "signUp" else "signInWithPassword",
                JSONObject().put("email", email.trim()).put("password", password).put("returnSecureToken", true))
            value.put("expiresAt", System.currentTimeMillis() + value.getString("expiresIn").toLong() * 1000)
            val user = firebase("lookup", JSONObject().put("idToken", value.getString("idToken"))).getJSONArray("users").getJSONObject(0)
            value.put("emailVerified", user.optBoolean("emailVerified")).put("displayName", user.optString("displayName"))
            save(value)
            if (register) firebase("sendOobCode", JSONObject().put("requestType", "VERIFY_EMAIL").put("idToken", value.getString("idToken")))
        }
    }
    suspend fun token(force: Boolean = false): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val old = session ?: error("Войдите в аккаунт для использования ИИ")
            if (!force && old.optLong("expiresAt") > System.currentTimeMillis() + 60_000) return@withLock old.getString("idToken")
            val connection = java.net.URL("https://securetoken.googleapis.com/v1/token?key=" + apiKey()).openConnection() as java.net.HttpURLConnection
            try {
                connection.connectTimeout = 8_000; connection.readTimeout = 15_000; connection.instanceFollowRedirects = false
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                val encoded = ("grant_type=refresh_token&refresh_token=" + java.net.URLEncoder.encode(old.getString("refreshToken"), "UTF-8")).toByteArray()
                connection.outputStream.use { it.write(encoded) }
                if (connection.responseCode !in 200..299) {
                    if (connection.responseCode in setOf(400, 401, 403)) save(null)
                    error("Не удалось обновить вход. Попробуйте войти снова")
                }
                val value = JSONObject(String(connection.inputStream.use { it.readBytesBounded(65_536) }, Charsets.UTF_8))
                val next = JSONObject(old.toString()).put("idToken", value.getString("id_token"))
                    .put("refreshToken", value.getString("refresh_token"))
                    .put("expiresAt", System.currentTimeMillis() + value.getString("expires_in").toLong() * 1000)
                val user = firebase("lookup", JSONObject().put("idToken", next.getString("idToken"))).getJSONArray("users").getJSONObject(0)
                next.put("emailVerified", user.optBoolean("emailVerified")).put("displayName", user.optString("displayName"))
                save(next)
                next.getString("idToken")
            } finally { connection.disconnect() }
        }
    }
    suspend fun sendVerification() = withContext(Dispatchers.IO) {
        firebase("sendOobCode", JSONObject().put("requestType", "VERIFY_EMAIL").put("idToken", token()))
        Unit
    }
    suspend fun reset(email: String) = withContext(Dispatchers.IO) {
        require(email.isNotBlank()) { "Введите почту" }
        // Do not disclose whether an email is registered.
        firebase("sendOobCode", JSONObject().put("requestType", "PASSWORD_RESET").put("email", email.trim()))
        Unit
    }
    suspend fun rename(name: String) = withContext(Dispatchers.IO) {
        require(name.trim().length in 1..80) { "Имя должно содержать 1–80 символов" }
        firebase("update", JSONObject().put("idToken", token()).put("displayName", name.trim()).put("returnSecureToken", false))
        mutex.withLock { session?.let { save(JSONObject(it.toString()).put("displayName", name.trim())) } }
    }
    suspend fun signOut() = withContext(Dispatchers.IO) { mutex.withLock { save(null) } }
    suspend fun deleteAccount(password: String) {
        val email = account?.email ?: error("Войдите снова")
        signIn(email, password, false) // Firebase requires recent authentication.
        withContext(Dispatchers.IO) {
            val idToken = token()
            // Worker deletes the Firebase account before removing user-specific quota data.
            NetworkJson.request(ProductionConfig.SERVER + "/api/v1/account", bearer = idToken, method = "DELETE")
        }
        signOut()
    }
}

private class SessionVault(context: Context) {
    private val file = File(context.noBackupFilesDir, "auth-session.enc")
    private val alias = "testhelper.auth.session.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun load(): JSONObject? = runCatching {
        if (!file.exists()) return null
        val envelope = JSONObject(file.readText())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)))
        JSONObject(String(cipher.doFinal(Base64.decode(envelope.getString("data"), Base64.NO_WRAP)), Charsets.UTF_8))
    }.getOrNull() // A lost key signs out; local tests are never touched.
    fun save(value: JSONObject?) {
        if (value == null) { check(!file.exists() || file.delete()) { "Не удалось завершить сеанс" }; return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        atomicWrite(file, JSONObject().put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)).toString())
    }
}
