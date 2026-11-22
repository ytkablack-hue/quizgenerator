package kz.quizgenerator
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

internal object GoogleCredentials {
    suspend fun obtain(context: Context, clientId: String): String? = try {
        val bytes=ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val nonce=android.util.Base64.encodeToString(bytes,android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
        val request=GetCredentialRequest.Builder().addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).setNonce(nonce).build()).build()
        val credential=CredentialManager.create(context).getCredential(context=context,request=request).credential
        require(credential is CustomCredential && credential.type==GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) { "Не удалось распознать вход Google" }
        val token=GoogleIdTokenCredential.createFrom(credential.data).idToken
        val parts=token.split('.');require(parts.size==3)
        val payload=org.json.JSONObject(String(android.util.Base64.decode(parts[1],android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP),Charsets.UTF_8))
        require(payload.optString("nonce")==nonce) { "Ответ Google не соответствует запросу" }
        token
    } catch(_: GetCredentialCancellationException) { null }
      catch(e: kotlinx.coroutines.CancellationException) { throw e }
      catch(_: Exception) { throw IllegalStateException("Не удалось войти с Google. Повторите попытку или используйте электронную почту") }
    suspend fun clear(context: Context) { runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) } }
}
