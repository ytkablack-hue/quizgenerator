package kz.quizgenerator

import android.os.Bundle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import java.io.File
import java.time.LocalDate
import java.util.UUID
import android.os.SystemClock
import androidx.compose.runtime.saveable.rememberSaveable
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
        setContent {
            val store = remember { StudyStore(File(filesDir, "study.json")) }
            val initial = remember { runCatching { store.load() } }
            var study by remember { mutableStateOf(initial.getOrDefault(StudyData())) }
            var failure by remember { mutableStateOf(if (initial.isFailure) "Не удалось прочитать данные подготовки" else null) }
            MaterialTheme(colorScheme = if (study.settings.dark) darkColorScheme() else lightColorScheme()) {
                QuizGeneratorApp(repository, study, { updated ->
                    runCatching {
                        check(initial.isSuccess) { "Файл подготовки не читается" }
                        store.save(updated)
                    }.onSuccess { study = updated; failure = null }
                        .onFailure { failure = "Не удалось сохранить данные подготовки" }.isSuccess
                }, failure)
            }
        }
    }
}

private enum class Page { LIBRARY, CREATE, MANUAL, CARDS, PREPARATION, SETTINGS, REVIEW, EDIT, QUIZ, RESULTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuizGeneratorApp(repository: QuizRepository, study: StudyData, update: (StudyData) -> Boolean, storageError: String?) {
    var quizzes by remember { mutableStateOf(repository.listQuizzes()) }
    var page by rememberSaveable { mutableStateOf(Page.LIBRARY) }
    var quizId by rememberSaveable { mutableStateOf<String?>(null) }
    val currentQuiz = quizzes.firstOrNull { it.id == quizId }
    var questionIndex by rememberSaveable { mutableStateOf(0) }
    var answers by rememberSaveable { mutableStateOf(arrayListOf<Int?>()) }
    var sessionSeed by rememberSaveable { mutableStateOf(0L) }
    var attemptId by rememberSaveable { mutableStateOf("") }
    val prepared = remember(currentQuiz, study.settings, sessionSeed) {
        currentQuiz?.let { StudyLogic.prepare(it, study.settings, sessionSeed) }
    }
    var search by rememberSaveable { mutableStateOf("") }
    var sorted by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Quiz?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var editingIndex by remember { mutableStateOf(0) }
    var draftText by remember { mutableStateOf("") }
    var draftOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var draftCorrect by remember { mutableStateOf<Int?>(null) }
    var draftExplanation by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }
    var timerMinutes by rememberSaveable { mutableStateOf(study.settings.minutes) }
    var deadline by rememberSaveable { mutableStateOf(0L) }
    var remainingSeconds by remember(deadline) {
        mutableStateOf(QuizTimer.secondsLeft(deadline, SystemClock.elapsedRealtime()))
    }
    var timeExpired by rememberSaveable { mutableStateOf(false) }

    fun finishQuiz() {
        if (page != Page.QUIZ) return
        val quiz = prepared ?: return
        val attempt = StudyLogic.complete(attemptId, quiz, answers, System.currentTimeMillis())
        if (study.attempts.none { it.id == attempt.id }) update(study.copy(attempts = study.attempts + attempt))
        timeExpired = QuizTimer.expired(deadline, SystemClock.elapsedRealtime())
        page = Page.RESULTS
    }

    LaunchedEffect(page, deadline) {
        if (page == Page.EDIT && draftOptions.isEmpty()) page = Page.REVIEW
        while (page == Page.QUIZ && deadline != 0L) {
            val now = SystemClock.elapsedRealtime()
            remainingSeconds = QuizTimer.secondsLeft(deadline, now)
            if (QuizTimer.expired(deadline, now)) {
                finishQuiz()
                break
            }
            delay(250)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val imported = repository.importQuiz(uri)
                quizzes = repository.listQuizzes()
                quizId = imported.quiz.id
                timerMinutes = study.settings.minutes
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
        quizId = null
        questionIndex = 0
        deadline = 0L
        answers = arrayListOf()
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

    deleteTarget?.let { quiz ->
        AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("Удалить тест?") },
            text = { Text(quiz.title) },
            confirmButton = { TextButton(onClick = {
                runCatching { repository.deleteQuiz(quiz); quizzes = repository.listQuizzes() }
                    .onFailure { importError = it.message }
                deleteTarget = null
            }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Отмена") } })
    }

    Scaffold(topBar = { TopAppBar(title = { Text("TestHelper") }) }) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            storageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            when (page) {
                Page.CREATE -> StudyColumn("Создать", ::returnHome) {
                    Button(onClick = { page = Page.MANUAL }) { Text("Создать вручную") }
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Импортировать TXT, DOCX или JSON") }
                    importError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                Page.MANUAL -> ManualQuizScreen(onSave = { quiz ->
                    val saved = repository.addQuiz(quiz)
                    quizzes = repository.listQuizzes()
                    quizId = saved.id
                    importWarnings = emptyList()
                    timerMinutes = study.settings.minutes
                    page = Page.REVIEW
                }, onBack = { page = Page.CREATE })
                Page.CARDS -> FlashcardScreen(study.cards, { update(study.copy(cards = it)) }, ::returnHome)
                Page.PREPARATION -> PreparationScreen(study, quizzes, { update(study.copy(exams = it)) }, ::returnHome)
                Page.SETTINGS -> StudyPreferences(study.settings, { update(study.copy(settings = it)) }, ::returnHome)
                Page.LIBRARY -> {
                    Text("Подготовка к экзаменам", style = MaterialTheme.typography.headlineMedium)
                    study.exams.filter { it.date >= LocalDate.now() }.minByOrNull(Exam::date)?.let {
                        Text("Ближайший экзамен · ${it.title} · ${it.date}")
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { page = Page.CREATE }) { Text("Создать тест") }
                        OutlinedButton(onClick = { page = Page.CARDS }) { Text("Карточки") }
                        OutlinedButton(onClick = { page = Page.PREPARATION }) { Text("План и статистика") }
                        OutlinedButton(onClick = { page = Page.SETTINGS }) { Text("Настройки") }
                    }
                    Text("Мои тесты", style = MaterialTheme.typography.titleLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(search, { search = it }, label = { Text("Поиск") }, modifier = Modifier.weight(1f))
                        TextButton(onClick = { sorted = !sorted }) { Text(if (sorted) "А–Я" else "Сортировать") }
                    }
                    val matching = quizzes.filter { it.title.contains(search.trim(), ignoreCase = true) }
                    importError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    HorizontalDivider()
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(if (sorted) matching.sortedBy { it.title.lowercase() } else matching, key = { it.id }) { quiz ->
                            Card(onClick = {
                                quizId = quiz.id
                                timerMinutes = study.settings.minutes
                                importWarnings = emptyList()
                                page = Page.REVIEW
                            }) {
                                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                    Text(quiz.title, style = MaterialTheme.typography.titleLarge)
                                    Text("Вопросов: ${quiz.questions.size}")
                                    if (repository.canDelete(quiz)) TextButton(onClick = { deleteTarget = quiz }) { Text("Удалить") }
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
                        sessionSeed = System.nanoTime()
                        answers = ArrayList(List(StudyLogic.prepare(quiz, study.settings, sessionSeed).questions.size) { null })
                        attemptId = UUID.randomUUID().toString()
                        deadline = QuizTimer.deadline(SystemClock.elapsedRealtime(), timerMinutes)
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
                            quizId = saved.id
                            quizzes = repository.listQuizzes()
                            editError = null
                            page = Page.REVIEW
                        }.onFailure { editError = it.message ?: "Не удалось сохранить вопрос" }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить исправления") }
                    TextButton(onClick = { page = Page.REVIEW }) { Text("Отмена") }
                }
                Page.QUIZ -> prepared?.let { quiz ->
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
                                        answers = ArrayList(answers).also { it[questionIndex] = optionIndex }
                                    }
                                ).padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = answers[questionIndex] == optionIndex,
                                    onClick = {
                                        answers = ArrayList(answers).also { it[questionIndex] = optionIndex }
                                    }
                                )
                                Text(optionText, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        if (study.settings.showHints && !question.explanation.isNullOrBlank()) {
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
                            if (questionIndex == quiz.questions.lastIndex ||
                                QuizTimer.expired(deadline, SystemClock.elapsedRealtime())) finishQuiz()
                            else questionIndex++
                        }) {
                            Text(if (questionIndex == quiz.questions.lastIndex) "Завершить" else "Далее")
                        }
                    }
                    TextButton(onClick = { returnHome() }) { Text("В библиотеку") }
                }
                Page.RESULTS -> prepared?.let { quiz ->
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
                                    if (study.settings.showAnswers) Text(
                                        when {
                                            question.correctIndex == null -> "Правильный ответ не указан в источнике"
                                            given == question.correctIndex -> "Верно"
                                            else -> "Правильно: ${question.options[question.correctIndex]}"
                                        }
                                    )
                                    if (study.settings.showAnswers) question.explanation?.let { Text("Пояснение: $it") }
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
