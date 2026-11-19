package kz.quizgenerator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.quizgenerator.ui.components.*
import kz.quizgenerator.ui.theme.*
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun RegistrationScreen(nickname: String, email: String, password: String,
                       onNicknameChange: (String) -> Unit, onEmailChange: (String) -> Unit,
                       onPasswordChange: (String) -> Unit, onSubmit: () -> Unit,
                       onToggleLogin: () -> Unit, isLogin: Boolean, busy: Boolean, error: String?) {
    Column(Modifier.fillMaxSize().background(SteamBackground)) {
        Column(Modifier.fillMaxSize().weight(.55f).background(SteamNavy).padding(horizontal = 18.dp, vertical = 22.dp), verticalArrangement = Arrangement.Center) {
            Surface(color = SteamAccent, shape = RoundedCornerShape(7.dp)) { Text("✓", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, fontSize = 25.sp) }
            Text("TESTHELPER", Modifier.padding(top = 12.dp), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("Создавай тесты за минуту. Учись с прогрессом.", Modifier.padding(top = 8.dp), color = Color(0xFFCCD8E5), fontSize = 14.sp)
        }
        Column(Modifier.fillMaxSize().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (isLogin) "Вход" else "Регистрация", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = SteamNavy)
            Text("Профиль сохраняется на этом устройстве", fontSize = 11.sp, color = SteamMuted)
            if (!isLogin) SteamField("Ник", nickname, onNicknameChange, hint = "Как тебя называть")
            SteamField("Email", email, onEmailChange, hint = "name@mail.kz", keyboardType = KeyboardType.Email)
            SteamField("Пароль", password, onPasswordChange, hint = "Не короче 8 символов", password = true)
            error?.let { Text(it, color = Color(0xFFB63A3A)) }
            SteamButton(if (busy) "Подождите…" else if (isLogin) "Войти" else "Создать аккаунт", enabled = !busy, onClick = onSubmit)
            SteamButton(if (isLogin) "Нет аккаунта? Создать" else "Уже есть аккаунт? Войти", tone = SteamTone.TEXT, onClick = onToggleLogin)
        }
    }
}
