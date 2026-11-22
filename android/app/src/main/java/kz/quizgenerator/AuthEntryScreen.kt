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
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background,contentColor=MaterialTheme.colorScheme.onBackground) {
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.TopCenter).widthIn(max=480.dp).fillMaxWidth().safeDrawingPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal=22.dp,vertical=20.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(vertical=16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surface,tonalElevation=1.dp) {kz.quizgenerator.ui.components.TestHelperMark(Modifier.padding(20.dp).size(if(mode=="welcome")56.dp else 32.dp))}
                Text("TestHelper",style=MaterialTheme.typography.headlineLarge)
                Text("Твои знания. Твой прогресс.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(mode=="welcome") {
                listOf(Triple(Glyph.FILE,"Материал всегда под рукой","PDF, Word, текст и фото — локально"),Triple(Glyph.AI,"Тесты и карточки","По теме, документу или своему тексту"),Triple(Glyph.PLAN,"Подготовка без догадок","История, ошибки и сохранённый прогресс")).forEach {(icon,title,detail)->
                    StudyPanel {Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.small){StudyIcon(icon,Modifier.padding(12.dp))};Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleMedium);Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
                }
                Spacer(Modifier.height(12.dp))
            } else Text(if(mode=="signup")"Создать аккаунт" else "С возвращением",style=MaterialTheme.typography.headlineSmall)
            GoogleBrandButton(!busy) {run {
                val credential=GoogleCredentials.obtain(context,auth.googleClientId())
                if(credential==null)"Вход Google отменён" else {auth.signInGoogle(credential);null}
            }}
            if(mode=="welcome") {
                StudyPrimary("Войти по почте",enabled=!busy,onClick={mode="login";error=null})
                TextButton(enabled=!busy,onClick={mode="signup";error=null},modifier=Modifier.fillMaxWidth()) {Text("Нет аккаунта? Зарегистрироваться")}
                Text("После первого входа сохранённые материалы доступны без интернета.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                OutlinedTextField(email,{email=it.take(254)},label={Text("Email")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Email))
                OutlinedTextField(password,{password=it.take(128)},label={Text("Пароль")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth(),
                    visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon={TextButton(onClick={visible=!visible}){Text(if(visible)"Скрыть" else "Показать")}})
                if(mode=="signup")OutlinedTextField(repeat,{repeat=it.take(128)},label={Text("Повтор пароля")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth(),visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation())
                Button(enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),onClick={run {
                    if(mode=="signup")AuthFields.registration(email,password,repeat)
                    auth.signIn(email,password,mode=="signup");null
                }}) {Text(if(mode=="signup")"Создать аккаунт" else "Войти")}
                if(mode=="login")TextButton(enabled=!busy,onClick={run {auth.reset(email);"Если аккаунт существует, письмо восстановления отправлено"}}) {Text("Восстановить пароль")}
                TextButton(enabled=!busy,onClick={mode=if(mode=="signup")"login" else "signup";password="";repeat="";error=null}) {Text(if(mode=="signup")"Уже есть аккаунт? Войти" else "Создать аккаунт")}
            }
            if(busy)CircularProgressIndicator()
            error?.let {StudyError(it)};message?.let {Text(it)}
            Text("Продолжая, ознакомьтесь с условиями и политикой обработки данных.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={uri.openUri("https://github.com/ytkablack-hue/quizgenerator/blob/feature/android-mvp/docs/testhelper-privacy-draft.md")},modifier=Modifier.weight(1f)) {Text("Конфиденциальность",style=MaterialTheme.typography.labelSmall)}
                TextButton(onClick={uri.openUri("https://github.com/ytkablack-hue/quizgenerator/blob/feature/android-mvp/docs/testhelper-beta-terms.md")},modifier=Modifier.weight(1f)) {Text("Условия",style=MaterialTheme.typography.labelSmall)}
            }
        }
        }
    }
}
