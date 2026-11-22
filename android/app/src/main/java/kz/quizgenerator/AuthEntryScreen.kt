package kz.quizgenerator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun AuthEntryScreen() {
    val auth=LocalAuth.current
    val context=LocalContext.current
    val uri=LocalUriHandler.current
    val scope=rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf("welcome") }
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords deliberately do not enter saved instance state.
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    fun run(action: suspend () -> String?) {
        if(busy)return
        busy=true;error=null;message=null
        scope.launch {
            try { message=action();password="";repeat="" }
            catch(e: CancellationException) { throw e }
            catch(_: java.io.IOException) { error="Нет соединения. Для первого входа нужен интернет. Повторите попытку позже" }
            catch(e: Exception) { error=e.message?.take(180) ?: "Не удалось войти. Попробуйте ещё раз" }
            finally { busy=false }
        }
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer,MaterialTheme.colorScheme.surface,MaterialTheme.colorScheme.secondaryContainer)))) {
        Column(Modifier.align(Alignment.Center).widthIn(max=480.dp).fillMaxWidth().safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary) {
                Box(Modifier.size(72.dp),contentAlignment=Alignment.Center) { Text("T",style=MaterialTheme.typography.displayMedium) }
            }
            Text("TestHelper",style=MaterialTheme.typography.headlineLarge)
            Text(if(mode=="login")"С возвращением" else "Готовься с уверенностью",style=MaterialTheme.typography.titleLarge)
            Text("Создавай тесты, запоминай материал и готовься к экзаменам")
            Text("Войдите один раз. Затем локальная библиотека работает без интернета.",style=MaterialTheme.typography.bodySmall)
            Button(enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),onClick={run {
                val credential=GoogleCredentials.obtain(context,auth.googleClientId())
                if(credential==null)"Вход Google отменён" else { auth.signInGoogle(credential);null }
            }}) { Text("Продолжить с Google") }
            if(mode=="welcome") {
                OutlinedButton(enabled=!busy,onClick={mode="signup";error=null},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text("Зарегистрироваться по электронной почте") }
                TextButton(enabled=!busy,onClick={mode="login";error=null}) { Text("Уже есть аккаунт? Войти") }
            } else {
                OutlinedTextField(email,{email=it.take(254)},label={Text("Email")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(password,{password=it.take(128)},label={Text("Пароль")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth(),
                    visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon={TextButton(onClick={visible=!visible}) { Text(if(visible)"Скрыть" else "Показать") }})
                if(mode=="signup")OutlinedTextField(repeat,{repeat=it.take(128)},label={Text("Повтор пароля")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth(),
                    visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation())
                Button(enabled=!busy,modifier=Modifier.fillMaxWidth(),onClick={run {
                    if(mode=="signup")AuthFields.registration(email,password,repeat)
                    auth.signIn(email,password,mode=="signup");null
                }}) { Text(if(mode=="signup")"Зарегистрироваться" else "Войти") }
                if(mode=="login")TextButton(enabled=!busy,onClick={run { auth.reset(email);"Если аккаунт существует, письмо восстановления отправлено" }}) { Text("Восстановить пароль") }
                TextButton(enabled=!busy,onClick={mode=if(mode=="signup")"login" else "signup";password="";repeat="";error=null}) { Text(if(mode=="signup")"Уже есть аккаунт? Войти" else "Создать аккаунт") }
            }
            if(busy)CircularProgressIndicator()
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            message?.let { Text(it) }
            TextButton(onClick={uri.openUri("https://github.com/ytkablack-hue/quizgenerator/blob/feature/android-mvp/docs/testhelper-privacy-draft.md")}) { Text("Политика конфиденциальности") }
            TextButton(onClick={uri.openUri("https://github.com/ytkablack-hue/quizgenerator/blob/feature/android-mvp/docs/testhelper-beta-terms.md")}) { Text("Условия использования") }
        }
    }
}
