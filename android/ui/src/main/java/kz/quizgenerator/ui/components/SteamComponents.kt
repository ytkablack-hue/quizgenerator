package kz.quizgenerator.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.quizgenerator.ui.theme.*

enum class SteamTone { GREEN, BLUE, PALE, TEXT }

@Composable
fun SteamTopBar(title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null,
                trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().background(SteamNavy).statusBarsPadding().height(48.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) { TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp)) { Text("‹", color = Color.White, fontSize = 27.sp) }; Spacer(Modifier.width(8.dp)) }
        else { Surface(color = SteamAccent, shape = RoundedCornerShape(5.dp), modifier = Modifier.size(22.dp)) { Box(contentAlignment = Alignment.Center) { Text("✓", color = Color.White, fontWeight = FontWeight.Bold) } }; Spacer(Modifier.width(9.dp)) }
        Text(title, Modifier.weight(1f), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.let { Row(content = it) }
    }
}

@Composable
fun SteamCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = SteamSurface),
        border = BorderStroke(1.dp, SteamBorder), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp), content = content)
    }
}

@Composable
fun SteamButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, tone: SteamTone = SteamTone.GREEN, onClick: () -> Unit) {
    when (tone) {
        SteamTone.TEXT -> TextButton(onClick, modifier.fillMaxWidth(), enabled = enabled) { Text(text, fontWeight = FontWeight.SemiBold) }
        SteamTone.PALE -> OutlinedButton(onClick, modifier.fillMaxWidth(), enabled = enabled,
            colors = ButtonDefaults.outlinedButtonColors(containerColor = SteamPaleBlue, contentColor = SteamText),
            border = BorderStroke(0.dp, Color.Transparent), shape = RoundedCornerShape(7.dp)) { Text(text, fontWeight = FontWeight.SemiBold) }
        else -> Button(onClick, modifier.fillMaxWidth(), enabled = enabled, shape = RoundedCornerShape(7.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (tone == SteamTone.BLUE) SteamBlue else SteamGreen)) { Text(text, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun SteamField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
               hint: String = "", singleLine: Boolean = true, password: Boolean = false,
               keyboardType: KeyboardType = KeyboardType.Text) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        BasicTextField(value, onValueChange, modifier = Modifier.fillMaxWidth(), singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = SteamText),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType,
                imeAction = if (password) ImeAction.Done else if (singleLine) ImeAction.Next else ImeAction.Default),
            decorationBox = { inner -> Box(Modifier.fillMaxWidth().border(1.dp, SteamBorder, RoundedCornerShape(7.dp))
                .background(Color.White, RoundedCornerShape(7.dp)).defaultMinSize(minHeight = if (singleLine) 40.dp else 78.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp)) { if (value.isEmpty() && hint.isNotEmpty()) Text(hint, color = SteamMuted); inner() } })
    }
}

@Composable fun SteamHeading(title: String, detail: String? = null) {
    Column { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SteamMuted) } }
}

@Composable fun SteamProgress(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(5.dp).background(SteamBorder, RoundedCornerShape(50))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(5.dp).background(SteamAccent, RoundedCornerShape(50)))
    }
}

@Composable fun SteamPage(title: String, onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(SteamBackground)) { SteamTopBar(title, onBack = onBack)
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
