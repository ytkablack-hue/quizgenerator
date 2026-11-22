package kz.quizgenerator
import org.junit.Assert.*
import org.junit.Test
import androidx.compose.ui.graphics.Color
import kz.quizgenerator.ui.theme.TestHelperPalettes
import kotlin.math.pow
class ThemeContrastTest {
    private fun l(c: Color): Double {
        fun channel(x: Float): Double=if(x<=.04045f)x/12.92 else ((x+.055)/1.055).pow(2.4)
        return .2126*channel(c.red)+.7152*channel(c.green)+.0722*channel(c.blue)
    }
    private fun contrast(a: Color,b: Color): Double {val x=l(a);val y=l(b);return (maxOf(x,y)+.05)/(minOf(x,y)+.05)}
    @Test fun sharedLightAndDarkTokensHaveReadableText() {
        for(s in listOf(TestHelperPalettes.light,TestHelperPalettes.dark)) {
            assertTrue(contrast(s.onSurface,s.surface)>=7)
            assertTrue(contrast(s.onBackground,s.background)>=7)
            assertTrue(contrast(s.onSurfaceVariant,s.surface)>=4.5)
            assertTrue(contrast(s.onPrimary,s.primary)>=4.5)
            assertTrue(contrast(s.onPrimaryContainer,s.primaryContainer)>=4.5)
        }
    }
    @Test fun newUsersDefaultToLightWithoutChangingStoredSystemChoice() {
        assertEquals("light",StudySettings().theme)
        assertEquals("system",StudySettings(theme="system").theme)
        assertEquals("dark",StudySettings(dark=true).theme)
    }
}
