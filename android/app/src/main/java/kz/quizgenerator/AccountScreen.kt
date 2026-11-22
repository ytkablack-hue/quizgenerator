package kz.quizgenerator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun AccountScreen(onBack: () -> Unit, quizzes: List<Quiz> = emptyList(), study: StudyData = StudyData(), onSettings: () -> Unit = {}, onExport: () -> Unit = {}) {
    val auth=LocalAuth.current
    val user=auth.account
    if(user==null) { AuthEntryScreen();return }
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var name by remember(user.uid) { mutableStateOf(user.name) }
    var password by remember { mutableStateOf("") }
    var confirmEmail by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    fun run(action: suspend () -> String?) {
        if(busy)return
        busy=true;message=null;error=null
        scope.launch {
            try { message=action();password="" }
            catch(e: CancellationException) { throw e }
            catch(_: java.io.IOException) { error="Нет соединения. Локальная библиотека доступна без сети" }
            catch(e: Exception) { error=e.message?.take(180) ?: "Не удалось выполнить действие" }
            finally { busy=false }
        }
    }
    StudyColumn("Личный кабинет",onBack) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(64.dp),contentAlignment=Alignment.Center) {
                    Text(user.name.ifBlank { user.email }.take(1).uppercase(java.util.Locale.ROOT),style=MaterialTheme.typography.headlineLarge)
                }
            }
            Column { Text(user.name.ifBlank { "Мой профиль" },style=MaterialTheme.typography.titleLarge);Text(user.email) }
        }
        Text(if(user.verified)"Почта подтверждена" else "Подтвердите почту для доступа к ИИ")
        Text("Библиотека и история этого аккаунта доступны без интернета. Для Gemini нужен действующий вход и сеть.")
        if(!user.verified) {
            OutlinedButton(enabled=!busy,onClick={run { auth.sendVerification();"Письмо отправлено. Проверьте также Спам" }}) { Text("Отправить письмо подтверждения") }
            Button(enabled=!busy,onClick={run { auth.token(true);if(auth.account?.verified==true)"Почта подтверждена" else "Подтверждение пока не получено" }}) { Text("Я подтвердил почту") }
        }
        StudyField(name,{name=it.take(80)},"Имя пользователя")
        OutlinedButton(enabled=!busy && name.isNotBlank(),onClick={run { auth.rename(name);"Имя сохранено" }}) { Text("Сохранить профиль") }
        StudyPanel(tone=PanelTone.SKY) {
            Text("Мои тесты: "+quizzes.size)
            Text("Вопросов: "+quizzes.sumOf { it.questions.size })
            Text("Пройдено тестов: "+study.attempts.size)
            study.attempts.sortedByDescending { it.completedAt }.take(5).forEach {
                Text(it.title+" · "+displayDate(it.completedAt)+" · "+it.score.correct+"/"+it.score.gradedTotal)
            }
            if(study.attempts.isEmpty())Text("История появится после первой сохранённой попытки")
        }
        OutlinedButton(enabled=!busy,onClick=onSettings) { Text("Тема и настройки таймера") }
        Text("Тема: "+when(study.settings.theme) {"dark"->"тёмная";"light"->"светлая";else->"системная"})
        Text("Таймер: "+if(study.settings.minutes==0)"без ограничения" else study.settings.minutes.toString()+" мин")
        Text("Локальный ИИ: модель ещё не установлена. Подготовка офлайн-движка не означает, что ответы уже вычисляются на устройстве.")
        if("google.com" !in user.providers)OutlinedButton(enabled=!busy,onClick={run {
            val token=GoogleCredentials.obtain(context,auth.googleClientId())
            if(token==null)"Связывание отменено" else { auth.signInGoogle(token,user.uid,true);"Google подключён к этому профилю" }
        }}) { Text("Подключить Google к этому аккаунту") }
        else Text("Google подключён")
        TextButton(enabled=!busy,onClick={run { auth.reset(user.email);"Если пароль доступен для аккаунта, письмо восстановления отправлено" }}) { Text("Восстановить пароль") }
        OutlinedButton(enabled=!busy,onClick=onExport) { Text("Сохранить резервную копию библиотеки") }
        OutlinedButton(enabled=!busy,onClick={run { auth.signOut();null }}) { Text("Выйти") }
        TextButton(enabled=!busy,onClick={deleting=!deleting}) { Text("Удалить аккаунт") }
        if(deleting)StudyPanel(tone=PanelTone.LAVENDER) {
            Text("Будет удалена облачная учётная запись. Локальные файлы не удаляются, но новый аккаунт имеет другой UID и отдельную библиотеку. Сначала сохраните резервную копию важных материалов.")
            OutlinedTextField(confirmEmail,{confirmEmail=it.take(254)},label={Text("Для подтверждения введите email")},singleLine=true,enabled=!busy)
            if("password" in user.providers) {
                OutlinedTextField(password,{password=it.take(128)},label={Text("Пароль")},singleLine=true,visualTransformation=PasswordVisualTransformation(),enabled=!busy)
                Button(enabled=!busy && password.isNotBlank() && confirmEmail.trim()==user.email,onClick={run { auth.deleteAccount(password);null }}) { Text("Подтвердить удаление") }
            }
            if("google.com" in user.providers)Button(enabled=!busy && confirmEmail.trim()==user.email,onClick={run {
                val token=GoogleCredentials.obtain(context,auth.googleClientId())
                if(token==null)"Удаление отменено" else { auth.deleteWithGoogle(token);null }
            }}) { Text("Подтвердить через Google и удалить") }
        }
        if(busy)StudyLoading("Подождите…")
        message?.let { Text(it) };error?.let { StudyError(it) }
    }
}
