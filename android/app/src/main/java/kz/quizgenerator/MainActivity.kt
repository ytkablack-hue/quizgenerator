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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
        val auth=AuthSessions.get(applicationContext)
        setContent {
            CompositionLocalProvider(LocalAuth provides auth) {
                val account=auth.account
                if(account==null)SoftStudyTheme("system") { AuthEntryScreen() }
                else key(account.uid) {
                    val editor=remember(account.uid) { ViewModelProvider(this@MainActivity).get("editor:"+account.uid,EditorModel::class.java) }
                    DisposableEffect(editor) { onDispose { editor.cancel() } }
                    AccountWorkspace(this@MainActivity,auth,account,editor)
                }
            }
        }
    }
}

private data class LibraryBoot(val study: Result<StudyData>,val quizzes: List<Quiz>,val active: Result<ActiveAttempt?>)

@Composable
private fun AccountWorkspace(activity: ComponentActivity,auth: AuthClient,account: Account,editor: EditorModel) {
    val storage=remember { AccountStorage(activity.filesDir) }
    var access by remember { mutableStateOf<AccountStorage.Access?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var claiming by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    LaunchedEffect(account.uid,retry) {
        error=null
        try { access=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { storage.access(account.uid) } }
        catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(_: Exception) { error="Не удалось открыть библиотеку. Исходные файлы не изменены" }
    }
    when(val current=access) {
        AccountStorage.Access.NeedsLegacyOwnership -> SoftStudyTheme("system") {
            StudyColumn("Локальная библиотека",{}) {
                Text("На устройстве есть тесты из прежней версии. Подтвердите, что это ваша библиотека: она будет закреплена за текущим аккаунтом без перемещения и удаления файлов. Другие аккаунты получат отдельную библиотеку.")
                Text(account.email)
                Button(enabled=!claiming,onClick={claiming=true;scope.launch {
                    try { access=AccountStorage.Access.Ready(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { storage.claimLegacy(account.uid) }) }
                    catch(e: kotlinx.coroutines.CancellationException) { throw e }
                    catch(e: Exception) { error=e.message ?: "Файлы не изменены" }
                    finally { claiming=false }
                }}) { Text("Это мои тесты — продолжить") }
                TextButton(onClick={scope.launch { auth.signOut() }}) { Text("Выйти и выбрать другой аккаунт") }
                error?.let { StudyError(it) }
            }
        }
        is AccountStorage.Access.Ready -> key(current.root.absolutePath) {
            val repository=remember { QuizRepository(activity.applicationContext,current.root) }
            val store=remember { StudyStore(File(current.root,"study.json")) }
            val activeStore=remember { ActiveAttemptStore(File(current.root,"active-attempt.json")) }
            var boot by remember { mutableStateOf<LibraryBoot?>(null) }
            LaunchedEffect(retry) {
                try { boot=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    LibraryBoot(runCatching { store.load() },repository.listQuizzes(),runCatching { activeStore.load() })
                } } catch(e: kotlinx.coroutines.CancellationException) { throw e }
                catch(_: Exception) { error="Не удалось прочитать библиотеку. Файлы не изменены" }
            }
            val export=rememberLibraryExport(current.root)
            val initial=boot
            if(initial!=null) {
                var study by remember { mutableStateOf(initial.study.getOrDefault(StudyData())) }
                var failure by remember { mutableStateOf(if(initial.study.isFailure)"Не удалось прочитать данные подготовки. Файл не перезаписан" else null) }
                val bootCount=android.provider.Settings.Global.getInt(activity.contentResolver,android.provider.Settings.Global.BOOT_COUNT,-1)
                SoftStudyTheme(study.settings.theme) {
                    TestHelperApp(repository,editor,study,activeStore,bootCount,{updated ->
                        runCatching { check(initial.study.isSuccess) { "Файл подготовки не читается" };store.save(updated) }
                            .onSuccess { study=updated;failure=null }.onFailure { failure="Не удалось сохранить данные подготовки" }.isSuccess
                    },failure,initial.quizzes,initial.active,export)
                }
            } else SoftStudyTheme("system") {
                StudyColumn("Библиотека",{}) {
                    if(error==null)StudyLoading("Открываем локальную библиотеку…") else {
                        StudyError(error!!);Button(onClick={error=null;retry++}) { Text("Повторить") }
                        TextButton(onClick={scope.launch { auth.signOut() }}) { Text("Выйти") }
                    }
                }
            }
        }
        null -> SoftStudyTheme("system") {
            StudyColumn("TestHelper",{}) {
                if(error==null)StudyLoading("Открываем аккаунт…") else {
                    StudyError(error!!);Button(onClick={retry++}) { Text("Повторить") }
                    TextButton(onClick={scope.launch { auth.signOut() }}) { Text("Выйти") }
                }
            }
        }
    }
}

private enum class Page(val title: String, val glyph: Glyph? = null) {
    HOME("Главная", Glyph.HOME), CREATE("Создать", Glyph.ADD), LIBRARY("Библиотека", Glyph.LIBRARY),
    PREPARATION("Подготовка", Glyph.PLAN), AI("Создать с ИИ"), CARDS("Карточки"), SETTINGS("Настройки"),
    ACCOUNT("Личный кабинет"), EDITOR("Редактор"), QUESTION("Вопрос"), REVIEW("Тест"), QUIZ("Прохождение"), RESULTS("Результат")
}

private enum class Confirmation { DISCARD_DRAFT, LEAVE_QUIZ, FINISH_QUIZ }

@Composable
private fun TestHelperApp(repository: QuizRepository, editor: EditorModel, study: StudyData,
                          activeStore: ActiveAttemptStore, bootCount: Int, update: (StudyData) -> Boolean, storageError: String?, initialQuizzes: List<Quiz>, restoredAttempt: Result<ActiveAttempt?>, onExportData: () -> Unit) {
    var quizzes by remember { mutableStateOf(initialQuizzes) }
    val restoreResult = remember { restoredAttempt }
    val restored = restoreResult.getOrNull()
    val screenStates = rememberSaveableStateHolder()
    var sessionQuiz by remember { mutableStateOf(restored?.quiz) }
    var wallDeadline by rememberSaveable { mutableLongStateOf(restored?.wallDeadline ?: 0L) }
    var page by rememberSaveable { mutableStateOf(if (restored != null) Page.QUIZ else Page.HOME) }
    var creationSource by rememberSaveable { mutableStateOf(CreationSource.CHOOSE) }
    var editorReturn by rememberSaveable { mutableStateOf(Page.CREATE) }
    var accountReturn by rememberSaveable { mutableStateOf(Page.SETTINGS) }
    var settingsReturn by rememberSaveable { mutableStateOf(Page.HOME) }
    var quizId by rememberSaveable { mutableStateOf<String?>(restored?.quiz?.id) }
    var reviewOverride by remember { mutableStateOf<Quiz?>(null) }
    val currentQuiz = reviewOverride ?: quizzes.firstOrNull { it.id == quizId } ?: sessionQuiz
    var questionIndex by rememberSaveable { mutableIntStateOf(restored?.index ?: 0) }
    LaunchedEffect(Unit) { if (restored != null && editor.activeAnswers.isEmpty()) editor.activeAnswers = restored.answers }
    var answers by editor::activeAnswers
    var selections by editor::activeSelections
    LaunchedEffect(Unit) { if(restored!=null && selections.isEmpty()) selections=restored.selections ?: restored.answers.map { setOfNotNull(it) } }
    var seed by rememberSaveable { mutableLongStateOf(0L) }
    var attemptId by rememberSaveable { mutableStateOf(restored?.attemptId ?: "") }
    var timerMinutes by rememberSaveable { mutableIntStateOf(study.settings.minutes) }
    var deadline by rememberSaveable { mutableLongStateOf(restored?.let {
        if (it.elapsedDeadline == 0L) 0L
        else if (bootCount >= 0 && it.bootCount == bootCount) it.elapsedDeadline
        else SystemClock.elapsedRealtime() + (it.wallDeadline - System.currentTimeMillis()).coerceAtLeast(0)
    } ?: 0L) }
    var seconds by remember(deadline) { mutableIntStateOf(QuizTimer.secondsLeft(deadline, SystemClock.elapsedRealtime())) }
    var expired by rememberSaveable { mutableStateOf(false) }
    val bankPrepared = remember(currentQuiz, study.settings, seed) { currentQuiz?.let { StudyLogic.prepare(it, study.settings, seed) } }
    val prepared = sessionQuiz ?: bankPrepared

    var draftError by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryError by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var aiTask by rememberSaveable { mutableStateOf("quizzes") }
    var aiAttemptId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCardCreation by rememberSaveable { mutableStateOf(false) }
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
        sessionQuiz = null
        reviewOverride = quiz.takeIf { candidate -> quizzes.none { it == candidate } }
        quizId = quiz.id
        timerMinutes = study.settings.minutes
        page = Page.REVIEW
    }

    fun start(quiz: Quiz) {
        questionIndex = 0
        seed = System.nanoTime()
        sessionQuiz = StudyLogic.prepare(quiz, study.settings, seed)
        answers = ArrayList(List(sessionQuiz!!.questions.size) { null })
        selections = List(sessionQuiz!!.questions.size) { emptySet() }
        attemptId = UUID.randomUUID().toString()
        deadline = QuizTimer.deadline(SystemClock.elapsedRealtime(), timerMinutes)
        wallDeadline = QuizTimer.deadline(System.currentTimeMillis(), timerMinutes)
        expired = false
        confirmation = null
        page = Page.QUIZ
    }

    fun finish() {
        if (page != Page.QUIZ) return
        val quiz = prepared ?: return
        if (answers.size != quiz.questions.size || selections.size != quiz.questions.size) return
        val attempt = StudyLogic.completeSelections(attemptId, quiz, selections, System.currentTimeMillis())
        if (study.attempts.none { it.id == attempt.id } && !update(study.copy(attempts = study.attempts + attempt))) {
            libraryError = "Не удалось сохранить результат. Повторите завершение"
            return
        }
        runCatching { activeStore.clear() }
        expired = QuizTimer.expired(deadline, SystemClock.elapsedRealtime())
        confirmation = null
        page = Page.RESULTS
    }

    LaunchedEffect(page, prepared, answers, selections, questionIndex, deadline) {
        if (page == Page.QUIZ && prepared != null && selections.size == prepared.questions.size && answers.size == prepared.questions.size) {
            runCatching { activeStore.save(ActiveAttempt(prepared, answers, questionIndex, attemptId, deadline, wallDeadline, bootCount,selections)) }
                .onFailure { libraryError = "Не удалось сохранить текущие ответы" }
        }
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
        else if (page in setOf(Page.QUIZ, Page.RESULTS) && answers.size != prepared?.questions?.size) page = Page.REVIEW
    }

    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { editor.import(repository, it) }
    }

    fun goBack() {
        when (page) {
            Page.HOME -> Unit
            Page.CREATE -> { editor.cancel(); page = Page.HOME }
            Page.AI -> page = Page.CREATE
            Page.ACCOUNT -> page = accountReturn
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
            runCatching { activeStore.clear() }
            sessionQuiz = null
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

    if (showCardCreation && currentQuiz != null) {
        val quiz = currentQuiz
        val unknown = quiz.questions.count { it.correctIndices.isEmpty() }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCardCreation = false },
            title = { Text("Карточки из теста") },
            text = { Text("Вопросов без ключа: $unknown. Для них выберите ответ в редакторе. Предположительные ключи сохранят свою пометку.") },
            confirmButton = { TextButton(onClick = {
                val created = QuizCards.create(quiz, study.cards)
                if (update(study.copy(cards = study.cards + created))) {
                    showCardCreation = false
                    page = Page.CARDS
                    scope.launch { snackbar.showSnackbar("Добавлено карточек: ${created.size}") }
                }
            }) { Text("Создать по указанным ответам") } },
            dismissButton = { TextButton(onClick = {
                showCardCreation = false
                openDraft(QuizDraft(quiz, original = quiz), Page.REVIEW)
            }) { Text("Указать ответы вручную") } }
        )
    }

    val tabs = listOf(Page.HOME, Page.CREATE, Page.PREPARATION)
    val mainPage = page in tabs
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (page != Page.QUESTION && page != Page.EDITOR && page != Page.QUIZ && page != Page.CARDS) {
                kz.quizgenerator.ui.components.SteamTopBar(
                    if (mainPage) "TestHelper" else page.title,
                    onBack = if (!mainPage && page != Page.CARDS) ::goBack else null,
                    trailing = if (mainPage) ({
                        IconButton(onClick = { settingsReturn = page; page = Page.SETTINGS }) {
                            StudyIcon(Glyph.SETTINGS, description = "Настройки")
                        }
                    }) else null
                )
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
                if (LocalAuth.current.account?.verified == false && page != Page.ACCOUNT) TextButton(onClick={accountReturn=page;page=Page.ACCOUNT}) { Text("Подтвердите почту для доступа к ИИ") }
                if (restoreResult.isFailure) StudyError("Не удалось восстановить текущую попытку. Сохранённые тесты доступны")
                screenStates.SaveableStateProvider(page.name) {
                when (page) {
                    Page.HOME -> HomeScreen { source -> creationSource = source; aiTask = "quizzes"; page = if (source == CreationSource.AI) Page.AI else Page.CREATE }
                    Page.CREATE -> {
                        editor.load?.let { StudyLoading("Обработка документа", it.fraction, editor::cancel) }
                        editor.failure?.let { StudyError(it); ImportEncodingControls(editor, repository) }
                        CreationFlow(creationSource,
                            onImport = { chooser.launch(arrayOf("text/plain", "application/json", "application/pdf", "image/jpeg", "image/png", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) },
                            onAi = { aiTask = "quizzes"; page = Page.AI }, onManual = { openDraft(QuizDraft(emptyQuiz()), Page.CREATE) },
                            onDraft = { openDraft(QuizDraft(it), Page.CREATE) }, onAccount = { accountReturn = page; page = Page.ACCOUNT })
                    }
                    Page.LIBRARY -> LibraryScreen(quizzes, repository::modifiedAt, libraryError, ::openQuiz,
                        { openDraft(QuizDraft(it, original = it), Page.LIBRARY) }, { deleteId = it.id },
                        repository::canDelete, { page = Page.CARDS }, { page = Page.CREATE },
                        cardCount = { quiz -> study.cards.count { it.sourceQuizId == quiz.id } },
                        quizCards = { quiz -> openQuiz(quiz); showCardCreation = true },
                        onImport = { page = Page.CREATE; chooser.launch(arrayOf("*/*")) })
                    Page.PREPARATION -> PreparationHub(study, quizzes, { update(study.copy(exams = it)) },
                        library = { LibraryScreen(quizzes, repository::modifiedAt, libraryError, ::openQuiz,
                            { openDraft(QuizDraft(it, original = it), Page.PREPARATION) }, { deleteId = it.id },
                            repository::canDelete, { page = Page.CARDS }, { creationSource = CreationSource.CHOOSE; page = Page.CREATE },
                            cardCount = { quiz -> study.cards.count { it.sourceQuizId == quiz.id } },
                            quizCards = { quiz -> openQuiz(quiz); showCardCreation = true }, onImport = { chooser.launch(arrayOf("*/*")) }) },
                        onCards = { page = Page.CARDS }, onAi = { aiAttemptId = it?.id; aiTask = if (it == null) "cards" else "review"; page = Page.AI }, onRepeat = ::openQuiz)
                    Page.CARDS -> CoinFlashcardsScreen(study.cards, { update(study.copy(cards = it)) }, { page = Page.LIBRARY })
                    Page.SETTINGS -> StudyPreferences(study.settings, { update(study.copy(settings = it)) }, ::goBack, { accountReturn = page; page = Page.ACCOUNT })
                    Page.ACCOUNT -> AccountScreen(::goBack,quizzes.filter(repository::canDelete),study,{settingsReturn=Page.ACCOUNT;page=Page.SETTINGS},onExportData)
                    Page.AI -> AiScreen(study.settings, study.attempts.firstOrNull { it.id == aiAttemptId } ?: study.attempts.lastOrNull { it.mistakes.isNotEmpty() },
                        repository, saveQuiz = { openDraft(QuizDraft(it), Page.AI) },
                        saveCards = { update(study.copy(cards = study.cards + it)) }, onBack = ::goBack, onAccount = { accountReturn = page; page = Page.ACCOUNT }, initialTask = aiTask)
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
                            }, draftError, { ImportEncodingControls(editor, repository) })
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
                            { openDraft(QuizDraft(quiz, original = quiz), Page.REVIEW) }, { start(quiz) }, { showCardCreation = true })
                    }
                    Page.QUIZ -> prepared?.takeIf { questionIndex in it.questions.indices && answers.size == it.questions.size }?.let {
                        RunScreen(it, questionIndex, selections.getOrNull(questionIndex).orEmpty(), seconds.takeIf { deadline != 0L },
                            study.settings.showHints, { option ->
                                if (QuizTimer.expired(deadline, SystemClock.elapsedRealtime())) finish()
                                else {
                                    val old=selections.getOrNull(questionIndex).orEmpty()
                                    val next=if(it.questions[questionIndex].isMultiAnswer) { if(option in old)old-option else old+option } else setOf(option)
                                    selections=selections.toMutableList().also { list -> list[questionIndex]=next }
                                    answers=answers.toMutableList().also { list -> list[questionIndex]=next.minOrNull() }
                                }
                            }, { if (questionIndex > 0) questionIndex-- }, {
                                if (QuizTimer.expired(deadline, SystemClock.elapsedRealtime())) finish() else questionIndex++
                            }, { confirmation = Confirmation.FINISH_QUIZ })
                    }
                    Page.RESULTS -> prepared?.takeIf { answers.size == it.questions.size }?.let {
                        ResultsScreen(it, selections, expired, study.settings.showAnswers, { currentQuiz?.let(::start) }, ::goBack)
                    }
                }
                }
            }
        }
    }
}
