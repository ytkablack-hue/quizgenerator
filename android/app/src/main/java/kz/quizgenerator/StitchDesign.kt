package kz.quizgenerator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Adapted from supplied TestHelper.zip: Color/Type/Theme and Components.ThCard/PrimaryButton.
// Keep the approved Home outside this provider; dark roles remain readable instead of hardcoded white.
internal val LocalStitch = staticCompositionLocalOf {false}
@Composable internal fun StitchContentTheme(enabled: Boolean=true,content: @Composable ()->Unit) {
    if(!enabled){content();return}
    val old=MaterialTheme.colorScheme
    val dark=old.background==kz.quizgenerator.ui.theme.TestHelperPalettes.dark.background
    val colors=if(dark)old else old.copy(primary=Color(0xFF005EA2),onPrimary=Color.White,
        background=Color(0xFFF9F9FF),onBackground=Color(0xFF0E1B32),surface=Color.White,onSurface=Color(0xFF0E1B32),
        onSurfaceVariant=Color(0xFF64748B),primaryContainer=Color(0xFFE8EEFF),onPrimaryContainer=Color(0xFF0E1B32),
        secondaryContainer=Color(0xFFF0F7FF),onSecondaryContainer=Color(0xFF00497F),surfaceContainer=Color(0xFFF1F3FF),outlineVariant=Color(0xFFE2E8F0))
    CompositionLocalProvider(LocalStitch provides true) {MaterialTheme(colorScheme=colors,typography=StitchTypography,
        shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(24.dp)),content=content)}
}
@Composable internal fun StitchCard(modifier: Modifier=Modifier,color: Color=MaterialTheme.colorScheme.surface,content: @Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=color,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),shadowElevation=1.dp) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}
@Composable internal fun StitchButton(onClick: ()->Unit,modifier: Modifier=Modifier,enabled: Boolean=true,content: @Composable RowScope.()->Unit) {
    Button(onClick,modifier.heightIn(min=52.dp),enabled=enabled,shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(horizontal=16.dp,vertical=12.dp),content=content)
}
@Composable internal fun StitchOutlinedButton(onClick: ()->Unit,modifier: Modifier=Modifier,enabled: Boolean=true,content: @Composable RowScope.()->Unit) {
    OutlinedButton(onClick,modifier.heightIn(min=48.dp),enabled=enabled,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),contentPadding=PaddingValues(horizontal=14.dp,vertical=10.dp),content=content)
}
@Composable internal fun StitchPill(text: String) {
    Surface(color=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.onPrimaryContainer,shape=RoundedCornerShape(100.dp)) {
        Text(text,Modifier.padding(horizontal=10.dp,vertical=5.dp),style=MaterialTheme.typography.labelSmall)
    }
}
@Composable internal fun StitchTabs(labels: List<String>,selected: Int,onSelect: (Int)->Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer,RoundedCornerShape(12.dp)).horizontalScroll(rememberScrollState()).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed {i,label->Surface(onClick={onSelect(i)},shape=RoundedCornerShape(8.dp),color=if(i==selected)MaterialTheme.colorScheme.surface else Color.Transparent,
            contentColor=if(i==selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) {Box(Modifier.heightIn(min=48.dp).padding(horizontal=12.dp,vertical=10.dp),contentAlignment=Alignment.Center){Text(label,style=MaterialTheme.typography.labelLarge)}}}
    }
}
@Composable internal fun StitchMetric(label: String,value: String,modifier: Modifier=Modifier) {
    Surface(modifier,color=MaterialTheme.colorScheme.surfaceContainer,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(horizontal=10.dp,vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)){Text(label,style=MaterialTheme.typography.labelSmall);Text(value,style=MaterialTheme.typography.titleLarge)}
    }
}
