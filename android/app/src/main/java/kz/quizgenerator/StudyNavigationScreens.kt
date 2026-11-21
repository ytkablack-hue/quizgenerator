package kz.quizgenerator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
internal fun ScreenList(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

internal fun examCountdown(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days < 0 -> "Дата экзамена прошла"
        days == 0L -> "Экзамен сегодня"
        else -> "Дней до экзамена: $days"
    }
}

internal fun displayDate(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()
internal fun documentCaption(document: DocumentDetails): String =
    document.bytes?.let { "${document.name} · ${java.text.NumberFormat.getIntegerInstance().format(it)} байт" }
        ?: document.name

@Composable
internal fun HomeScreen(onCreate: (CreationSource) -> Unit) {
    ScreenList {
        item { SectionHeading("Создай тест из своего материала", "Проверь вопросы и учись в своём темпе") }
        item {
            Button(onClick = { onCreate(CreationSource.CHOOSE) }, modifier = Modifier.fillMaxWidth().heightIn(min=64.dp)) {
                Text("Создать тест", style=MaterialTheme.typography.titleLarge)
            }
        }
        items(listOf(CreationSource.DOCUMENT to "Из PDF или Word", CreationSource.PHOTO to "Из фотографии",
            CreationSource.TEXT to "Из текста", CreationSource.AI to "С помощью ИИ")) { (source,label) ->
            StudyPanel(onClick={onCreate(source)}) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(label,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);Text("›")
                }
            }
        }
        item { Text("Сохранённые тесты, карточки и история — в разделе «Подготовка».", color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun CreateScreen(import: ImportProgress?, error: String?, onDocument: () -> Unit, onCancel: () -> Unit,
                          onAi: () -> Unit, onManual: () -> Unit) {
    ScreenList {
        item { SectionHeading("Создать", "Выбери, откуда возьмутся вопросы.") }
        if (import != null) item { StudyLoading("Обработка: ${documentCaption(import.document)}", import.fraction, onCancel) }
        error?.let { item { StudyError(it) } }
        item {
            CreationCard("Из документа", "Загрузи готовые вопросы из TXT, Word или JSON",
                Glyph.FILE, PanelTone.LAVENDER, enabled = import == null, onClick = onDocument)
        }
        item {
            CreationCard("Создать с ИИ", "ИИ составит тест по теме или учебному материалу",
                Glyph.AI, PanelTone.SKY, enabled = import == null, caption = "Требуется интернет", onClick = onAi)
        }
        item {
            CreationCard("Создать вручную", "Добавляй собственные вопросы и ответы",
                Glyph.EDIT, PanelTone.PLAIN, enabled = import == null, onClick = onManual)
        }
    }
}

@Composable
private fun CreationCard(title: String, detail: String, icon: Glyph, tone: PanelTone, enabled: Boolean,
                         caption: String? = null, onClick: () -> Unit) {
    StudyPanel(tone = tone, onClick = if (enabled) onClick else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.medium) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { StudyIcon(icon) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(detail)
                caption?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
            }
            Text("›", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
internal fun LibraryScreen(quizzes: List<Quiz>, modified: (Quiz) -> Long?, error: String?,
                           open: (Quiz) -> Unit, edit: (Quiz) -> Unit, delete: (Quiz) -> Unit,
                           canDelete: (Quiz) -> Boolean, cards: () -> Unit, onCreate: () -> Unit,
                           cardCount: (Quiz) -> Int, quizCards: (Quiz) -> Unit, onImport: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var alphabetical by rememberSaveable { mutableStateOf(false) }
    val byId = remember(quizzes) { quizzes.associateBy(Quiz::id) }
    kz.quizgenerator.ui.screens.CreateLibraryScreen(
        quizzes.map { kz.quizgenerator.ui.screens.QuizRow(it.id, it.title, it.questions.size,
            cardCount(it), modified(it) ?: 0L, !canDelete(it)) }, query, { query = it },
        onCreate, onImport, { id -> byId[id]?.let(open) }, { id -> byId[id]?.let(edit) },
        { id -> byId[id]?.let(delete) }, cards, alphabetical, { alphabetical = !alphabetical }, error,
        { id -> byId[id]?.let(quizCards) }
    )
}
