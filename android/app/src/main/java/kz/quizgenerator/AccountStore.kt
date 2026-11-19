package kz.quizgenerator

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal data class LocalProfile(val nickname: String, val email: String)

/** Stores a local profile only. The current backend has no account endpoints. */
internal class AccountStore(private val file: File) {
    fun exists() = file.isFile
    fun profile(): LocalProfile? = runCatching { JSONObject(file.readText()).let { LocalProfile(it.getString("nickname"), it.getString("email")) } }.getOrNull()
    fun register(nickname: String, email: String, password: String): LocalProfile {
        require(nickname.trim().length >= 2) { "Введите имя (не короче 2 символов)" }
        require(email.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) { "Проверьте адрес email" }
        require(password.length >= 8) { "Пароль должен содержать не менее 8 символов" }
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = derive(password, salt)
        atomicWrite(file, JSONObject().put("nickname", nickname.trim().take(64)).put("email", email.trim().lowercase())
            .put("salt", salt.joinToString("") { "%02x".format(it) }).put("passwordHash", hash.joinToString("") { "%02x".format(it) }).toString())
        return LocalProfile(nickname.trim(), email.trim().lowercase())
    }
    fun login(email: String, password: String): LocalProfile {
        val data = JSONObject(file.readText())
        require(email.trim().equals(data.getString("email"), ignoreCase = true)) { "Неверный email или пароль" }
        val salt = data.getString("salt").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val expected = data.getString("passwordHash").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        require(MessageDigest.isEqual(expected, derive(password, salt))) { "Неверный email или пароль" }
        return LocalProfile(data.getString("nickname"), data.getString("email"))
    }
    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
}
