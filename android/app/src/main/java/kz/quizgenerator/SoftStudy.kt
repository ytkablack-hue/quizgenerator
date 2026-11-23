package kz.quizgenerator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable
internal fun SoftStudyTheme(mode: String, content: @Composable () -> Unit) {
    kz.quizgenerator.ui.theme.SteamTheme(mode) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
        androidx.compose.runtime.SideEffect {
            (context as? android.app.Activity)?.let { activity ->
                androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
        content()
    }
}

internal enum class Glyph { HOME, ADD, LIBRARY, PLAN, SETTINGS, FILE, AI, EDIT, CARDS, PROFILE, BOOKMARK }

@Composable
internal fun StudyIcon(glyph: Glyph, modifier: Modifier = Modifier, description: String? = null) {
    val tint = LocalContentColor.current
    Canvas(modifier.size(24.dp).then(if (description == null) Modifier else Modifier.semantics { contentDescription = description })) {
        val stroke = 1.8.dp.toPx()
        fun line(x: Float, y: Float, a: Float, b: Float) = drawLine(tint,
            Offset(x * size.width, y * size.height), Offset(a * size.width, b * size.height), stroke)
        fun rect(x: Float, y: Float, w: Float, h: Float) = drawRect(tint, Offset(x * size.width, y * size.height),
            androidx.compose.ui.geometry.Size(w * size.width, h * size.height), style = Stroke(stroke))
        when (glyph) {
            Glyph.HOME -> {
                line(.12f,.48f,.5f,.15f); line(.5f,.15f,.88f,.48f)
                line(.23f,.43f,.23f,.83f); line(.77f,.43f,.77f,.83f); line(.23f,.83f,.77f,.83f)
            }
            Glyph.ADD -> {
                drawCircle(tint, size.minDimension * .37f, style = Stroke(stroke))
                line(.31f,.5f,.69f,.5f); line(.5f,.31f,.5f,.69f)
            }
            Glyph.LIBRARY -> { rect(.18f,.22f,.14f,.56f); rect(.38f,.16f,.15f,.62f); line(.61f,.23f,.79f,.79f) }
            Glyph.PLAN -> { rect(.16f,.24f,.68f,.59f); line(.16f,.42f,.84f,.42f); line(.33f,.14f,.33f,.3f); line(.68f,.14f,.68f,.3f) }
            Glyph.SETTINGS -> {
                drawCircle(tint, size.minDimension * .28f, style = Stroke(stroke))
                drawCircle(tint, size.minDimension * .09f, style = Stroke(stroke))
                line(.5f,.12f,.5f,.25f); line(.5f,.75f,.5f,.88f); line(.12f,.5f,.25f,.5f); line(.75f,.5f,.88f,.5f)
            }
            Glyph.FILE -> { rect(.22f,.13f,.56f,.74f); line(.34f,.42f,.66f,.42f); line(.34f,.58f,.66f,.58f) }
            Glyph.AI -> {
                line(.52f,.13f,.61f,.41f); line(.61f,.41f,.88f,.5f); line(.88f,.5f,.61f,.59f)
                line(.61f,.59f,.52f,.87f); line(.52f,.87f,.43f,.59f); line(.43f,.59f,.16f,.5f)
                line(.16f,.5f,.43f,.41f); line(.43f,.41f,.52f,.13f)
            }
            Glyph.EDIT -> { line(.24f,.76f,.72f,.28f); line(.36f,.84f,.84f,.36f); line(.24f,.76f,.21f,.87f); line(.21f,.87f,.36f,.84f); line(.72f,.28f,.84f,.36f) }
            Glyph.PROFILE -> {drawCircle(tint,size.minDimension*.15f,Offset(size.width*.5f,size.height*.3f),style=Stroke(stroke));rect(.2f,.58f,.6f,.27f)}
            Glyph.BOOKMARK -> {line(.27f,.14f,.73f,.14f);line(.27f,.14f,.27f,.87f);line(.73f,.14f,.73f,.87f);line(.27f,.87f,.5f,.68f);line(.5f,.68f,.73f,.87f)}
            Glyph.CARDS -> { rect(.22f,.32f,.62f,.46f); line(.15f,.23f,.75f,.23f); line(.15f,.23f,.15f,.69f) }
        }
    }
}

internal enum class PanelTone { PLAIN, LAVENDER, SKY }

@Composable
internal fun StudyPanel(modifier: Modifier = Modifier, tone: PanelTone = PanelTone.PLAIN,
                        onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val click = if (onClick == null) modifier else modifier.clickable(onClick = onClick)
    if(LocalStitch.current){StitchCard(click,when(tone){PanelTone.SKY->MaterialTheme.colorScheme.secondaryContainer;PanelTone.LAVENDER->MaterialTheme.colorScheme.primaryContainer;else->MaterialTheme.colorScheme.surface},content);return}
    kz.quizgenerator.ui.components.SteamCard(click,when(tone){PanelTone.SKY->MaterialTheme.colorScheme.secondaryContainer;PanelTone.LAVENDER->MaterialTheme.colorScheme.primaryContainer;else->MaterialTheme.colorScheme.surface}) { content() }
}

@Composable
internal fun SectionHeading(title: String, detail: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun StudyError(message: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(message, Modifier.padding(12.dp))
    }
}

@Composable
internal fun StudyLoading(label: String, fraction: Float? = null, cancel: (() -> Unit)? = null) {
    StudyPanel(tone = PanelTone.SKY) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        cancel?.let { TextButton(onClick = it) { Text("Отменить") } }
    }
}

@Composable
internal fun StudyConfirmation(title: String, description: String, confirm: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onCancel, title = { Text(title) }, text = { Text(description) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Отмена") } })
}


@Composable internal fun StudyPrimary(text: String,enabled: Boolean=true,onClick: ()->Unit) {
    if(LocalStitch.current)StitchButton(onClick,Modifier.fillMaxWidth(),enabled){Text(text)} else kz.quizgenerator.ui.components.SteamButton(text,enabled=enabled,modifier=Modifier.heightIn(min=52.dp),onClick=onClick)
}
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun FileAttachmentCard(record: MaterialRecord,selected: Boolean=false,onOpen: ()->Unit,
    onRecognize: ()->Unit,onChoose: ()->Unit,onDetach: ()->Unit,detachLabel: String="Открепить") {
    var menu by androidx.compose.runtime.remember {androidx.compose.runtime.mutableStateOf(false)}
    StudyPanel(onClick=onOpen) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.small) {Column(Modifier.sizeIn(minWidth=44.dp,minHeight=48.dp).padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally){StudyIcon(Glyph.FILE);Text(record.name.substringAfterLast('.',record.format).uppercase().take(5),style=MaterialTheme.typography.labelSmall)}}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(record.name,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(fileSizeLabel(record.bytes)+" · На устройстве",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(selected)Text("Выбран для подготовки",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
            }
            Box {IconButton(onClick={menu=true},modifier=Modifier.semantics {contentDescription="Действия файла "+record.name}){Text("⋮")}
                DropdownMenu(menu,{menu=false}) {
                    DropdownMenuItem(text={Text("Открыть материал")},onClick={menu=false;onOpen()})
                    DropdownMenuItem(text={Text("Распознать готовые вопросы")},onClick={menu=false;onRecognize()})
                    DropdownMenuItem(text={Text(if(selected)"Выбран для ИИ" else "Выбрать для ИИ")},onClick={menu=false;onChoose()})
                    DropdownMenuItem(text={Text(detachLabel)},onClick={menu=false;onDetach()})
                }
            }
        }
        if(record.notice.isNotBlank())Text(record.notice,style=MaterialTheme.typography.bodySmall)
    }
}
internal fun fileSizeLabel(bytes: Long): String = when {
    bytes>=1024*1024 -> String.format(java.util.Locale.ROOT,"%.1f МиБ",bytes/(1024.0*1024))
    bytes>=1024 -> String.format(java.util.Locale.ROOT,"%.1f КиБ",bytes/1024.0)
    else -> "$bytes байт"
}
