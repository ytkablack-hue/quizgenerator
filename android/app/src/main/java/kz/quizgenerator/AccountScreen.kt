package kz.quizgenerator

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun AccountScreen(onBack: () -> Unit) {
    val auth = LocalAuth.current
    val user = auth.account
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember(user?.uid) { mutableStateOf(user?.name.orEmpty()) }
    var register by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    fun run(action: suspend () -> String) {
        if (busy) return
        busy = true; error = null; message = null
        scope.launch {
            try { message = action(); password = "" }
            catch (e: CancellationException) { throw e }
            catch (_: java.io.IOException) { error = "Нет соединения. Локальные тесты доступны без входа" }
            catch (e: Exception) { error = e.message?.take(180) ?: "Не удалось выполнить действие" }
            finally { busy = false }
        }
    }
    StudyColumn("Личный кабинет", onBack) {
        Text("Вход нужен только для ИИ. Ваши тесты и история хранятся на устройстве.")
        if (user == null) {
            Text(if (register) "Регистрация" else "Вход", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(email, { email = it.take(254) }, label = { Text("Электронная почта") }, enabled = !busy,
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it.take(128) }, label = { Text("Пароль (от 8 символов)") }, enabled = !busy,
                singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Button(enabled = !busy && email.isNotBlank() && password.length >= 8, onClick = {
                run { auth.signIn(email, password, register); if (register) "Письмо подтверждения отправлено. Проверьте почту" else "Вход выполнен" }
            }) { Text(if (register) "Зарегистрироваться" else "Войти") }
            TextButton(enabled = !busy, onClick = { register = !register }) { Text(if (register) "Уже есть аккаунт" else "Создать аккаунт") }
            TextButton(enabled = !busy && email.isNotBlank(), onClick = { run { auth.reset(email); "Если аккаунт существует, письмо восстановления отправлено" } }) { Text("Восстановить пароль") }
        } else {
            Text(user.email, style = MaterialTheme.typography.titleLarge)
            Text(if (user.verified) "Почта подтверждена" else "Подтвердите почту для доступа к ИИ")
            if (!user.verified) {
                OutlinedButton(enabled = !busy, onClick = { run { auth.sendVerification(); "Письмо отправлено" } }) { Text("Отправить письмо подтверждения") }
                Button(enabled = !busy, onClick = { run { auth.token(true); if (auth.account?.verified == true) "Почта подтверждена" else "Подтверждение пока не получено" } }) { Text("Я подтвердил почту") }
            }
            StudyField(name, { name = it.take(80) }, "Имя")
            OutlinedButton(enabled = !busy && name.isNotBlank(), onClick = { run { auth.rename(name); "Имя сохранено" } }) { Text("Сохранить профиль") }
            OutlinedButton(enabled = !busy, onClick = { run { auth.signOut(); "Вы вышли из аккаунта" } }) { Text("Выйти") }
            TextButton(enabled = !busy, onClick = { deleting = !deleting }) { Text("Удалить аккаунт") }
            if (deleting) StudyPanel {
                Text("Будет удалена учётная запись Firebase. Тесты на устройстве сохранятся. Введите пароль и подтвердите удаление.")
                OutlinedTextField(password, { password = it.take(128) }, label = { Text("Пароль") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Button(enabled = !busy && password.isNotBlank(), onClick = { run { auth.deleteAccount(password); deleting = false; "Аккаунт удалён" } }) { Text("Подтвердить удаление аккаунта") }
            }
        }
        if (busy) StudyLoading("Подождите…")
        message?.let { Text(it) }; error?.let { StudyError(it) }
        TextButton(enabled = !busy, onClick = onBack) { Text("Продолжить локально") }
    }
}
