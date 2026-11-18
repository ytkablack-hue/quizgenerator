package kz.quizgenerator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlashcardScreen(cards: List<Flashcard>, onSave: (List<Flashcard>) -> Boolean, onBack: () -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var flipped by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var form by rememberSaveable { mutableStateOf(false) }
    var front by rememberSaveable { mutableStateOf("") }
    var back by rememberSaveable { mutableStateOf("") }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val index = selected.coerceIn(0, cards.lastIndex.coerceAtLeast(0))
    val card = cards.getOrNull(index)
    fun closeForm() { form = false; editing = null; front = ""; back = "" }
    fun leaveCards() {
        if (!form) onBack()
        else if (front.isNotBlank() || back.isNotBlank()) discard = true
        else closeForm()
    }
    BackHandler { leaveCards() }
    if (discard) StudyConfirmation("Отменить редактирование?", "Несохранённый текст карточки пропадёт.",
        "Отменить", { discard = false }) { discard = false; closeForm() }
    cards.firstOrNull { it.id == removing }?.let { target ->
        StudyConfirmation("Удалить карточку?", target.front, "Удалить", { removing = null }) {
            if (onSave(cards.filterNot { it.id == target.id })) {
                if (editing == target.id) closeForm()
                selected = index.coerceAtMost((cards.size - 2).coerceAtLeast(0))
                flipped = false
                removing = null
            }
        }
    }
    ScreenList {
        item {
            TextButton(onClick = { leaveCards() }) { Text("Назад") }
            SectionHeading("Карточки", "Повторяй материал в своём темпе.")
        }
        if (card == null) item { StudyPanel(PanelTone.LAVENDER) { Text("Добавь первую карточку.") } }
        else {
            item {
                StudyPanel(if (flipped) PanelTone.SKY else PanelTone.LAVENDER, onClick = { flipped = !flipped }) {
                    Text("Карточка ${index + 1} из ${cards.size}", style = MaterialTheme.typography.labelLarge)
                    Text(if (flipped) "Обратная сторона" else "Лицевая сторона", style = MaterialTheme.typography.labelLarge)
                    Box(Modifier.fillMaxWidth().heightIn(min = 125.dp).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text(if (flipped) card.back else card.front, style = MaterialTheme.typography.headlineSmall)
                    }
                    if (card.generatedByAi) Text("Создано ИИ", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = index > 0, onClick = { selected = index - 1; flipped = false }) { Text("Предыдущая") }
                    Button(onClick = { flipped = !flipped }) { Text("Перевернуть") }
                    OutlinedButton(enabled = index < cards.lastIndex, onClick = { selected = index + 1; flipped = false }) { Text("Следующая") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !form, onClick = {
                        editing = card.id; front = card.front; back = card.back; form = true
                    }) { Text("Редактировать") }
                    TextButton(onClick = { removing = card.id }) { Text("Удалить") }
                }
            }
        }
        item {
            if (!form) Button(onClick = { form = true }) { Text("Добавить карточку") }
            else StudyPanel {
                Text(if (editing == null) "Новая карточка" else "Редактирование карточки", style = MaterialTheme.typography.titleMedium)
                StudyField(front, { front = it.take(16_000) }, "Лицевая сторона")
                StudyField(back, { back = it.take(16_000) }, "Обратная сторона")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = front.isNotBlank() && back.isNotBlank(), onClick = {
                        val id = editing
                        val ai = cards.firstOrNull { it.id == id }?.generatedByAi ?: false
                        val changed = Flashcard(id ?: UUID.randomUUID().toString(), front.trim(), back.trim(), ai)
                        if (onSave(if (id == null) cards + changed else cards.map { if (it.id == id) changed else it })) {
                            if (id == null) selected = cards.size
                            flipped = false
                            closeForm()
                        }
                    }) { Text("Сохранить") }
                    TextButton(onClick = { discard = true }) { Text("Отмена") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun PreparationScreen(data: StudyData, quizzes: List<Quiz>, onSave: (List<Exam>) -> Boolean) {
    var showForm by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var topics by rememberSaveable { mutableStateOf("") }
    var links by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var calendar by rememberSaveable { mutableStateOf(false) }
    var attemptId by rememberSaveable { mutableStateOf<String?>(null) }
    var materials by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val selectedAttempt = data.attempts.firstOrNull { it.id == attemptId }
    BackHandler(selectedAttempt != null || showForm) {
        if (selectedAttempt != null) attemptId = null else showForm = false
    }
    if (selectedAttempt != null) {
        ScreenList {
            item {
                TextButton(onClick = { attemptId = null }) { Text("К истории") }
                SectionHeading(selectedAttempt.title, displayDate(selectedAttempt.completedAt))
            }
            item { StudyPanel(PanelTone.LAVENDER) {
                Text("Правильно: ${selectedAttempt.score.correct} из ${selectedAttempt.score.gradedTotal}")
            } }
            if (selectedAttempt.mistakes.isEmpty()) item { StudyPanel { Text("Нет ошибок или пропусков.") } }
            items(selectedAttempt.mistakes) { mistake ->
                StudyPanel {
                    Text(mistake.question, style = MaterialTheme.typography.titleMedium)
                    Text("Твой ответ · ${mistake.given ?: "Пропущен"}")
                    Text("Правильный ответ · ${mistake.correct}")
                    mistake.explanation?.let { Text(it) }
                }
            }
        }
        return
    }
    if (calendar) {
        val initial = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(onDismissRequest = { calendar = false }, confirmButton = {
            TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate().toString() }
                calendar = false
            }) { Text("Выбрать") }
        }, dismissButton = { TextButton(onClick = { calendar = false }) { Text("Отмена") } }) {
            DatePicker(state = picker, modifier = Modifier.heightIn(max = 435.dp).verticalScroll(rememberScrollState()))
        }
    }
    if (showForm) {
        ScreenList {
            item {
                TextButton(onClick = { showForm = false }) { Text("К экзаменам") }
                SectionHeading(if (selectedId == null) "Новый экзамен" else "Редактировать экзамен")
            }
            item { StudyField(title, { title = it.take(200) }, "Дисциплина") }
            item {
                OutlinedButton(onClick = { calendar = true }) { Text("Дата · $date") }
            }
            item { StudyField(topics, { topics = it.take(2500) }, "Темы через запятую") }
            item {
                TextButton(onClick = { materials = !materials }) { Text("Связанные тесты · ${links.size}") }
            }
            if (materials) items(quizzes, key = Quiz::id) { quiz ->
                FilterChip(quiz.id in links, {
                    links = ArrayList(if (quiz.id in links) links - quiz.id else links + quiz.id)
                }, label = { Text(quiz.title) })
            }
            item {
                Button(enabled = title.isNotBlank(), onClick = {
                    val exam = Exam(selectedId ?: UUID.randomUUID().toString(), title.trim(), LocalDate.parse(date),
                        topics.split(',').map(String::trim).filter(String::isNotEmpty).distinct(), links.toList())
                    if (onSave(data.exams.filterNot { it.id == exam.id } + exam)) showForm = false
                }) { Text("Сохранить экзамен") }
            }
        }
        return
    }
    val weak = data.attempts.groupBy(Attempt::title).mapValues { (_, items) -> items.sumOf { it.mistakes.size } }
        .filterValues { it > 0 }.entries.sortedByDescending { it.value }.take(5)
    ScreenList {
        item { SectionHeading("Подготовка", "Экзамены, повторение и результаты занятий.") }
        item { StudyPanel(PanelTone.LAVENDER) {
            Text("Экзамены", style = MaterialTheme.typography.titleLarge)
            if (data.exams.isEmpty()) Text("Добавь дисциплину и дату, чтобы составить план повторения.")
            Button(onClick = {
                selectedId = null; title = ""; date = today.toString(); topics = ""; links = arrayListOf(); materials = false; showForm = true
            }) { Text("Добавить экзамен") }
        } }
        items(data.exams.sortedBy(Exam::date), key = Exam::id) { exam ->
            StudyPanel {
                Text(exam.title, style = MaterialTheme.typography.titleLarge)
                Text("${exam.date} · ${examCountdown(exam.date, today)}")
                val linkedTitles = quizzes.filter { it.id in exam.quizIds }.map(Quiz::title)
                if (linkedTitles.isNotEmpty()) Text("Материалы · ${linkedTitles.joinToString(", ")}")
                val plan = StudyLogic.plan(exam, quizzes, data.attempts, today)
                if (plan.isNotEmpty()) {
                    Text("План повторения", style = MaterialTheme.typography.titleSmall)
                    plan.forEach { Text("${it.date} · ${it.topic}") }
                }
                TextButton(onClick = {
                    selectedId = exam.id; title = exam.title; date = exam.date.toString()
                    topics = exam.topics.joinToString(", "); links = ArrayList(exam.quizIds); materials = false; showForm = true
                }) { Text("Изменить экзамен") }
            }
        }
        if (weak.isNotEmpty()) item { StudyPanel(PanelTone.SKY) {
            Text("Темы для повторения", style = MaterialTheme.typography.titleMedium)
            weak.forEach { Text("${it.key} · ошибок и пропусков: ${it.value}") }
        } }
        item { SectionHeading("История занятий", if (data.attempts.isEmpty()) "Здесь появятся пройденные тесты." else null) }
        items(data.attempts.asReversed(), key = Attempt::id) { attempt ->
            StudyPanel {
                Text(attempt.title, style = MaterialTheme.typography.titleMedium)
                Text("${displayDate(attempt.completedAt)} · ${attempt.score.correct} из ${attempt.score.gradedTotal}")
                TextButton(onClick = { attemptId = attempt.id }) { Text("Разобрать ошибки") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudyPreferences(settings: StudySettings, onSave: (StudySettings) -> Boolean, onBack: () -> Unit) {
    var value by remember(settings) { mutableStateOf(settings) }
    var limit by rememberSaveable { mutableStateOf(settings.limit.toString()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    StudyColumn("Настройки", onBack) {
        StudyPanel {
            Text("Тема оформления", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "Как в системе", "light" to "Светлая", "dark" to "Тёмная").forEach { (key, label) ->
                    FilterChip(value.theme == key, { value = value.copy(theme = key, dark = key == "dark") }, label = { Text(label) })
                }
            }
        }
        StudyPanel {
            Text("Прохождение тестов", style = MaterialTheme.typography.titleMedium)
            StudyField(limit, { limit = it.filter(Char::isDigit).take(3) }, "Число вопросов (0 — все)")
            Preference("Перемешивать вопросы", value.shuffleQuestions) { value = value.copy(shuffleQuestions = it) }
            Preference("Перемешивать варианты", value.shuffleOptions) { value = value.copy(shuffleOptions = it) }
            Preference("Показывать подсказки", value.showHints) { value = value.copy(showHints = it) }
            Preference("Показывать ключ после теста", value.showAnswers) { value = value.copy(showAnswers = it) }
            Text("Таймер по умолчанию")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 10, 20, 40).forEach { minutes ->
                    FilterChip(value.minutes == minutes, { value = value.copy(minutes = minutes) },
                        label = { Text(if (minutes == 0) "Без таймера" else "$minutes мин") })
                }
            }
        }
        StudyPanel(PanelTone.SKY) {
            Text("Интеграция с ИИ", style = MaterialTheme.typography.titleMedium)
            Text(if (value.server.isBlank()) "Сервер пока не настроен." else "Проверь подключение в разделе «Создать с ИИ».")
            StudyField(value.server, { value = value.copy(server = it.trim()) }, "Адрес сервера")
            OutlinedTextField(value.accessToken, { value = value.copy(accessToken = it.trim()) },
                label = { Text("Код доступа к серверу") }, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation())
        }
        error?.let { StudyError(it) }
        Button(onClick = {
            runCatching {
                if (value.server.isNotBlank()) AiClient.baseUri(value.server)
                if (onSave(value.copy(limit = (limit.toIntOrNull() ?: 0).coerceIn(0, 500)))) onBack()
            }.onFailure { error = it.message }
        }) { Text("Сохранить настройки") }
    }
}

@Composable
private fun Preference(label: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(enabled, onChange)
    }
}

@Composable
internal fun StudyField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
}

@Composable
internal fun StudyColumn(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading(title)
        content()
    }
}
