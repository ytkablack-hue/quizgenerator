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

internal data class Account(val uid: String, val email: String, val verified: Boolean, val name: String,
    val providers: Set<String> = setOf("password"))
internal val LocalAuth = staticCompositionLocalOf<AuthClient> { error("Auth not initialized") }

/** Only Firebase sessions are persisted. Google credentials and passwords never enter the vault. */
internal class AuthClient(private val vault: AuthSessionStore, private val gateway: AuthGateway = FirebaseRestGateway(), private val clearCredentials: suspend () -> Unit = {}) {
    constructor(context: Context): this(SessionVault(context.applicationContext),FirebaseRestGateway(),{GoogleCredentials.clear(context.applicationContext)})
    private val mutex=Mutex()
    private var config: JSONObject?=null
    private var session: JSONObject?=vault.load()
    var account by mutableStateOf(session?.account())
        private set

    private fun JSONObject.account(): Account {
        val providers=optJSONArray("providers")
        return Account(getString("localId"),optString("email"),optBoolean("emailVerified"),optString("displayName"),
            if(providers==null)setOf("password") else (0 until providers.length()).map { providers.getString(it) }.toSet())
    }
    private fun save(value: JSONObject?) { vault.save(value);session=value;account=value?.account() }
    private fun configuration(): JSONObject {
        if(config==null)config=gateway.config()
        return config!!
    }
    private fun apiKey()=configuration().getString("apiKey").also { require(it.isNotBlank()) { "Вход временно недоступен" } }
    private fun firebase(action: String, body: JSONObject)=gateway.firebase(action,apiKey(),body)
    suspend fun googleClientId(): String=withContext(Dispatchers.IO) {
        mutex.withLock { configuration().optString("googleClientId").takeIf { it.endsWith(".apps.googleusercontent.com") }
            ?: error("Вход с Google временно недоступен. Используйте электронную почту") }
    }
    private fun accept(value: JSONObject, expectedUid: String? = null, fallbackProvider: String = "password") {
        val uid=value.getString("localId")
        require(expectedUid==null || expectedUid==uid) { "Выберите Google-аккаунт этого профиля. Другой аккаунт не был изменён" }
        val user=firebase("lookup",JSONObject().put("idToken",value.getString("idToken"))).getJSONArray("users").getJSONObject(0)
        require(user.getString("localId")==uid) { "Не удалось подтвердить аккаунт" }
        val infos=user.optJSONArray("providerUserInfo")
        val providers=org.json.JSONArray()
        if(infos==null || infos.length()==0)providers.put(fallbackProvider)
        else for(i in 0 until infos.length())providers.put(infos.getJSONObject(i).getString("providerId"))
        save(JSONObject().put("localId",uid).put("email",user.optString("email",value.optString("email")))
            .put("idToken",value.getString("idToken")).put("refreshToken",value.getString("refreshToken"))
            .put("expiresAt",System.currentTimeMillis()+value.getString("expiresIn").toLong()*1000)
            .put("emailVerified",user.optBoolean("emailVerified")).put("displayName",user.optString("displayName"))
            .put("providers",providers))
    }
    suspend fun signIn(email: String,password: String,register: Boolean,expectedUid: String?=null)=withContext(Dispatchers.IO) {
        mutex.withLock {
            val address=AuthFields.email(email)
            require(password.length in 8..128) { "Введите пароль от 8 символов" }
            val value=firebase(if(register)"signUp" else "signInWithPassword",JSONObject().put("email",address).put("password",password).put("returnSecureToken",true))
            accept(value,expectedUid)
            if(register)firebase("sendOobCode",JSONObject().put("requestType","VERIFY_EMAIL").put("idToken",value.getString("idToken")))
        }
    }
    suspend fun signInGoogle(googleIdToken: String, expectedUid: String? = null, link: Boolean = false)=withContext(Dispatchers.IO) {
        val previous=if(link)token() else null
        mutex.withLock {
            require(googleIdToken.isNotBlank() && googleIdToken.length<=16_384) { "Не удалось получить вход Google" }
            require(!link || (expectedUid!=null && account?.uid==expectedUid)) { "Войдите снова перед связыванием" }
            val body=JSONObject().put("postBody","id_token="+java.net.URLEncoder.encode(googleIdToken,"UTF-8")+"&providerId=google.com")
                .put("requestUri","https://"+configuration().getString("projectId")+".firebaseapp.com")
                .put("returnSecureToken",true).put("returnIdpCredential",true)
            previous?.let { body.put("idToken",it) }
            val value=firebase("signInWithIdp",body)
            accept(value,expectedUid,"google.com")
        }
    }
    suspend fun token(force: Boolean=false): String=withContext(Dispatchers.IO) {
        mutex.withLock {
            val old=session ?: error("Войдите в аккаунт для использования ИИ")
            if(!force && old.optLong("expiresAt")>System.currentTimeMillis()+60_000)return@withLock old.getString("idToken")
            try {
                val value=gateway.refresh(apiKey(),old.getString("refreshToken"))
                val next=JSONObject(old.toString()).put("idToken",value.getString("id_token")).put("refreshToken",value.getString("refresh_token"))
                    .put("expiresAt",System.currentTimeMillis()+value.getString("expires_in").toLong()*1000)
                val user=firebase("lookup",JSONObject().put("idToken",next.getString("idToken"))).getJSONArray("users").getJSONObject(0)
                require(user.getString("localId")==old.getString("localId")) { "Не удалось подтвердить аккаунт" }
                next.put("emailVerified",user.optBoolean("emailVerified")).put("displayName",user.optString("displayName"))
                save(next);next.getString("idToken")
            } catch(e: ExpiredAuth) { save(null);throw e }
        }
    }
    suspend fun sendVerification()=withContext(Dispatchers.IO) { firebase("sendOobCode",JSONObject().put("requestType","VERIFY_EMAIL").put("idToken",token()));Unit }
    suspend fun reset(email: String)=withContext(Dispatchers.IO) { firebase("sendOobCode",JSONObject().put("requestType","PASSWORD_RESET").put("email",AuthFields.email(email)));Unit }
    suspend fun rename(name: String)=withContext(Dispatchers.IO) {
        require(name.trim().length in 1..80) { "Имя должно содержать 1–80 символов" }
        val idToken=token()
        mutex.withLock {
            firebase("update",JSONObject().put("idToken",idToken).put("displayName",name.trim()).put("returnSecureToken",false))
            session?.let { save(JSONObject(it.toString()).put("displayName",name.trim())) }
        }
    }
    suspend fun signOut()=withContext(Dispatchers.IO) {
        mutex.withLock { save(null) }
        withContext(kotlinx.coroutines.NonCancellable) { kotlinx.coroutines.withTimeoutOrNull(3000) { clearCredentials() } }
    }
    suspend fun deleteAccount(password: String) {
        val old=account ?: error("Войдите снова")
        signIn(old.email,password,false,old.uid)
        require(account?.uid==old.uid) { "Аккаунт изменился. Повторите вход" }
        deleteCurrent(old.uid)
    }
    suspend fun deleteWithGoogle(googleIdToken: String) {
        val uid=account?.uid ?: error("Войдите снова")
        signInGoogle(googleIdToken,uid)
        deleteCurrent(uid)
    }
    private suspend fun deleteCurrent(uid: String)=withContext(Dispatchers.IO) {
        val idToken=token()
        mutex.withLock { require(account?.uid==uid) { "Аккаунт изменился" };gateway.delete(idToken);save(null) }
        withContext(kotlinx.coroutines.NonCancellable) { kotlinx.coroutines.withTimeoutOrNull(3000) { clearCredentials() } }
    }
}

private class SessionVault(context: Context): AuthSessionStore {
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
    override fun load(): JSONObject? = runCatching {
        if (!file.exists()) return null
        val envelope = JSONObject(file.readText())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)))
        JSONObject(String(cipher.doFinal(Base64.decode(envelope.getString("data"), Base64.NO_WRAP)), Charsets.UTF_8))
    }.getOrNull() // A lost key signs out; local tests are never touched.
    override fun save(value: JSONObject?) {
        if (value == null) { check(!file.exists() || file.delete()) { "Не удалось завершить сеанс" }; return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        atomicWrite(file, JSONObject().put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)).toString())
    }
}

internal object AuthSessions {
    @Volatile private var instance: AuthClient?=null
    fun get(context: Context): AuthClient=synchronized(this) { instance ?: AuthClient(context.applicationContext).also { instance=it } }
}
