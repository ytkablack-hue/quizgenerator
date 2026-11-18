package kz.quizgenerator

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = QuizRepository(applicationContext)
        val editor = ViewModelProvider(this)[EditorModel::class.java]
        setContent {
            val store = remember { StudyStore(File(filesDir, "study.json")) }
            val initial = remember { runCatching { store.load() } }
            var study by remember { mutableStateOf(initial.getOrDefault(StudyData())) }
            var failure by remember { mutableStateOf(if (initial.isFailure) "Не удалось прочитать данные подготовки" else null) }
            SoftStudyTheme(study.settings.theme) {
                TestHelperApp(repository, editor, study, { updated ->
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

private enum class Page(val title: String, val glyph: Glyph? = null) {
    HOME("Главная", Glyph.HOME), CREATE("Создать", Glyph.ADD), LIBRARY("Библиотека", Glyph.LIBRARY),
    PREPARATION("Подготовка", Glyph.PLAN), AI("Создать с ИИ"), CARDS("Карточки"), SETTINGS("Настройки"),
    EDITOR("Редактор"), QUESTION("Вопрос"), REVIEW("Тест"), QUIZ("Прохождение"), RESULTS("Результат")
}

private enum class Confirmation { DISCARD_DRAFT, LEAVE_QUIZ, FINISH_QUIZ }

@Composable
private fun TestHelperApp(repository: QuizRepository, editor: EditorModel, study: StudyData,
                          update: (StudyData) -> Boolean, storageError: String?) {
    var quizzes by remember { mutableStateOf(repository.listQuizzes()) }
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    var editorReturn by rememberSaveable { mutableStateOf(Page.CREATE) }
    var settingsReturn by rememberSaveable { mutableStateOf(Page.HOME) }
    var quizId by rememberSaveable { mutableStateOf<String?>(null) }
    val currentQuiz = quizzes.firstOrNull { it.id == quizId }
    var questionIndex by rememberSaveable { mutableIntStateOf(0) }
    var answers by rememberSaveable { mutableStateOf(arrayListOf<Int?>()) }
    var seed by rememberSaveable { mutableLongStateOf(0L) }
    var attemptId by rememberSaveable { mutableStateOf("") }
    var timerMinutes by rememberSaveable { mutableIntStateOf(study.settings.minutes) }
    var deadline by rememberSaveable { mutableLongStateOf(0L) }
    var seconds by remember(deadline) { mutableIntStateOf(QuizTimer.secondsLeft(deadline, SystemClock.elapsedRealtime())) }
    var expired by rememberSaveable { mutableStateOf(false) }
    val prepared = remember(currentQuiz, study.settings, seed) { currentQuiz?.let { StudyLogic.prepare(it, study.settings, seed) } }

    var draftError by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryError by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmation by rememberSaveable { mutableStateOf<Confirmation?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun openDraft(draft: QuizDraft, back: Page) {
        editor.draft = draft
        editorReturn = back
        draftError = null
        page = Page.EDITOR
    }

    fun closeDraft() {
        editor.draft = null
        editor.fields = null
        draftError = null
        page = editorReturn
    }

    fun openQuiz(quiz: Quiz) {
        quizId = quiz.id
        timerMinutes = study.settings.minutes
        page = Page.REVIEW
    }

    fun start(quiz: Quiz) {
        questionIndex = 0
        seed = System.nanoTime()
        answers = ArrayList(List(StudyLogic.prepare(quiz, study.settings, seed).questions.size) { null })
        attemptId = UUID.randomUUID().toString()
        deadline = QuizTimer.deadline(SystemClock.elapsedRealtime(), timerMinutes)
        expired = false
        confirmation = null
        page = Page.QUIZ
    }

    fun finish() {
        if (page != Page.QUIZ) return
        val quiz = prepared ?: return
        if (answers.size != quiz.questions.size) return
        val attempt = StudyLogic.complete(attemptId, quiz, answers, System.currentTimeMillis())
        if (study.attempts.none { it.id == attempt.id }) update(study.copy(attempts = study.attempts + attempt))
        expired = QuizTimer.expired(deadline, SystemClock.elapsedRealtime())
        confirmation = null
        page = Page.RESULTS
    }

    val finishLatest by rememberUpdatedState(::finish)
    LaunchedEffect(page, deadline) {
        while (page == Page.QUIZ && deadline != 0L) {
            val now = SystemClock.elapsedRealtime()
            seconds = QuizTimer.secondsLeft(deadline, now)
            if (QuizTimer.expired(deadline, now)) { finishLatest(); break }
            delay(250)
        }
    }

    LaunchedEffect(editor.imported) {
        editor.imported?.let {
            openDraft(QuizDraft(it.quiz, document = it.document, warnings = it.warnings), Page.CREATE)
            editor.imported = null
        }
    }

    // The editor survives Activity recreation in a ViewModel. A cold process may have no pending draft.
    LaunchedEffect(page, currentQuiz, editor.draft) {
        if (page in setOf(Page.EDITOR, Page.QUESTION) && editor.draft == null) page = editorReturn
        else if (page == Page.QUESTION && editor.fields == null) page = Page.EDITOR
        if (page in setOf(Page.QUIZ, Page.RESULTS, Page.REVIEW) && currentQuiz == null) page = Page.LIBRARY
    }

    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { editor.import(repository, it) }
    }

    fun goBack() {
        when (page) {
            Page.HOME -> Unit
            Page.CREATE -> { editor.cancel(); page = Page.HOME }
            Page.AI -> page = Page.CREATE
            Page.CARDS, Page.REVIEW, Page.RESULTS -> page = Page.LIBRARY
            Page.SETTINGS -> page = settingsReturn
            Page.QUESTION -> page = Page.EDITOR
            Page.EDITOR -> if (editor.draft?.isDirty == true) confirmation = Confirmation.DISCARD_DRAFT else closeDraft()
            Page.QUIZ -> confirmation = Confirmation.LEAVE_QUIZ
            else -> page = Page.HOME
        }
    }

    BackHandler(page != Page.HOME && page != Page.QUESTION) { goBack() }

    when (confirmation) {
        Confirmation.DISCARD_DRAFT -> StudyConfirmation("Отменить правки теста?",
            "Изменения в редакторе не сохранены.", "Выйти", { confirmation = null }) { confirmation = null; closeDraft() }
        Confirmation.LEAVE_QUIZ -> StudyConfirmation("Прервать тест?",
            "Текущие ответы не попадут в историю.", "Прервать", { confirmation = null }) {
            confirmation = null
            page = Page.REVIEW
            deadline = 0L
        }
        Confirmation.FINISH_QUIZ -> StudyConfirmation("Завершить тест?",
            "Результат будет добавлен в историю.", "Завершить", { confirmation = null }, ::finish)
        null -> Unit
    }

    deleteId?.let { id ->
        quizzes.firstOrNull { it.id == id }?.let { quiz ->
            StudyConfirmation("Удалить тест?", quiz.title, "Удалить", { deleteId = null }) {
                runCatching { repository.deleteQuiz(quiz); quizzes = repository.listQuizzes() }
                    .onSuccess { scope.launch { snackbar.showSnackbar("Тест удалён") } }
                    .onFailure { libraryError = it.message ?: "Не удалось удалить тест" }
                deleteId = null
            }
        }
    }

    val tabs = listOf(Page.HOME, Page.CREATE, Page.LIBRARY, Page.PREPARATION)
    val mainPage = page in tabs
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (page != Page.QUESTION && page != Page.EDITOR && page != Page.QUIZ) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (!mainPage && page != Page.CARDS) TextButton(onClick = ::goBack) { Text("Назад") }
                        Text(if (mainPage) "TestHelper" else page.title,
                            modifier = Modifier.weight(1f).padding(start = 4.dp),
                            style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (mainPage) IconButton(onClick = { settingsReturn = page; page = Page.SETTINGS }) {
                            StudyIcon(Glyph.SETTINGS, "Настройки")
                        } else Spacer(Modifier.height(48.dp))
                    }
                }
            }
        },
        bottomBar = {
            if (mainPage) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEach { tab ->
                    NavigationBarItem(page == tab, onClick = {
                        if (tab != Page.CREATE) editor.cancel()
                        page = tab
                    }, icon = { StudyIcon(requireNotNull(tab.glyph)) }, label = { Text(tab.title, maxLines = 2) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer))
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxWidth().fillMaxHeight().padding(horizontal = 14.dp, vertical = 8.dp)) {
                storageError?.let { StudyError(it); Spacer(Modifier.height(6.dp)) }
                when (page) {
                    Page.HOME -> HomeScreen(study, { page = Page.CREATE }, { page = Page.CARDS },
                        {
                            val recent = study.attempts.asReversed().firstNotNullOfOrNull { attempt ->
                                quizzes.firstOrNull { it.id == attempt.quizId }
                            }
                            if (recent == null) page = Page.LIBRARY else openQuiz(recent)
                        }, { page = Page.PREPARATION })
                    Page.CREATE -> CreateScreen(editor.load, editor.failure,
                        { chooser.launch(arrayOf("text/plain", "application/json", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/octet-stream")) },
                        editor::cancel, { page = Page.AI }, { openDraft(QuizDraft(emptyQuiz()), Page.CREATE) })
                    Page.LIBRARY -> LibraryScreen(quizzes, repository::modifiedAt, libraryError, ::openQuiz,
                        { openDraft(QuizDraft(it, original = it), Page.LIBRARY) }, { deleteId = it.id },
                        repository::canDelete, { page = Page.CARDS }, { page = Page.CREATE })
                    Page.PREPARATION -> PreparationScreen(study, quizzes) { update(study.copy(exams = it)) }
                    Page.CARDS -> FlashcardScreen(study.cards, { update(study.copy(cards = it)) }, { page = Page.LIBRARY })
                    Page.SETTINGS -> StudyPreferences(study.settings, { update(study.copy(settings = it)) }, ::goBack)
                    Page.AI -> AiScreen(study.settings, study.attempts.lastOrNull { it.mistakes.isNotEmpty() },
                        repository::readStudyMaterial, saveQuiz = { openDraft(QuizDraft(it), Page.AI) },
                        saveCards = { update(study.copy(cards = study.cards + it)) }, onBack = ::goBack)
                    Page.EDITOR -> editor.draft?.let { draft ->
                        DraftScreen(draft,
                            { editor.draft = draft.copy(quiz = it); draftError = null },
                            { index ->
                                editor.fields = QuestionFields(draft.quiz.questions.getOrNull(index) ?: emptyQuestion())
                                page = Page.QUESTION
                            }, ::goBack, {
                                runCatching {
                                    val trimmed = draft.quiz.copy(title = draft.quiz.title.trim())
                                    EditorRules.validate(trimmed.title, trimmed.questions)
                                    if (draft.original == null) repository.addQuiz(trimmed) else repository.saveQuiz(trimmed)
                                }.onSuccess {
                                    quizzes = repository.listQuizzes()
                                    editor.draft = null
                                    openQuiz(it)
                                    scope.launch { snackbar.showSnackbar("Тест сохранён") }
                                }.onFailure { draftError = it.message ?: "Не удалось сохранить тест" }
                            }, draftError)
                    }
                    Page.QUESTION -> editor.draft?.let { draft ->
                        editor.fields?.let { fields ->
                            QuestionScreen(fields, { editor.fields = null; page = Page.EDITOR }) { changed ->
                                val questions = draft.quiz.questions.toMutableList()
                                val index = questions.indexOfFirst { it.id == changed.id }
                                if (index < 0) questions.add(changed) else questions[index] = changed
                                editor.draft = draft.copy(quiz = draft.quiz.copy(questions = questions))
                                editor.fields = null
                                draftError = null
                                page = Page.EDITOR
                            }
                        }
                    }
                    Page.REVIEW -> currentQuiz?.let { quiz ->
                        StartScreen(quiz, timerMinutes, { timerMinutes = it },
                            { openDraft(QuizDraft(quiz, original = quiz), Page.REVIEW) }, { start(quiz) })
                    }
                    Page.QUIZ -> prepared?.takeIf { questionIndex in it.questions.indices && answers.size == it.questions.size }?.let {
                        RunScreen(it, questionIndex, answers[questionIndex], seconds.takeIf { deadline != 0L },
                            study.settings.showHints, { option ->
                                if (QuizTimer.expired(deadline, SystemClock.elapsedRealtime())) finish()
                                else answers = ArrayList(answers).also { list -> list[questionIndex] = option }
                            }, { if (questionIndex > 0) questionIndex-- }, {
                                if (QuizTimer.expired(deadline, SystemClock.elapsedRealtime())) finish() else questionIndex++
                            }, { confirmation = Confirmation.FINISH_QUIZ })
                    }
                    Page.RESULTS -> prepared?.takeIf { answers.size == it.questions.size }?.let {
                        ResultsScreen(it, answers, expired, study.settings.showAnswers, { currentQuiz?.let(::start) }, ::goBack)
                    }
                }
            }
        }
    }
}
