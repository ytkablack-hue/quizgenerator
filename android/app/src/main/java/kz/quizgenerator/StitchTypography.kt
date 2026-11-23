package kz.quizgenerator

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * В дизайне используется Inter. Чтобы подключить его:
 * положите inter_regular.ttf / inter_medium.ttf / inter_semibold.ttf / inter_bold.ttf в res/font
 * и замените FontFamily.Default на FontFamily(Font(R.font.inter_regular, FontWeight.Normal), ...).
 */
private val Inter = FontFamily.Default

internal val StitchTypography = Typography(
    // display-lg
    displayLarge = TextStyle(fontFamily = Inter, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em),
    // headline-lg
    headlineLarge = TextStyle(fontFamily = Inter, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em),
    // headline-lg-mobile
    headlineMedium = TextStyle(fontFamily = Inter, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em),
    // headline-md
    headlineSmall = TextStyle(fontFamily = Inter, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    // title-lg
    titleLarge = TextStyle(fontFamily = Inter, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    // title-md
    titleMedium = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    // body-lg
    bodyLarge = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    // body-md
    bodyMedium = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    // body-sm
    bodySmall = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    // label-lg
    labelLarge = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.01.em),
    // label-md
    labelMedium = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    // label-sm
    labelSmall = TextStyle(fontFamily = Inter, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.04.em),
)
