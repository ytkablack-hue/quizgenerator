package kz.quizgenerator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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

object TestHelperPalettes {
    val light=lightColorScheme(
        primary=Color(0xFF0075BF),onPrimary=Color.White,secondary=Color(0xFF236A98),onSecondary=Color.White,
        tertiary=Color(0xFF426583),onTertiary=Color.White,
        background=Color(0xFFF8FAFD),surface=Color.White,surfaceContainer=Color(0xFFF0F4F9),
        onBackground=Color(0xFF18293C),onSurface=Color(0xFF18293C),onSurfaceVariant=Color(0xFF506174),
        primaryContainer=Color(0xFFE1F2FF),onPrimaryContainer=Color(0xFF063A60),
        secondaryContainer=Color(0xFFEBF5FC),onSecondaryContainer=Color(0xFF163B56),
        outline=Color(0xFF738397),outlineVariant=Color(0xFFDCE5EE)
    )
    val dark=darkColorScheme(
        primary=Color(0xFF85CAFF),onPrimary=Color(0xFF003352),secondary=Color(0xFF9ACAF0),onSecondary=Color(0xFF10364E),
        tertiary=Color(0xFFABC9E3),onTertiary=Color(0xFF17354E),
        background=Color(0xFF101923),surface=Color(0xFF172431),surfaceContainer=Color(0xFF203140),
        onBackground=Color(0xFFE8F0F8),onSurface=Color(0xFFE8F0F8),onSurfaceVariant=Color(0xFFBAC8D8),
        primaryContainer=Color(0xFF123D5C),onPrimaryContainer=Color(0xFFD8EEFF),
        secondaryContainer=Color(0xFF233E53),onSecondaryContainer=Color(0xFFD9ECFB),
        outline=Color(0xFF8C9EB1),outlineVariant=Color(0xFF3D5266)
    )
}
@Composable
fun SteamTheme(mode: String="light",content: @Composable ()->Unit) {
    val dark=when(mode){"dark"->true;"light"->false;else->isSystemInDarkTheme()}
    MaterialTheme(colorScheme=if(dark)TestHelperPalettes.dark else TestHelperPalettes.light,
        shapes=Shapes(small=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),medium=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),large=androidx.compose.foundation.shape.RoundedCornerShape(22.dp)),
        content=content)
}
