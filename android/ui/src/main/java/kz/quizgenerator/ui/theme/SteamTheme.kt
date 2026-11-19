package kz.quizgenerator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SteamNavy = Color(0xFF1B2838)
val SteamBackground = Color(0xFFE3EAF2)
val SteamSurface = Color(0xFFFFFFFF)
val SteamBorder = Color(0xFFC7D4E1)
val SteamGreen = Color(0xFF4E8216)
val SteamBlue = Color(0xFF2A6FC9)
val SteamAccent = Color(0xFF1A9FFF)
val SteamText = Color(0xFF26384A)
val SteamMuted = Color(0xFF607489)
val SteamPaleBlue = Color(0xFFDCEBFA)
val SteamPaleGreen = Color(0xFFE7F1DA)
val SteamWarning = Color(0xFF9A6A14)
val SteamPaleWarning = Color(0xFFFFF0CE)

@Composable
fun SteamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = SteamGreen, onPrimary = Color.White, secondary = SteamBlue,
        background = SteamBackground, surface = SteamSurface, onSurface = SteamText,
        onBackground = SteamText, primaryContainer = SteamPaleGreen,
        secondaryContainer = SteamPaleBlue, outline = SteamBorder
    ), content = content)
}
