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
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import kz.quizgenerator.ui.theme.SteamTheme
import kz.quizgenerator.ui.theme.SteamNavy
import kz.quizgenerator.ui.theme.SteamAccent
import kz.quizgenerator.ui.theme.SteamBlue
import kz.quizgenerator.ui.screens.RegistrationScreen
import kz.quizgenerator.ui.screens.CreateLibraryScreen
import kz.quizgenerator.ui.screens.QuizRow
import kz.quizgenerator.ui.screens.LearningHomeScreen
import kz.quizgenerator.ui.screens.LectureRow
import kz.quizgenerator.ui.screens.NewLectureScreen
import kz.quizgenerator.ui.screens.LectureStagesScreen
import kz.quizgenerator.ui.screens.StageRow
import kz.quizgenerator.ui.screens.LectureReadingScreen
import kz.quizgenerator.ui.screens.FlashcardsScreen
import kz.quizgenerator.ui.screens.CardRow
import kz.quizgenerator.ui.screens.CardEditorScreen
import kz.quizgenerator.ui.screens.LectureQuestionScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = QuizRepository(applicationContext)
        val accountStore = AccountStore(File(filesDir, "account.json"))
        val editor = ViewModelProvider(this)[EditorModel::class.java]
        setContent {
            val store = remember { StudyStore(File(filesDir, "study.json")) }
            val initial = remember { runCatching { store.load() } }
            var study by remember { mutableStateOf(initial.getOrDefault(StudyData())) }
            var failure by remember { mutableStateOf(if (initial.isFailure) "Не удалось прочитать данные подготовки" else null) }
            SteamTheme {
                TestHelperApp(repository, editor, accountStore, study, { updated ->
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
    PREPARATION("Обучение", Glyph.PLAN), AI("Создать с ИИ"), CARDS("Карточки"), SETTINGS("Настройки"),
    EDITOR("Редактор"), QUESTION("Вопрос"), REVIEW("Тест"), QUIZ("Прохождение"), RESULTS("Результат")
    , REGISTER("Регистрация"), LEARNING_NEW("Новая лекция"), LECTURE_STAGES("Этапы обучения"), LECTURE_READING("Чтение"),
    LECTURE_CARDS("Карточки лекции"), LECTURE_TEST("Тест по лекции"), LECTURE_CHECK("Проверка знаний"), CARD_FORM("Карточка"), EXAMS("Экзамены и история")
}

private enum class Confirmation { DISCARD_DRAFT, LEAVE_QUIZ, FINISH_QUIZ }

@Composable
private fun TestHelperApp(repository: QuizRepository, editor: EditorModel, accountStore: AccountStore, study: StudyData,
                          update: (StudyData) -> Boolean, storageError: String?) {
    val context = LocalContext.current
    var quizzes by remember { mutableStateOf(repository.listQuizzes()) }
    var page by rememberSaveable { mutableStateOf(if (accountStore.exists()) Page.LIBRARY else Page.REGISTER) }
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
    var query by rememberSaveable { mutableStateOf("") }
    var alphabetical by rememberSaveable { mutableStateOf(false) }
    var profileMenu by remember { mutableStateOf(false) }
    var nickname by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isLogin by rememberSaveable { mutableStateOf(false) }
    var accountBusy by rememberSaveable { mutableStateOf(false) }
    var accountError by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedQuizId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedLectureId by rememberSaveable { mutableStateOf<String?>(null) }
    var lectureFile by rememberSaveable { mutableStateOf<Uri?>(null) }
    var lectureFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var lectureError by rememberSaveable { mutableStateOf<String?>(null) }
    var lectureBusy by rememberSaveable { mutableStateOf(false) }
    var lectureJob by remember { mutableStateOf<Job?>(null) }
    var createLectureQuiz by rememberSaveable { mutableStateOf(true) }
    var createLectureCards by rememberSaveable { mutableStateOf(true) }
    var createLectureStages by rememberSaveable { mutableStateOf(true) }
    var lectureCount by rememberSaveable { mutableIntStateOf(10) }
    var cardId by rememberSaveable { mutableStateOf<String?>(null) }
    var cardFront by rememberSaveable { mutableStateOf("") }
    var cardBack by rememberSaveable { mutableStateOf("") }
    var cardError by rememberSaveable { mutableStateOf<String?>(null) }
    var cardReturn by rememberSaveable { mutableStateOf(Page.CARDS) }
    var lectureQuestionIndex by rememberSaveable { mutableIntStateOf(0) }
    var lectureAnswers by rememberSaveable { mutableStateOf(arrayListOf<Int?>()) }
    var lectureHint by rememberSaveable { mutableStateOf(false) }
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

    val activeLecture = study.lectures.firstOrNull { it.id == selectedLectureId }
    val lectureQuiz = activeLecture?.quizId?.takeIf(String::isNotBlank)?.let { id -> quizzes.firstOrNull { it.id == id } }
    var reviewedLectureCards by remember(selectedLectureId) { mutableStateOf(emptySet<String>()) }
    fun saveProgress(course: LectureCourse, stage: Int, progress: Int) {
        val changed = course.copy(stageProgress = (course.stageProgress + listOf(0, 0, 0, 0)).take(4).toMutableList().also { it[stage] = progress.coerceIn(0, 100) })
        update(study.copy(lectures = study.lectures.map { if (it.id == course.id) changed else it }))
    }
    fun openLectureStage(index: Int) {
        val course = activeLecture ?: return
        when (index) {
            0 -> page = Page.LECTURE_READING
            1 -> { reviewedLectureCards = emptySet(); page = Page.LECTURE_CARDS }
            2 -> if (lectureQuiz != null) { lectureQuestionIndex = 0; lectureAnswers = ArrayList(List(lectureQuiz.questions.size) { null }); page = Page.LECTURE_TEST }
            3 -> if (lectureQuiz != null) { lectureQuestionIndex = 0; lectureAnswers = ArrayList(List(lectureQuiz.questions.size) { null }); page = Page.LECTURE_CHECK }
        }
    }
    fun openNextLectureStage() {
        val course = activeLecture ?: return
        val stageAvailable = listOf(true, course.cardIds.isNotEmpty(), lectureQuiz != null, lectureQuiz != null)
        val next = stageAvailable.indices.firstOrNull { stageAvailable[it] && course.stageProgress.getOrElse(it) { 0 } < 100 }
        if (next == null) page = Page.LECTURE_STAGES else openLectureStage(next)
    }
    fun submitLectureQuestion(skip: Boolean = false) {
        val quiz = lectureQuiz ?: return
        val idx = lectureQuestionIndex
        val changedAnswers = ArrayList(lectureAnswers).also { if (skip) it[idx] = null }
        lectureAnswers = changedAnswers
        lectureHint = false
        if (idx < quiz.questions.lastIndex) lectureQuestionIndex++ else {
            val stage = if (page == Page.LECTURE_TEST) 2 else 3
            val score = QuizScorer.score(quiz, changedAnswers)
            saveProgress(requireNotNull(activeLecture), stage, if (stage == 3 && score.gradedTotal > 0 && score.correct * 100 / score.gradedTotal < 80) 0 else 100)
            page = Page.LECTURE_STAGES
        }
    }
    fun openCardForm(id: String?) {
        val card = study.cards.firstOrNull { it.id == id }
        cardId = card?.id; cardFront = card?.front.orEmpty(); cardBack = card?.back.orEmpty(); cardError = null
        cardReturn = if (page == Page.LECTURE_CARDS) Page.LECTURE_CARDS else Page.CARDS
        page = Page.CARD_FORM
    }
    fun rateCard(id: String, rating: String) {
        val card = study.cards.firstOrNull { it.id == id } ?: return
        val interval = when (rating) { "hard" -> 1; "medium" -> (card.intervalDays.coerceAtLeast(1) * 2).coerceAtMost(365); else -> (card.intervalDays.coerceAtLeast(1) * 3).coerceAtMost(365) }
        var changed = study.copy(cards = study.cards.map { if (it.id == id) it.copy(reviewCount = it.reviewCount + 1, intervalDays = interval, dueAt = System.currentTimeMillis() + interval * 86_400_000L) else it })
        if (page == Page.LECTURE_CARDS) activeLecture?.let { course ->
            reviewedLectureCards = reviewedLectureCards + id
            val progress = maxOf(course.stageProgress.getOrElse(1) { 0 }, reviewedLectureCards.size * 100 / course.cardIds.size.coerceAtLeast(1))
            val values = (course.stageProgress + listOf(0, 0, 0, 0)).take(4).toMutableList().also { it[1] = progress }
            changed = changed.copy(lectures = changed.lectures.map { if (it.id == course.id) it.copy(stageProgress = values) else it })
        }
        update(changed)
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
            openDraft(QuizDraft(it.quiz, document = it.document, warnings = it.warnings), Page.LIBRARY)
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
    val lectureChooser = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        lectureFile = uri
        lectureFileName = uri?.let { runCatching { repository.describe(it).name }.getOrNull() }
    }

    fun goBack() {
        when (page) {
            Page.HOME -> Unit
            Page.CREATE -> { editor.cancel(); page = Page.HOME }
            Page.AI -> page = Page.LIBRARY
            Page.CARDS, Page.REVIEW, Page.RESULTS -> page = Page.LIBRARY
            Page.REGISTER -> Unit
            Page.LEARNING_NEW -> { lectureJob?.cancel(); lectureJob = null; lectureBusy = false; page = Page.PREPARATION }
            Page.LECTURE_STAGES -> page = Page.PREPARATION
            Page.LECTURE_READING, Page.LECTURE_CARDS, Page.LECTURE_TEST, Page.LECTURE_CHECK -> page = Page.LECTURE_STAGES
            Page.CARD_FORM -> page = cardReturn
            Page.EXAMS -> page = Page.PREPARATION
            Page.SETTINGS -> page = settingsReturn
            Page.QUESTION -> page = Page.EDITOR
            Page.EDITOR -> if (editor.draft?.isDirty == true) confirmation = Confirmation.DISCARD_DRAFT else closeDraft()
            Page.QUIZ -> confirmation = Confirmation.LEAVE_QUIZ
            else -> page = Page.HOME
        }
    }

    BackHandler(page != Page.HOME && page != Page.QUESTION && page != Page.REGISTER) { goBack() }

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

    val tabs = listOf(Page.LIBRARY, Page.PREPARATION)
    val mainPage = page in tabs
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (page !in setOf(Page.REGISTER, Page.EDITOR, Page.QUESTION, Page.QUIZ, Page.CARDS, Page.CARD_FORM,
                    Page.LEARNING_NEW, Page.LECTURE_STAGES, Page.LECTURE_READING, Page.LECTURE_CARDS, Page.LECTURE_TEST, Page.LECTURE_CHECK)) {
                Surface(color = SteamNavy, contentColor = Color.White) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().height(48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = SteamAccent, shape = MaterialTheme.shapes.small) { Text("✓", Modifier.padding(horizontal = 5.dp, vertical = 1.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
                        Text("TESTHELPER", Modifier.weight(1f).padding(start = 8.dp), fontSize = 14.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 2.sp)
                        Box {
                            IconButton(onClick = { profileMenu = true }) { Text("♙", color = Color.White) }
                            DropdownMenu(profileMenu, { profileMenu = false }) {
                                DropdownMenuItem(text = { Text("Настройки") }, onClick = { profileMenu = false; settingsReturn = page; page = Page.SETTINGS })
                                DropdownMenuItem(text = { Text("Экзамены и история") }, onClick = { profileMenu = false; page = Page.EXAMS })
                                DropdownMenuItem(text = { Text("Создать с ИИ") }, onClick = { profileMenu = false; page = Page.AI })
                                DropdownMenuItem(text = { Text("Профиль") }, onClick = { profileMenu = false; isLogin = true; accountError = null; page = Page.REGISTER })
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (mainPage || page in setOf(Page.LEARNING_NEW, Page.LECTURE_STAGES)) Surface(color = Color.White) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding()) { tabs.forEach { tab ->
                    val selected = if (tab == Page.LIBRARY) page !in setOf(Page.PREPARATION, Page.LEARNING_NEW, Page.LECTURE_STAGES) else page in setOf(Page.PREPARATION, Page.LEARNING_NEW, Page.LECTURE_STAGES)
                    Column(Modifier.weight(1f).clickable { page = tab }, horizontalAlignment = Alignment.CenterHorizontally) {
                        if (selected) Surface(Modifier.fillMaxWidth().height(2.dp), color = SteamAccent) {}
                        Text(if (tab == Page.LIBRARY) "⊞" else "♧", color = SteamBlue)
                        Text(if (tab == Page.LIBRARY) "Создать" else "Обучение", style = MaterialTheme.typography.labelSmall, color = SteamBlue)
                    }
                } }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentAlignment = Alignment.TopCenter) {
            val steamScreen = page in setOf(Page.REGISTER, Page.LIBRARY, Page.PREPARATION, Page.CARDS, Page.CARD_FORM,
                Page.LEARNING_NEW, Page.LECTURE_STAGES, Page.LECTURE_READING, Page.LECTURE_CARDS, Page.LECTURE_TEST, Page.LECTURE_CHECK)
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().fillMaxHeight().then(if (steamScreen) Modifier else Modifier.padding(horizontal = 14.dp, vertical = 8.dp))) {
                storageError?.let { StudyError(it); Spacer(Modifier.height(6.dp)) }
                when (page) {
                    Page.REGISTER -> RegistrationScreen(nickname, email, password,
                        { nickname = it }, { email = it }, { password = it }, isLogin = isLogin, busy = accountBusy, error = accountError,
                        onToggleLogin = { isLogin = !isLogin; accountError = null }, onSubmit = {
                            if (!accountBusy) {
                                accountBusy = true
                                runCatching { if (isLogin) accountStore.login(email, password) else accountStore.register(nickname, email, password) }
                                    .onSuccess { accountError = null; password = ""; page = Page.LIBRARY }
                                    .onFailure { accountError = it.message ?: "Не удалось сохранить профиль" }
                                accountBusy = false
                            }
                        })
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
                    Page.LIBRARY -> CreateLibraryScreen(
                        quizzes = quizzes.map { quiz -> QuizRow(quiz.id, quiz.title, quiz.questions.size, study.cards.count { it.quizId == quiz.id }, repository.modifiedAt(quiz) ?: 0L, !repository.canDelete(quiz)) },
                        query = query, onQueryChange = { query = it }, onCreate = { openDraft(QuizDraft(emptyQuiz()), Page.LIBRARY) },
                        onImport = { chooser.launch(arrayOf("text/plain", "application/json", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/octet-stream")) },
                        onOpen = { id -> quizzes.firstOrNull { it.id == id }?.let(::openQuiz) },
                        onEdit = { id -> quizzes.firstOrNull { it.id == id }?.let { openDraft(QuizDraft(it, original = it), Page.LIBRARY) } },
                        onDelete = { id -> quizzes.firstOrNull { it.id == id }?.let { deleteId = it.id } },
                        onCards = { selectedQuizId = null; page = Page.CARDS }, alphabetical = alphabetical,
                        onSort = { alphabetical = !alphabetical }, error = libraryError)
                    Page.PREPARATION -> LearningHomeScreen(
                        study.lectures.map { course -> LectureRow(course.id, course.title,
                            run { val enabled = listOf(true, course.cardIds.isNotEmpty(), course.quizId.isNotBlank(), course.quizId.isNotBlank()); enabled.indices.filter { enabled[it] }.let { ids -> ids.sumOf { course.stageProgress.getOrElse(it) { 0 } } / ids.size.coerceAtLeast(1) } },
                            course.stageProgress.indexOfFirst { it < 100 }.coerceAtLeast(0)) },
                        onNew = { lectureError = null; lectureFile = null; lectureFileName = null; page = Page.LEARNING_NEW },
                        onOpen = { selectedLectureId = it; page = Page.LECTURE_STAGES })
                    Page.EXAMS -> PreparationScreen(study, quizzes) { update(study.copy(exams = it)) }
                    Page.CARDS, Page.LECTURE_CARDS -> {
                        val cards = if (page == Page.LECTURE_CARDS) study.cards.filter { it.id in (activeLecture?.cardIds ?: emptyList()) }
                            else selectedQuizId?.let { id -> study.cards.filter { it.quizId == id } }?.takeIf { it.isNotEmpty() } ?: study.cards
                        FlashcardsScreen(cards.map { CardRow(it.id, it.front, it.back) }, onBack = ::goBack,
                            onAdd = { openCardForm(null) }, onEdit = ::openCardForm,
                        onDelete = { id -> update(study.copy(cards = study.cards.filterNot { it.id == id }, lectures = study.lectures.map { course -> course.copy(cardIds = course.cardIds.filterNot { it == id }) })) }, onRate = ::rateCard)
                    }
                    Page.CARD_FORM -> CardEditorScreen(cardFront, cardBack, cardError,
                        onFront = { cardFront = it.take(16_000) }, onBackText = { cardBack = it.take(16_000) }, onCancel = ::goBack,
                        onSave = {
                            val old = study.cards.firstOrNull { it.id == cardId }
                            val linked = old?.quizId ?: activeLecture?.quizId?.takeIf(String::isNotBlank) ?: selectedQuizId
                            val card = Flashcard(cardId ?: UUID.randomUUID().toString(), cardFront.trim(), cardBack.trim(), old?.generatedByAi ?: false,
                                old?.reviewCount ?: 0, old?.intervalDays ?: 0, old?.dueAt ?: 0L, linked)
                            val cards = if (old == null) study.cards + card else study.cards.map { if (it.id == old.id) card else it }
                            val courses = if (old == null && cardReturn == Page.LECTURE_CARDS && activeLecture != null) study.lectures.map { if (it.id == activeLecture.id) it.copy(cardIds = it.cardIds + card.id) else it } else study.lectures
                            if (update(study.copy(cards = cards, lectures = courses))) page = cardReturn else cardError = "Не удалось сохранить карточку"
                        })
                    Page.LEARNING_NEW -> NewLectureScreen(fileName = lectureFileName,
                        fileSize = lectureFile?.let { runCatching { repository.describe(it).bytes?.let { bytes -> "$bytes байт" } }.getOrNull() },
                        makeQuiz = createLectureQuiz, makeCards = createLectureCards, makeStages = createLectureStages, count = lectureCount,
                        busy = lectureBusy, error = lectureError,
                        onChoose = { lectureChooser.launch(arrayOf("text/plain", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/octet-stream")) },
                        onQuiz = { createLectureQuiz = it }, onCards = { createLectureCards = it }, onStages = { createLectureStages = it }, onCount = { lectureCount = it.coerceIn(1, 25) },
                        onBack = ::goBack, onCreate = {
                            val uri = lectureFile
                            if (!lectureBusy && uri != null) {
                                lectureBusy = true; lectureError = null
                                lectureJob = scope.launch {
                                    try {
                                        val material = withContext(Dispatchers.IO) { repository.readStudyMaterial(uri) }
                                        val title = lectureFileName?.substringBeforeLast('.')?.ifBlank { "Новая лекция" } ?: "Новая лекция"
                                        val client = AiClient(study.settings)
                                        val generatedQuiz = if (createLectureQuiz) client.quiz(title, material, "medium", lectureCount) else null
                                        val generatedCards = if (createLectureCards) client.cards(title, material, "medium", lectureCount) else emptyList()
                                        val savedQuiz = generatedQuiz?.let { withContext(Dispatchers.IO) { repository.addQuiz(it) } }
                                        val cards = generatedCards.map { it.copy(quizId = savedQuiz?.id) }
                                        val course = if (createLectureStages) LectureCourse(UUID.randomUUID().toString(), title, material,
                                            savedQuiz?.id.orEmpty(), cards.map { it.id }) else null
                                        check(update(study.copy(cards = study.cards + cards, lectures = course?.let { study.lectures + it } ?: study.lectures))) { "Не удалось сохранить результат" }
                                        quizzes = repository.listQuizzes(); lectureFile = null; lectureFileName = null
                                        if (course == null) page = Page.LIBRARY else { selectedLectureId = course.id; page = Page.LECTURE_STAGES }
                                    } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                                    catch (failure: Exception) { lectureError = failure.message ?: "Не удалось создать курс" }
                                    finally { lectureBusy = false; lectureJob = null }
                                }
                            }
                        })
                    Page.LECTURE_STAGES -> activeLecture?.let { course ->
                        val quiz = quizzes.firstOrNull { it.id == course.quizId }
                        LectureStagesScreen(course.title, listOf(
                            StageRow("Чтение", "Изучи исходный материал", course.stageProgress.getOrElse(0) { 0 }),
                            StageRow("Карточки", "${course.cardIds.size} карточек", course.stageProgress.getOrElse(1) { 0 }, course.cardIds.isEmpty() || course.stageProgress.getOrElse(0) { 0 } < 100),
                            StageRow("Тест", "${quiz?.questions?.size ?: 0} вопросов", course.stageProgress.getOrElse(2) { 0 }, quiz == null || course.stageProgress.getOrElse(0) { 0 } < 100 || (course.cardIds.isNotEmpty() && course.stageProgress.getOrElse(1) { 0 } < 100)),
                            StageRow("Проверка усвоения", "Закрепи материал", course.stageProgress.getOrElse(3) { 0 }, quiz == null || course.stageProgress.getOrElse(2) { 0 } < 100)
                        ), onOpen = ::openLectureStage, onBack = ::goBack)
                    }
                    Page.LECTURE_READING -> activeLecture?.let { course -> LectureReadingScreen(course.title, course.material, ::goBack) {
                        saveProgress(course, 0, 100); openLectureStage(if (course.cardIds.isNotEmpty()) 1 else 2)
                    } }
                    Page.LECTURE_TEST, Page.LECTURE_CHECK -> {
                        val quiz = lectureQuiz; val question = quiz?.questions?.getOrNull(lectureQuestionIndex)
                        if (quiz != null && question != null) LectureQuestionScreen(activeLecture?.title.orEmpty(), lectureQuestionIndex + 1,
                            quiz.questions.size, question.text, question.options, lectureAnswers.getOrNull(lectureQuestionIndex),
                            question.explanation.takeIf { lectureHint },
                            onSelect = { selected -> lectureAnswers = ArrayList(lectureAnswers).also { it[lectureQuestionIndex] = selected } },
                            onHint = { lectureHint = !lectureHint }, onSubmit = { submitLectureQuestion() }, onSkip = { submitLectureQuestion(true) }, onBack = ::goBack)
                    }
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
