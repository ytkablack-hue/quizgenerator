package kz.quizgenerator.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kz.quizgenerator.ui.components.*
import kz.quizgenerator.ui.theme.*

data class LectureRow(val id: String, val title: String, val progress: Int, val stage: Int)
data class StageRow(val title: String, val description: String, val progress: Int, val locked: Boolean = false)
data class CardRow(val id: String, val front: String, val back: String)

@Composable
fun LearningHomeScreen(lectures: List<LectureRow>, onNew: () -> Unit, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SteamHeading("Обучение", "Загрузи лекцию — ИИ разберёт её на этапы, составит тест и карточки.")
        SteamButton("⇧  Загрузить лекцию", onClick = onNew)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (lectures.isEmpty()) item { SteamCard { Text("Пока нет лекций", fontWeight = FontWeight.Bold); Text("Загрузи материал, чтобы начать обучение.", color = SteamMuted) } }
            items(lectures, key = { it.id }) { lecture -> SteamCard {
                Text(lecture.title, fontWeight = FontWeight.Bold); SteamProgress(lecture.progress / 100f)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("${lecture.progress}%", Modifier.weight(1f), fontWeight = FontWeight.Bold); Text("Чтение · Карточки · Тест · Проверка", color = SteamMuted) }
                SteamButton("▷  Продолжить", onClick = { onOpen(lecture.id) })
            } }
        }
    }
}

@Composable
fun NewLectureScreen(fileName: String?, fileSize: String?, makeQuiz: Boolean, makeCards: Boolean, makeStages: Boolean,
                     count: Int, busy: Boolean, error: String?, onChoose: () -> Unit, onQuiz: (Boolean) -> Unit,
                     onCards: (Boolean) -> Unit, onStages: (Boolean) -> Unit, onCount: (Int) -> Unit,
                     onCreate: () -> Unit, onBack: () -> Unit) {
    SteamPage("Новая лекция", onBack) {
        SteamHeading("Создай курс", "Загрузи учебный материал и выбери, что подготовить.")
        SteamCard { Text("Учебный материал", fontWeight = FontWeight.Bold)
            Text(fileName ?: "Выбери TXT или DOCX", color = SteamMuted)
            fileSize?.let { Text(it, color = SteamMuted) }
            SteamButton("Выбрать файл", tone = SteamTone.PALE, onClick = onChoose)
        }
        SteamCard {
            Text("Содержание курса", fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(makeStages, onStages); Text("Этапы обучения") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(makeQuiz, onQuiz); Text("Тест по материалу") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(makeCards, onCards); Text("Карточки для повторения") }
            Text("Количество вопросов и карточек: $count", color = SteamMuted)
            Slider(count.toFloat(), { onCount(it.toInt().coerceIn(1, 25)) }, valueRange = 1f..25f, steps = 23)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        SteamButton(if (busy) "Создаём материалы…" else "Создать курс", enabled = !busy && fileName != null && (makeQuiz || makeCards), onClick = onCreate)
    }
}

@Composable
fun LectureStagesScreen(title: String, stages: List<StageRow>, onOpen: (Int) -> Unit, onBack: () -> Unit) {
    SteamPage("Этапы обучения", onBack) {
        SteamHeading(title, "Четыре этапа помогут закрепить материал")
        stages.forEachIndexed { index, stage -> SteamCard(Modifier.clickable(enabled = !stage.locked) { onOpen(index) }) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("${index + 1}", color = SteamAccent, fontWeight = FontWeight.Bold); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(stage.title, fontWeight = FontWeight.Bold); Text(stage.description, color = SteamMuted) }; Text(if (stage.locked) "🔒" else if (stage.progress == 100) "✓" else "›") }
            SteamProgress(stage.progress / 100f)
        } }
    }
}

@Composable
fun LectureReadingScreen(title: String, material: String, onBack: () -> Unit, onComplete: () -> Unit) {
    SteamPage("Чтение", onBack) {
        SteamHeading(title, "Этап 1 · Изучи материал")
        SteamCard(Modifier.weight(1f)) { Text(material, style = MaterialTheme.typography.bodyLarge) }
        SteamButton("Материал прочитан", onClick = onComplete)
    }
}

@Composable
fun FlashcardsScreen(cards: List<CardRow>, onBack: () -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit,
                     onDelete: (String) -> Unit, onRate: (String, String) -> Unit) {
    var index by remember(cards) { mutableIntStateOf(0) }
    var flipped by remember(cards) { mutableStateOf(false) }
    val card = cards.getOrNull(index)
    SteamPage("Карточки", onBack) {
        Row(verticalAlignment = Alignment.CenterVertically) { SteamHeading("Повторение"); Spacer(Modifier.weight(1f)); Text("${if (cards.isEmpty()) 0 else index + 1} / ${cards.size}", color = SteamMuted) }
        card?.let {
            SteamCard(Modifier.weight(1f).clickable { flipped = !flipped }) {
                Text(if (flipped) "Обратная сторона" else "Лицевая сторона", color = SteamBlue)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (flipped) it.back else it.front, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            }
            Text("Насколько легко было вспомнить?", color = SteamMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Сложно" to "hard", "Средне" to "medium", "Легко" to "easy").forEach { (label, rating) ->
                    Button(onClick = { onRate(it.id, rating); index = (index + 1).mod(cards.size); flipped = false }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = when(rating) { "hard" -> Color(0xFFD77878); "medium" -> Color(0xFFE4B950); else -> SteamGreen })) { Text(label) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { onEdit(it.id) }, modifier = Modifier.weight(1f)) { Text("Изменить") }; OutlinedButton(onClick = { onDelete(it.id) }, modifier = Modifier.weight(1f)) { Text("Удалить") } }
            SteamButton(if (flipped) "Скрыть ответ" else "Перевернуть карточку", tone = SteamTone.PALE, onClick = { flipped = !flipped })
        } ?: SteamCard { Text("Пока нет карточек", fontWeight = FontWeight.Bold); SteamButton("Создать карточку", onClick = onAdd) }
        if (cards.isNotEmpty()) SteamButton("Добавить карточку", tone = SteamTone.TEXT, onClick = onAdd)
    }
}

@Composable
fun CardEditorScreen(front: String, back: String, error: String?, onFront: (String) -> Unit, onBackText: (String) -> Unit,
                     onSave: () -> Unit, onCancel: () -> Unit) {
    SteamPage("Карточка", onCancel) {
        SteamField("Лицевая сторона", front, onFront, hint = "Вопрос или термин", singleLine = false)
        SteamField("Обратная сторона", back, onBackText, hint = "Ответ или объяснение", singleLine = false)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        SteamButton("Сохранить карточку", enabled = front.isNotBlank() && back.isNotBlank(), onClick = onSave)
    }
}

@Composable
fun LectureQuestionScreen(title: String, number: Int, total: Int, question: String, options: List<String>,
                          selected: Int?, hint: String?, onSelect: (Int) -> Unit, onHint: () -> Unit,
                          onSubmit: () -> Unit, onSkip: () -> Unit, onBack: () -> Unit) {
    SteamPage("Проверка", onBack) {
        SteamHeading(title, "Вопрос $number из $total")
        SteamProgress(number.toFloat() / total.coerceAtLeast(1))
        SteamCard(Modifier.weight(1f)) {
            Text(question, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            options.forEachIndexed { index, option ->
                Row(Modifier.fillMaxWidth().clickable { onSelect(index) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected == index, onClick = { onSelect(index) }); Text(option)
                }
            }
            hint?.let { Text(it, color = SteamBlue) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onHint, modifier = Modifier.weight(1f)) { Text("Подсказка") }
            OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) { Text("Пропустить") }
        }
        SteamButton("Ответить", enabled = selected != null, onClick = onSubmit)
    }
}
