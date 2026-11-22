package kz.quizgenerator
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.IOException

class AuthClientTest {
    private class Vault(var value: JSONObject?=null): AuthSessionStore {
        override fun load()=value?.let { JSONObject(it.toString()) }
        override fun save(value: JSONObject?) { this.value=value?.let { JSONObject(it.toString()) } }
    }
    private class Gateway: AuthGateway {
        val actions=mutableListOf<String>()
        var uid="alice";var verified=true;var provider="password";var fail: Exception?=null
        var lastBody=JSONObject();var deletes=0
        override fun config()=JSONObject().put("projectId","testhelper-test").put("apiKey","public-client-key").put("googleClientId","123-test.apps.googleusercontent.com")
        fun response()=JSONObject().put("localId",uid).put("email","test@example.test").put("idToken","firebase-token")
            .put("refreshToken","refresh-token").put("expiresIn","3600").put("oauthAccessToken","must-not-persist")
        override fun firebase(action: String,apiKey: String,body: JSONObject): JSONObject {
            fail?.let { throw it };actions+=action;lastBody=body
            return if(action=="lookup")JSONObject().put("users",org.json.JSONArray().put(JSONObject().put("localId",uid)
                .put("email","test@example.test").put("emailVerified",verified).put("displayName","Ученик")
                .put("providerUserInfo",org.json.JSONArray().put(JSONObject().put("providerId",provider))))) else response()
        }
        override fun refresh(apiKey: String, refreshToken: String): JSONObject {
            fail?.let { throw it };actions+="refresh"
            return JSONObject().put("id_token","fresh-firebase-token").put("refresh_token","fresh-refresh").put("expires_in","3600")
        }
        override fun delete(idToken: String) { fail?.let { throw it };deletes++ }
    }
    @Test fun signupSendsVerificationAndStoresOnlyFirebaseSession()=runBlocking {
        val v=Vault();val g=Gateway().apply { verified=false };val auth=AuthClient(v,g)
        auth.signIn(" test@example.test ","safe-password",true)
        assertEquals(listOf("signUp","lookup","sendOobCode"),g.actions)
        assertFalse(auth.account!!.verified)
        assertEquals("VERIFY_EMAIL",g.lastBody.getString("requestType"))
        assertFalse(v.value.toString().contains("safe-password"));assertFalse(v.value.toString().contains("must-not-persist"))
    }
    @Test fun wrongPasswordAndFirebaseErrorsNeverCreateLocalSession()=runBlocking {
        for(e in listOf(NetworkFailure(400,"INVALID_LOGIN_CREDENTIALS","Неверная почта или пароль"),IOException("offline"))) {
            val v=Vault();val g=Gateway().apply { fail=e };val auth=AuthClient(v,g)
            try { auth.signIn("test@example.test","safe-password",false);fail("Expected failure") }catch(_: Exception){}
            assertNull(auth.account);assertNull(v.value)
        }
    }
    @Test fun restoredSessionDoesNotRequireAnyNetworkForLocalAccessAndLogoutPersists()=runBlocking {
        val v=Vault();val g=Gateway();AuthClient(v,g).signIn("test@example.test","safe-password",false)
        val offline=Gateway().apply { fail=IOException("offline") };val auth=AuthClient(v,offline)
        assertEquals("alice",auth.account!!.uid);assertTrue(offline.actions.isEmpty())
        assertEquals("firebase-token",auth.token())
        auth.signOut();assertNull(v.value);assertNull(AuthClient(v,offline).account)
    }
    @Test fun temporaryRefreshFailureRetainsOfflineSessionButRevocationSignsOut()=runBlocking {
        val v=Vault();val g=Gateway();val auth=AuthClient(v,g);auth.signIn("test@example.test","safe-password",false)
        g.fail=IOException("offline")
        try { auth.token(true);fail("Expected failure") }catch(_: IOException){}
        assertNotNull(auth.account);assertNotNull(v.value)
        g.fail=ExpiredAuth()
        try { auth.token(true);fail("Expected failure") }catch(_: ExpiredAuth){}
        assertNull(auth.account);assertNull(v.value)
    }
    @Test fun verificationRefreshAndPasswordRecoveryUseExistingAccount()=runBlocking {
        val v=Vault();val g=Gateway().apply { verified=false };val auth=AuthClient(v,g)
        auth.signIn("test@example.test","safe-password",false);g.verified=true
        assertEquals("fresh-firebase-token",auth.token(true));assertTrue(auth.account!!.verified)
        auth.reset("test@example.test");assertEquals("PASSWORD_RESET",g.lastBody.getString("requestType"))
    }
    @Test fun googleUsesFirebaseExchangeAndDoesNotPersistGoogleCredentials()=runBlocking {
        val v=Vault();val g=Gateway().apply { provider="google.com" };val auth=AuthClient(v,g)
        auth.signInGoogle("google-only-credential")
        assertEquals("signInWithIdp",g.actions.first());assertEquals(setOf("google.com"),auth.account!!.providers)
        assertFalse(v.value.toString().contains("google-only-credential"));assertFalse(v.value.toString().contains("must-not-persist"))
        val uid=auth.account!!.uid
        auth.signInGoogle("google-only-credential",uid,true)
        assertEquals(uid,auth.account!!.uid)
    }
    @Test fun deletionCannotSwitchToAnotherUsersGoogleOrPasswordAccount()=runBlocking {
        val v=Vault();val g=Gateway();val auth=AuthClient(v,g);auth.signIn("test@example.test","safe-password",false)
        g.uid="bob"
        try { auth.deleteWithGoogle("other-google-token");fail("Expected UID rejection") }catch(_: IllegalArgumentException){}
        assertEquals("alice",auth.account!!.uid);assertEquals(0,g.deletes)
        try { auth.deleteAccount("safe-password");fail("Expected UID rejection") }catch(_: IllegalArgumentException){}
        assertEquals("alice",auth.account!!.uid);assertEquals(0,g.deletes)
    }
    @Test fun successfulDeletionClearsSessionOnlyAfterBackendSuccess()=runBlocking {
        val v=Vault();val g=Gateway();val auth=AuthClient(v,g);auth.signIn("test@example.test","safe-password",false)
        auth.deleteAccount("safe-password");assertEquals(1,g.deletes);assertNull(auth.account);assertNull(v.value)
    }
    @Test fun registrationChecksEmailRepeatAndLength() {
        AuthFields.registration("me+study@example.test","12345678","12345678")
        for((email,password,again) in listOf(Triple("bad","12345678","12345678"),Triple("a@b.test","short","short"),Triple("a@b.test","12345678","87654321"))) {
            assertThrows(IllegalArgumentException::class.java) { AuthFields.registration(email,password,again) }
        }
    }
}
