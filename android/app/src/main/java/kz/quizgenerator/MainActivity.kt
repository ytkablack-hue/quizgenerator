package kz.quizgenerator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = QuizRepository(applicationContext)
        setContent { MaterialTheme { QuizGeneratorApp(repository) } }
    }
}

private enum class Page { LIBRARY, REVIEW, EDIT, QUIZ, RESULTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuizGeneratorApp(repository: QuizRepository) {
    var quizzes by remember { mutableStateOf(repository.listQuizzes()) }
    var page by remember { mutableStateOf(Page.LIBRARY) }
    var currentQuiz by remember { mutableStateOf<Quiz?>(null) }
    var questionIndex by remember { mutableStateOf(0) }
    var answers by remember { mutableStateOf<List<Int?>>(emptyList()) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var editingIndex by remember { mutableStateOf(0) }
    var draftText by remember { mutableStateOf("") }
    var draftOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var draftCorrect by remember { mutableStateOf<Int?>(null) }
    var draftExplanation by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }
    var timerMinutes by remember { mutableStateOf(0) }
    var remainingSeconds by remember { mutableStateOf(0) }
    var timeExpired by remember { mutableStateOf(false) }

    LaunchedEffect(page, currentQuiz?.id) {
        if (page == Page.QUIZ && timerMinutes > 0) {
            while (remainingSeconds > 0) {
                delay(1000)
                remainingSeconds--
            }
            timeExpired = true
            page = Page.RESULTS
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val imported = repository.importQuiz(uri)
                quizzes = repository.listQuizzes()
                currentQuiz = imported.quiz
                importWarnings = imported.warnings
                importError = null
                page = Page.REVIEW
            }.onFailure { error ->
                importError = error.message ?: "Не удалось прочитать тест"
            }
        }
    }

    fun returnHome() {
        page = Page.LIBRARY
        currentQuiz = null
        questionIndex = 0
        answers = emptyList()
    }

    fun openEditor(index: Int, question: QuizQuestion) {
        editingIndex = index
        draftText = question.text
        draftOptions = question.options
        draftCorrect = question.correctIndex
        draftExplanation = question.explanation.orEmpty()
        editError = null
        page = Page.EDIT
    }

    Scaffold(topBar = { TopAppBar(title = { Text("QuizGenerator") }) }) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (page) {
                Page.LIBRARY -> {
                    Text("Мои тесты", style = MaterialTheme.typography.headlineMedium)
                    Text("Все сохранённые тесты доступны без интернета.")
                    Button(onClick = { picker.launch(arrayOf("*/*")) }) {
                        Text("Импортировать Word / TXT / JSON")
                    }
                    Text("PDF, фото и распознавание ИИ добавим позже.", style = MaterialTheme.typography.bodySmall)
                    importError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    HorizontalDivider()
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(quizzes, key = { it.id }) { quiz ->
                            Card(onClick = {
                                currentQuiz = quiz
                                importWarnings = emptyList()
                                page = Page.REVIEW
                            }) {
                                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                    Text(quiz.title, style = MaterialTheme.typography.titleLarge)
                                    Text("Вопросов: ${quiz.questions.size}")
                                }
                            }
                        }
                    }
                }
                Page.REVIEW -> currentQuiz?.let { quiz ->
                    Text("Проверка перед тестом", style = MaterialTheme.typography.headlineSmall)
                    Text(quiz.title, style = MaterialTheme.typography.titleMedium)
                    val withoutAnswers = quiz.questions.count { it.correctIndex == null }
                    if (withoutAnswers > 0) {
                        Text("Без ключа ответа: $withoutAnswers. Их можно исправить вручную; пока они не учитываются в оценке.")
                    }
                    importWarnings.forEach { warning ->
                        Text("Внимание: $warning", color = MaterialTheme.colorScheme.error)
                    }
                    Text("Таймер:", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0, 10, 20, 40).forEach { minutes ->
                            OutlinedButton(onClick = { timerMinutes = minutes }) {
                                Text((if (timerMinutes == minutes) "✓ " else "") +
                                    (if (minutes == 0) "Без таймера" else "$minutes мин"))
                            }
                        }
                    }
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(quiz.questions.indices.toList()) { index ->
                            val question = quiz.questions[index]
                            Card {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("${index + 1}. ${question.text}")
                                    Text(
                                        question.correctIndex?.let { "Ключ: ${question.options[it]}" }
                                            ?: "Ключ неизвестен — оценка не выставляется",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    OutlinedButton(onClick = { openEditor(index, question) }) {
                                        Text("Проверить / исправить")
                                    }
                                }
                            }
                        }
                    }
                    Button(onClick = {
                        questionIndex = 0
                        answers = List(quiz.questions.size) { null }
                        remainingSeconds = timerMinutes * 60
                        timeExpired = false
                        page = Page.QUIZ
                    }, modifier = Modifier.fillMaxWidth()) { Text("Начать тест") }
                    TextButton(onClick = { returnHome() }) { Text("В библиотеку") }
                }
                Page.EDIT -> currentQuiz?.let { quiz ->
                    Text("Редактор вопроса ${editingIndex + 1}", style = MaterialTheme.typography.headlineSmall)
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = draftText,
                            onValueChange = { draftText = it },
                            label = { Text("Вопрос") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Отметь один правильный ответ или оставь ключ неизвестным.")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = draftCorrect == null, onClick = { draftCorrect = null })
                            TextButton(onClick = { draftCorrect = null }) { Text("Ключ неизвестен") }
                        }
                        draftOptions.forEachIndexed { index, option ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = draftCorrect == index, onClick = { draftCorrect = index })
                                OutlinedTextField(
                                    value = option,
                                    onValueChange = { updated ->
                                        draftOptions = draftOptions.toMutableList().also { it[index] = updated }
                                    },
                                    label = { Text("Вариант ${index + 1}") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        OutlinedTextField(
                            value = draftExplanation,
                            onValueChange = { draftExplanation = it },
                            label = { Text("Пояснение (необязательно)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        editError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    Button(onClick = {
                        runCatching {
                            require(draftText.isNotBlank()) { "Напиши текст вопроса" }
                            require(draftOptions.all { it.isNotBlank() }) { "Заполни все варианты" }
                            val old = quiz.questions[editingIndex]
                            val updated = old.copy(
                                text = draftText.trim(),
                                options = draftOptions.map { it.trim() },
                                correctIndex = draftCorrect,
                                explanation = draftExplanation.trim().ifEmpty { null }
                            )
                            val newQuestions = quiz.questions.toMutableList().also { it[editingIndex] = updated }
                            val saved = repository.saveQuiz(quiz.copy(questions = newQuestions))
                            currentQuiz = saved
                            quizzes = repository.listQuizzes()
                            editError = null
                            page = Page.REVIEW
                        }.onFailure { editError = it.message ?: "Не удалось сохранить вопрос" }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить исправления") }
                    TextButton(onClick = { page = Page.REVIEW }) { Text("Отмена") }
                }
                Page.QUIZ -> currentQuiz?.let { quiz ->
                    val question = quiz.questions[questionIndex]
                    var showExplanation by remember(quiz.id, questionIndex) { mutableStateOf(false) }
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (timerMinutes > 0) {
                            Text("Осталось: %02d:%02d".format(remainingSeconds / 60, remainingSeconds % 60))
                        }
                        Text("Вопрос ${questionIndex + 1} из ${quiz.questions.size}", style = MaterialTheme.typography.labelLarge)
                        Text(question.text, style = MaterialTheme.typography.titleLarge)
                        question.options.forEachIndexed { optionIndex, optionText ->
                            Row(
                                modifier = Modifier.fillMaxWidth().selectable(
                                    selected = answers[questionIndex] == optionIndex,
                                    onClick = {
                                        answers = answers.toMutableList().also { it[questionIndex] = optionIndex }
                                    }
                                ).padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = answers[questionIndex] == optionIndex,
                                    onClick = {
                                        answers = answers.toMutableList().also { it[questionIndex] = optionIndex }
                                    }
                                )
                                Text(optionText, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        if (!question.explanation.isNullOrBlank()) {
                            TextButton(onClick = { showExplanation = !showExplanation }) {
                                Text(if (showExplanation) "Скрыть пояснение" else "Пояснить вопрос")
                            }
                            if (showExplanation) Text(question.explanation)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { questionIndex-- },
                            enabled = questionIndex > 0
                        ) { Text("Назад") }
                        Button(onClick = {
                            if (questionIndex == quiz.questions.lastIndex) page = Page.RESULTS
                            else questionIndex++
                        }) {
                            Text(if (questionIndex == quiz.questions.lastIndex) "Завершить" else "Далее")
                        }
                    }
                    TextButton(onClick = { returnHome() }) { Text("В библиотеку") }
                }
                Page.RESULTS -> currentQuiz?.let { quiz ->
                    val score = QuizScorer.score(quiz, answers)
                    Text("Результат", style = MaterialTheme.typography.headlineMedium)
                    if (timeExpired) Text("Время вышло: тест завершён автоматически.")
                    Text("Верно: ${score.correct} из ${score.gradedTotal}", style = MaterialTheme.typography.titleLarge)
                    Text("Ошибок: ${score.incorrect} · Пропущено: ${score.skipped}")
                    if (score.ungraded > 0) {
                        Text("Без ключа ответа: ${score.ungraded}. Эти вопросы не влияют на оценку.")
                    }
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(quiz.questions.indices.toList()) { index ->
                            val question = quiz.questions[index]
                            val given = answers[index]
                            Card {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text("${index + 1}. ${question.text}")
                                    Text("Твой ответ: ${given?.let { question.options[it] } ?: "Нет ответа"}")
                                    Text(
                                        when {
                                            question.correctIndex == null -> "Правильный ответ не указан в источнике"
                                            given == question.correctIndex -> "Верно"
                                            else -> "Правильно: ${question.options[question.correctIndex]}"
                                        }
                                    )
                                    question.explanation?.let { Text("Пояснение: $it") }
                                }
                            }
                        }
                    }
                    Button(onClick = { returnHome() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Вернуться к тестам")
                    }
                }
            }
        }
    }
}
