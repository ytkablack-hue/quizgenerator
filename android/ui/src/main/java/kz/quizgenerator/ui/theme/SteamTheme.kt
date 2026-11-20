package kz.quizgenerator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Original COIN palette, with matching dark surfaces and readable text.
val SteamNavy = Color(0xFF1B2838)
val SteamGreen: Color @Composable get() = MaterialTheme.colorScheme.primary
val SteamBlue: Color @Composable get() = MaterialTheme.colorScheme.secondary
val SteamAccent: Color @Composable get() = MaterialTheme.colorScheme.tertiary
val SteamBackground: Color @Composable get() = MaterialTheme.colorScheme.background
val SteamSurface: Color @Composable get() = MaterialTheme.colorScheme.surface
val SteamBorder: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
val SteamText: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val SteamMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val SteamPaleBlue: Color @Composable get() = MaterialTheme.colorScheme.secondaryContainer
val SteamPaleGreen: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
val SteamWarning: Color @Composable get() = MaterialTheme.colorScheme.error
val SteamPaleWarning: Color @Composable get() = MaterialTheme.colorScheme.errorContainer

@Composable
fun SteamTheme(mode: String = "system", content: @Composable () -> Unit) {
    val useDark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val scheme = if (useDark) darkColorScheme(
        primary = Color(0xFFA6D37B), onPrimary = Color(0xFF173200),
        secondary = Color(0xFF91C8FF), onSecondary = Color(0xFF002F50),
        tertiary = Color(0xFF73C2FF), onTertiary = Color(0xFF002E4B),
        background = Color(0xFF101923), surface = Color(0xFF1B2838), surfaceContainer = Color(0xFF223448),
        onBackground = Color(0xFFE1EAF5), onSurface = Color(0xFFE1EAF5), onSurfaceVariant = Color(0xFFB6C8DB),
        primaryContainer = Color(0xFF2C4321), onPrimaryContainer = Color(0xFFD6EFC1),
        secondaryContainer = Color(0xFF233E5A), onSecondaryContainer = Color(0xFFD6E9FF),
        outline = Color(0xFF91A5BC), outlineVariant = Color(0xFF41546B)
    ) else lightColorScheme(
        primary = Color(0xFF4E8216), onPrimary = Color.White, secondary = Color(0xFF2A6FC9), onSecondary = Color.White,
        tertiary = Color(0xFF0874BB), onTertiary = Color.White,
        background = Color(0xFFE3EAF2), surface = Color.White, surfaceContainer = Color(0xFFEDF3F9),
        onBackground = Color(0xFF26384A), onSurface = Color(0xFF26384A), onSurfaceVariant = Color(0xFF52677D),
        primaryContainer = Color(0xFFE7F1DA), onPrimaryContainer = Color(0xFF263E12),
        secondaryContainer = Color(0xFFDCEBFA), onSecondaryContainer = Color(0xFF153A60),
        outline = Color(0xFF607489), outlineVariant = Color(0xFFC7D4E1)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
