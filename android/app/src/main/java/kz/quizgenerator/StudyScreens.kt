package kz.quizgenerator

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Composable
internal fun ManualQuizScreen(onSave: (Quiz) -> Unit, onBack: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var question by rememberSaveable { mutableStateOf("") }
    var options by rememberSaveable { mutableStateOf(arrayListOf("", "")) }
    var correct by rememberSaveable { mutableStateOf<Int?>(null) }
    var explanation by rememberSaveable { mutableStateOf("") }
    var questions by remember { mutableStateOf(emptyList<QuizQuestion>()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun draft(): QuizQuestion {
        require(question.isNotBlank() && options.none(String::isBlank)) { "Введите вопрос и все варианты" }
        return QuizQuestion(UUID.randomUUID().toString(), question.trim(), options.map(String::trim),
            correct, explanation.trim().ifEmpty { null })
    }

    StudyColumn("Создать тест вручную", onBack) {
        StudyField(title, { title = it }, "Название")
        questions.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}. ${item.text}", modifier = Modifier.weight(1f))
                TextButton(onClick = { questions = questions.filterIndexed { position, _ -> position != index } }) {
                    Text("Убрать")
                }
            }
        }
        StudyField(question, { question = it }, "Следующий вопрос")
        options.forEachIndexed { index, value ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(correct == index, { correct = index })
                OutlinedTextField(value, { update -> options = ArrayList(options).also { it[index] = update } },
                    label = { Text("Вариант ${index + 1}") }, modifier = Modifier.weight(1f))
            }
        }
        Row {
            TextButton(enabled = options.size < 8, onClick = { options = ArrayList(options).also { it.add("") } }) { Text("+ вариант") }
            TextButton(enabled = options.size > 2, onClick = {
                options = ArrayList(options.dropLast(1))
                if (correct?.let { it !in options.indices } == true) correct = null
            }) { Text("− вариант") }
            TextButton(onClick = { correct = null }) { Text("Без ключа") }
        }
        StudyField(explanation, { explanation = it }, "Пояснение")
        OutlinedButton(enabled = questions.size < 500, onClick = {
            runCatching { questions = questions + draft() }.onSuccess {
                question = ""; options = arrayListOf("", ""); correct = null; explanation = ""; error = null
            }.onFailure { error = it.message }
        }) { Text("Добавить вопрос") }
        Button(onClick = {
            runCatching {
                val items = if (question.isNotBlank() || options.any(String::isNotBlank)) questions + draft() else questions
                onSave(StudyLogic.manualQuiz(title, items))
            }.onFailure { error = it.message }
        }) { Text("К предварительному просмотру") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
internal fun FlashcardScreen(cards: List<Flashcard>, onSave: (List<Flashcard>) -> Unit, onBack: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(0) }
    var flipped by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var front by rememberSaveable { mutableStateOf("") }
    var back by rememberSaveable { mutableStateOf("") }
    val card = cards.getOrNull(selected.coerceAtMost(cards.lastIndex.coerceAtLeast(0)))
    StudyColumn("Карточки", onBack) {
        if (card == null) Text("Добавьте первую карточку.")
        else {
            Text("Карточка ${cards.indexOf(card) + 1} из ${cards.size}")
            Card(onClick = { flipped = !flipped }, modifier = Modifier.fillMaxWidth()) {
                Text(if (flipped) card.back else card.front, modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.titleLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { flipped = !flipped }) { Text("Перевернуть") }
                OutlinedButton(onClick = { selected = (cards.indexOf(card) + 1) % cards.size; flipped = false }) { Text("Далее") }
            }
            TextButton(onClick = { editing = card.id; front = card.front; back = card.back }) { Text("Редактировать карточку") }
        }
        StudyField(front, { front = it }, "Лицевая сторона")
        StudyField(back, { back = it }, "Обратная сторона")
        Button(enabled = front.isNotBlank() && back.isNotBlank(), onClick = {
            val id = editing
            val changed = Flashcard(id ?: UUID.randomUUID().toString(), front.trim(), back.trim())
            onSave(if (id == null) cards + changed else cards.map { if (it.id == id) changed else it })
            if (id == null) selected = cards.size
            flipped = false; editing = null; front = ""; back = ""
        }) { Text(if (editing == null) "Добавить карточку" else "Сохранить правки") }
        if (editing != null) TextButton(onClick = { editing = null; front = ""; back = "" }) { Text("Отменить редактирование") }
    }
}

@Composable
internal fun PreparationScreen(data: StudyData, quizzes: List<Quiz>, onSave: (List<Exam>) -> Unit, onBack: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var topics by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var links by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val today = LocalDate.now()
    val score = data.attempts.sumOf { it.score.correct }
    val total = data.attempts.sumOf { it.score.gradedTotal }
    StudyColumn("Подготовка", onBack) {
        Text("Пройдено тестов: ${data.attempts.size} · Верно: $score из $total")
        data.exams.sortedBy(Exam::date).forEach { exam ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${exam.title} · ${exam.date}", style = MaterialTheme.typography.titleMedium)
                    val plan = StudyLogic.plan(exam, quizzes, data.attempts, today)
                    if (plan.isEmpty()) Text(if (exam.date < today) "Дата экзамена прошла" else "Добавьте темы или тесты")
                    plan.forEach { Text("${it.date} · ${it.topic}") }
                    TextButton(onClick = {
                        selectedId = exam.id; title = exam.title; date = exam.date.toString()
                        topics = exam.topics.joinToString(", "); links = ArrayList(exam.quizIds)
                    }) { Text("Изменить экзамен") }
                }
            }
        }
        Text("Учебные материалы и темы", style = MaterialTheme.typography.titleMedium)
        StudyField(title, { title = it }, "Дисциплина")
        StudyField(date, { date = it }, "Дата (ГГГГ-ММ-ДД)")
        StudyField(topics, { topics = it }, "Темы через запятую")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            quizzes.forEach { quiz ->
                OutlinedButton(onClick = {
                    links = ArrayList(if (quiz.id in links) links - quiz.id else links + quiz.id)
                }) { Text((if (quiz.id in links) "✓ " else "") + quiz.title) }
            }
        }
        Button(enabled = title.isNotBlank(), onClick = {
            runCatching {
                val parsedDate = LocalDate.parse(date.trim())
                val exam = Exam(selectedId ?: UUID.randomUUID().toString(), title.trim(), parsedDate,
                    topics.split(',').map(String::trim).filter(String::isNotEmpty).distinct(), links.toList())
                onSave(data.exams.filterNot { it.id == exam.id } + exam)
            }.onSuccess { title = ""; topics = ""; selectedId = null; links = arrayListOf(); error = null }
                .onFailure { error = "Проверьте дату экзамена (ГГГГ-ММ-ДД)" }
        }) { Text(if (selectedId == null) "Создать экзамен" else "Сохранить экзамен") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("История занятий", style = MaterialTheme.typography.titleMedium)
        data.attempts.takeLast(20).asReversed().forEach { attempt ->
            val day = Instant.ofEpochMilli(attempt.completedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            Text("$day · ${attempt.title} · ${attempt.score.correct}/${attempt.score.gradedTotal}")
            attempt.mistakes.forEach { mistake ->
                Text("${mistake.question} · Правильно: ${mistake.correct}")
                mistake.explanation?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
internal fun StudyPreferences(settings: StudySettings, onSave: (StudySettings) -> Unit, onBack: () -> Unit) {
    var value by remember(settings) { mutableStateOf(settings) }
    var limit by rememberSaveable { mutableStateOf(settings.limit.toString()) }
    StudyColumn("Настройки", onBack) {
        Text("TestHelper · подготовка к экзаменам")
        StudyField(limit, { limit = it.filter(Char::isDigit).take(3) }, "Вопросов на тест (0 — все)")
        Preference("Случайный порядок вопросов", value.shuffleQuestions) { value = value.copy(shuffleQuestions = it) }
        Preference("Перемешивать варианты", value.shuffleOptions) { value = value.copy(shuffleOptions = it) }
        Preference("Показывать подсказки", value.showHints) { value = value.copy(showHints = it) }
        Preference("Показывать ключ после теста", value.showAnswers) { value = value.copy(showAnswers = it) }
        Preference("Тёмная тема", value.dark) { value = value.copy(dark = it) }
        Text("Таймер по умолчанию")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 10, 20, 40).forEach { minutes ->
                OutlinedButton(onClick = { value = value.copy(minutes = minutes) }) {
                    Text((if (value.minutes == minutes) "✓ " else "") + "$minutes мин")
                }
            }
        }
        Button(onClick = { onSave(value.copy(limit = (limit.toIntOrNull() ?: 0).coerceIn(0, 500))); onBack() }) {
            Text("Сохранить настройки")
        }
    }
}

@Composable
private fun Preference(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
internal fun StudyField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
}

@Composable
internal fun StudyColumn(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = onBack) { Text("Назад") }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        content()
    }
}
