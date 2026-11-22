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
import androidx.compose.runtime.CompositionLocalProvider
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
                trailing: (@Composable RowScope.() -> Unit)? = null, applyStatusBarInsets: Boolean = true) {
    Row(modifier.fillMaxWidth().background(SteamSurface).then(if (applyStatusBarInsets) Modifier.statusBarsPadding() else Modifier).heightIn(min = 56.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) { TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp), modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("‹", color = SteamText, fontSize = 27.sp) }; Spacer(Modifier.width(8.dp)) }
        else {TestHelperMark(Modifier.size(28.dp));Spacer(Modifier.width(10.dp))}
        Text(title, Modifier.weight(1f), color = SteamText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.let { trailingContent -> CompositionLocalProvider(LocalContentColor provides SteamText) { Row(content = trailingContent) } }
    }
}

@Composable
fun SteamCard(modifier: Modifier = Modifier, background: Color = SteamSurface, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = background),
        border = BorderStroke(1.dp, SteamBorder), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun SteamButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, tone: SteamTone = SteamTone.GREEN, onClick: () -> Unit) {
    when (tone) {
        SteamTone.TEXT -> TextButton(onClick, modifier.fillMaxWidth().heightIn(min=48.dp), enabled = enabled) { Text(text, fontWeight = FontWeight.SemiBold) }
        SteamTone.PALE -> OutlinedButton(onClick, modifier.fillMaxWidth().heightIn(min=48.dp), enabled = enabled,
            colors = ButtonDefaults.outlinedButtonColors(containerColor = SteamPaleBlue, contentColor = SteamText),
            border = BorderStroke(0.dp, Color.Transparent), shape = RoundedCornerShape(14.dp)) { Text(text, fontWeight = FontWeight.SemiBold) }
        else -> Button(onClick, modifier.fillMaxWidth().heightIn(min=48.dp), enabled = enabled, shape = RoundedCornerShape(14.dp),
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
            decorationBox = { inner -> Box(Modifier.fillMaxWidth().border(1.dp, SteamBorder, RoundedCornerShape(14.dp))
                .background(SteamSurface, RoundedCornerShape(14.dp)).defaultMinSize(minHeight = if (singleLine) 48.dp else 90.dp)
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

@Composable fun SteamPage(title: String, onBack: (() -> Unit)? = null, showHeader: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(SteamBackground)) { if(showHeader)SteamTopBar(title, onBack = onBack, applyStatusBarInsets = false)
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
