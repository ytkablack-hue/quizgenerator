package kz.quizgenerator

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AiTask(val title: String) { QUIZ("Тест"), CARDS("Карточки"), REVIEW("Разбор ошибок") }

@Composable
internal fun AiScreen(
    settings: StudySettings,
    attempt: Attempt?,
    readMaterial: (Uri) -> String,
    saveQuiz: (Quiz) -> Unit,
    saveCards: (List<Flashcard>) -> Boolean,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val client = remember(settings) { AiClient(settings) }
    var mode by rememberSaveable { mutableStateOf(AiTask.QUIZ) }
    var topic by rememberSaveable { mutableStateOf("") }
    var material by rememberSaveable { mutableStateOf("") }
    var difficulty by rememberSaveable { mutableStateOf("medium") }
    var count by rememberSaveable { mutableStateOf("5") }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var quiz by remember { mutableStateOf<Quiz?>(null) }
    var cards by remember { mutableStateOf<List<Flashcard>>(emptyList()) }
    var review by remember { mutableStateOf<AiReview?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }

    fun request(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        failure = null
        notice = null
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failure = error.message?.take(180) ?: "Не удалось выполнить запрос" }
            finally { busy = false }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) request { material = withContext(Dispatchers.IO) { readMaterial(uri) } }
    }
    StudyColumn("Создать с ИИ", onBack) {
        Text("Учебный материал отправляется на настроенный сервер и в облачную модель только по нажатию кнопки.")
        if (settings.server.isBlank()) Text("Укажите адрес сервера в настройках.")
        OutlinedButton(enabled = !busy && settings.server.isNotBlank(), onClick = {
            request { notice = if (client.configured()) "Сервер доступен, ИИ настроен" else "Сервер доступен, ключ ИИ не настроен" }
        }) { Text("Проверить подключение") }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AiTask.entries.forEach { task ->
                OutlinedButton(enabled = !busy, onClick = { mode = task }) {
                    Text((if (mode == task) "✓ " else "") + task.title)
                }
            }
        }
        if (mode == AiTask.REVIEW) {
            val available = attempt?.takeIf { it.mistakes.isNotEmpty() }
            Text(available?.let { "Последний тест с ошибками: ${it.title}" } ?: "Сначала пройдите тест с ошибками.")
            Button(enabled = available != null && !busy && settings.server.isNotBlank(), onClick = {
                request { review = client.review(requireNotNull(available)) }
            }) { Text("Получить анализ") }
            review?.let { result ->
                Text(result.summary, style = MaterialTheme.typography.titleMedium)
                result.steps.forEach { Text("• $it") }
            }
        } else {
            StudyField(topic, { topic = it.take(160) }, "Тема")
            StudyField(material, { material = it.take(12_000) }, "Учебный материал")
            TextButton(enabled = !busy, onClick = {
                picker.launch(arrayOf("text/plain", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            }) { Text("Загрузить TXT или DOCX") }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("easy" to "Легко", "medium" to "Средне", "hard" to "Сложно").forEach { (id, label) ->
                    OutlinedButton(enabled = !busy, onClick = { difficulty = id }) {
                        Text((if (difficulty == id) "✓ " else "") + label)
                    }
                }
            }
            StudyField(count, { count = it.filter(Char::isDigit).take(2) }, "Количество (1–25)")
            val quantity = count.toIntOrNull() ?: 0
            Button(enabled = !busy && settings.server.isNotBlank() && quantity in 1..25 &&
                (topic.isNotBlank() || material.isNotBlank()), onClick = {
                request {
                    if (mode == AiTask.QUIZ) {
                        quiz = client.quiz(topic, material, difficulty, quantity)
                        editingId = null
                    } else cards = client.cards(topic, material, difficulty, quantity)
                }
            }) { Text(if (busy) "Выполняется…" else "Генерировать") }

            if (mode == AiTask.QUIZ) quiz?.let { preview ->
                Text("Создано ИИ · проверьте перед сохранением", style = MaterialTheme.typography.titleMedium)
                StudyField(preview.title, { quiz = preview.copy(title = it) }, "Название теста")
                preview.questions.forEachIndexed { index, item ->
                    Text("${index + 1}. ${item.text}", style = MaterialTheme.typography.titleMedium)
                    val key = item.correctIndex ?: 0
                    item.options.forEachIndexed { i, option -> Text((if (i == key) "✓ " else "") + option) }
                    item.sourceExcerpt?.let { Text("Цитата: $it") }
                    Row {
                        TextButton(onClick = { editingId = if (editingId == item.id) null else item.id }) {
                            Text(if (editingId == item.id) "Свернуть" else "Редактировать")
                        }
                        TextButton(enabled = preview.questions.size > 1, onClick = {
                            quiz = preview.copy(questions = preview.questions.filterNot { it.id == item.id })
                        }) { Text("Убрать") }
                    }
                    if (editingId == item.id) {
                        fun change(updated: QuizQuestion) {
                            quiz = preview.copy(questions = preview.questions.map { if (it.id == item.id) updated else it })
                        }
                        StudyField(item.text, { change(item.copy(text = it)) }, "Вопрос")
                        item.options.forEachIndexed { i, option ->
                            StudyField(option, { value ->
                                change(item.copy(options = item.options.mapIndexed { j, old -> if (i == j) value else old }))
                            }, "Вариант ${i + 1}")
                            TextButton(onClick = { change(item.copy(correctIndex = i)) }) { Text("Отметить правильным") }
                        }
                        StudyField(item.explanation.orEmpty(), { change(item.copy(explanation = it)) }, "Пояснение")
                    }
                }
                Button(enabled = !busy && preview.title.isNotBlank() &&
                    preview.questions.all { it.text.isNotBlank() && it.options.none(String::isBlank) }, onClick = {
                    runCatching { saveQuiz(preview) }.onFailure { failure = it.message }
                }) { Text("Сохранить тест") }
            }
            if (mode == AiTask.CARDS && cards.isNotEmpty()) {
                Text("Карточки созданы ИИ · проверьте перед сохранением")
                cards.forEach { card ->
                    Text(card.front, style = MaterialTheme.typography.titleMedium)
                    Text(card.back)
                    TextButton(onClick = { cards = cards.filterNot { it.id == card.id } }) { Text("Убрать карточку") }
                }
                Button(enabled = !busy, onClick = {
                    if (saveCards(cards)) { cards = emptyList(); notice = "Карточки сохранены" }
                }) { Text("Сохранить карточки") }
            }
        }
        notice?.let { Text(it) }
        failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
