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
import kotlinx.coroutines.ensureActive
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
                if(account==null)SoftStudyTheme("light") { AuthEntryScreen() }
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
    PREPARATION("Подготовка", Glyph.PLAN), AI("Создать с ИИ"), CARDS("Карточки", Glyph.CARDS), SETTINGS("Настройки"),
    ACCOUNT("Профиль", Glyph.PROFILE), WORKSPACE("Учебный набор"), SOURCE("Источник"), EDITOR("Редактор"), QUESTION("Вопрос"), REVIEW("Тест"), QUIZ("Прохождение"), RESULTS("Результат")
}

private enum class Confirmation { DISCARD_DRAFT, LEAVE_QUIZ, FINISH_QUIZ }

@Composable
private fun TestHelperApp(repository: QuizRepository, editor: EditorModel, study: StudyData,
                          activeStore: ActiveAttemptStore, bootCount: Int, update: (StudyData) -> Boolean, storageError: String?, initialQuizzes: List<Quiz>, restoredAttempt: Result<ActiveAttempt?>, onExportData: () -> Unit) {
    var quizzes by remember { mutableStateOf(initialQuizzes) }
    val restoreResult = remember { restoredAttempt }
    val restored = restoreResult.getOrNull()?.takeUnless {snapshot->study.attempts.any {it.id==snapshot.attemptId}}
    val screenStates = rememberSaveableStateHolder()
    var sessionQuiz by remember { mutableStateOf(restored?.quiz) }
    var wallDeadline by rememberSaveable { mutableLongStateOf(restored?.wallDeadline ?: 0L) }
    var page by rememberSaveable { mutableStateOf(if (restored != null) Page.QUIZ else Page.HOME) }
    var activeSetId by rememberSaveable {mutableStateOf<String?>(null)}
    var quizSetReturn by rememberSaveable {mutableStateOf(false)}
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
    var sessionExam by rememberSaveable {mutableStateOf(restored?.examMode ?: study.settings.examMode)}
    var sessionHints by rememberSaveable {mutableStateOf(restored?.hints ?: study.settings.showHints)}
    var sessionReveal by rememberSaveable {mutableStateOf(restored?.revealAnswers ?: study.settings.showAnswers)}
    var startedAt by rememberSaveable {mutableLongStateOf(restored?.startedAt ?: 0)}
    var startedElapsed by rememberSaveable {mutableLongStateOf(restored?.startedElapsed ?: 0)}
    var startedBoot by rememberSaveable {mutableIntStateOf(restored?.startedBootCount ?: bootCount)}
    var marked by rememberSaveable {mutableStateOf(restored?.bookmarks?.toList().orEmpty())}
    var completedDuration by rememberSaveable {mutableStateOf<Long?>(null)}
    val bankPrepared = remember(currentQuiz, study.settings, seed) { currentQuiz?.let { StudyLogic.prepare(it, study.settings, seed) } }
    val prepared = sessionQuiz ?: bankPrepared

    var draftSaving by remember {mutableStateOf(false)}
    var draftError by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryError by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var aiTask by rememberSaveable { mutableStateOf("quizzes") }
    var aiAttemptId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCardCreation by rememberSaveable { mutableStateOf(false) }
    var confirmation by rememberSaveable { mutableStateOf<Confirmation?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val exportSet=rememberStudySetExport(repository,study)

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
        quizSetReturn=false
        sessionQuiz = null
        reviewOverride = quiz.takeIf { candidate -> quizzes.none { it == candidate } }
        quizId = quiz.id
        timerMinutes = study.settings.minutes
        page = Page.REVIEW
    }

    fun start(quiz: Quiz,options: StudySettings=study.settings) {
        questionIndex = 0
        seed = System.nanoTime()
        sessionQuiz = StudyLogic.prepare(quiz, options, seed)
        sessionExam=options.examMode;sessionHints=options.showHints;sessionReveal=options.showAnswers
        startedAt=System.currentTimeMillis();startedElapsed=SystemClock.elapsedRealtime();startedBoot=bootCount;marked=emptyList();completedDuration=null
        timerMinutes=options.minutes
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
        val duration=StudySessionPolicy.duration(startedAt,startedElapsed,startedBoot,bootCount,System.currentTimeMillis(),SystemClock.elapsedRealtime(),wallDeadline,deadline)
        val attempt = StudyLogic.completeSelections(attemptId, quiz, selections, System.currentTimeMillis()).copy(durationMillis=duration,examMode=sessionExam)
        if (study.attempts.none { it.id == attempt.id } && !update(study.copy(attempts = study.attempts + attempt))) {
            libraryError = "Не удалось сохранить результат. Повторите завершение"
            return
        }
        completedDuration=duration
        runCatching { activeStore.clear() }
        expired = QuizTimer.expired(deadline, SystemClock.elapsedRealtime())
        confirmation = null
        page = Page.RESULTS
    }

    LaunchedEffect(page, prepared, answers, selections, questionIndex, deadline,marked) {
        if (page == Page.QUIZ && prepared != null && selections.size == prepared.questions.size && answers.size == prepared.questions.size) {
            val snapshot=ActiveAttempt(prepared,answers,questionIndex,attemptId,deadline,wallDeadline,bootCount,selections,sessionExam,sessionHints,sessionReveal,startedAt,startedElapsed,startedBoot,marked.toSet())
            val ticket=activeStore.ticket()
            val savingWorker=kotlin.coroutines.coroutineContext
            runCatching {kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){activeStore.save(snapshot,ticket){savingWorker.ensureActive()}}}
                .onFailure { if(it is kotlinx.coroutines.CancellationException) throw it; libraryError = "Не удалось сохранить текущие ответы" }
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
            openDraft(QuizDraft(it.quiz, document = it.document, warnings = it.warnings), if(activeSetId!=null)Page.WORKSPACE else Page.SOURCE)
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
            Page.AI -> page = if(activeSetId!=null)Page.WORKSPACE else Page.SOURCE
            Page.SOURCE -> {editor.cancel();page=if(activeSetId!=null)Page.WORKSPACE else Page.HOME}
            Page.WORKSPACE -> {activeSetId=null;quizSetReturn=false;page=Page.HOME}
            Page.ACCOUNT -> page = accountReturn
            Page.CARDS, Page.REVIEW, Page.RESULTS -> page = if(activeSetId!=null && quizSetReturn)Page.WORKSPACE else Page.LIBRARY
            Page.SETTINGS -> page = settingsReturn
            Page.QUESTION -> page = Page.EDITOR
            Page.EDITOR -> if (editor.draft?.isDirty == true) confirmation = Confirmation.DISCARD_DRAFT else closeDraft()
            Page.QUIZ -> confirmation = Confirmation.LEAVE_QUIZ
            else -> page = Page.HOME
        }
    }

    BackHandler(page != Page.HOME && page != Page.QUESTION) { goBack() }
    BackHandler(draftSaving) {}

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

    val tabs = listOf(Page.HOME, Page.LIBRARY, Page.CARDS, Page.ACCOUNT)
    val mainPage = page in tabs
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (page != Page.QUESTION && page != Page.EDITOR && page != Page.QUIZ && page != Page.CARDS && page != Page.WORKSPACE) {
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
                        editor.cancel();activeSetId=null;quizSetReturn=false
                        if(tab==Page.ACCOUNT)accountReturn=Page.HOME
                        page = tab
                    }, icon = { StudyIcon(requireNotNull(tab.glyph)) }, label = { Text(if(tab==Page.LIBRARY)"Тесты" else tab.title, maxLines = 2) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer))
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().fillMaxHeight().padding(horizontal = 14.dp, vertical = 8.dp)) {
                storageError?.let { StudyError(it); Spacer(Modifier.height(6.dp)) }
                if (LocalAuth.current.account?.verified == false && page != Page.ACCOUNT) TextButton(onClick={accountReturn=page;page=Page.ACCOUNT}) { Text("Подтвердите почту для доступа к ИИ") }
                if (restoreResult.isFailure) StudyError("Не удалось восстановить текущую попытку. Сохранённые тесты доступны")
                screenStates.SaveableStateProvider(page.name) {
                when (page) {
                    Page.HOME -> StudySetHome(repository,quizzes.filter(repository::canDelete),study,
                        onOpen={activeSetId=it;page=Page.WORKSPACE},onCreate={activeSetId=null;page=Page.CREATE},
                        onProfile={accountReturn=Page.HOME;page=Page.ACCOUNT},onExport=exportSet,onLegacy={activeSetId=null;page=Page.LIBRARY},onPreparation={activeSetId=null;page=Page.PREPARATION})
                    Page.CREATE -> NewStudySetScreen(repository,{activeSetId=it;page=Page.WORKSPACE},::goBack)
                    Page.WORKSPACE -> activeSetId?.let {setId -> key(setId) {StudySetWorkspace(setId,repository,quizzes,study,update,::goBack,
                        onQuiz={openQuiz(it);quizSetReturn=true},onEdit={bank->openDraft(QuizDraft(bank ?: emptyQuiz(),original=bank),Page.WORKSPACE)},
                        onAi={aiTask=it;aiAttemptId=study.attempts.lastOrNull {a->a.mistakes.isNotEmpty()}?.id;page=Page.AI},
                        onCardsFromQuiz={openQuiz(it);quizSetReturn=true;page=Page.WORKSPACE;showCardCreation=true},
                        onImportQuiz={openDraft(QuizDraft(it),Page.WORKSPACE)},onOptions={settingsReturn=Page.WORKSPACE;page=Page.SETTINGS},onExport=exportSet,
                        onOwnText={creationSource=CreationSource.TEXT;page=Page.SOURCE})}}

                    Page.SOURCE -> {
                        editor.load?.let { StudyLoading("Обработка документа", it.fraction, editor::cancel) }
                        editor.failure?.let { StudyError(it); ImportEncodingControls(editor, repository) }
                        CreationFlow(creationSource,
                            onImport = { chooser.launch(arrayOf("text/plain", "application/json", "application/pdf", "image/jpeg", "image/png", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) },
                            onAi = { aiTask = "quizzes"; page = Page.AI }, onManual = { openDraft(QuizDraft(emptyQuiz()), Page.SOURCE) },
                            onDraft = { openDraft(QuizDraft(it), Page.SOURCE) }, onAccount = { accountReturn = page; page = Page.ACCOUNT })
                    }
                    Page.LIBRARY -> LibraryScreen(quizzes.filter(repository::canDelete), repository::modifiedAt, libraryError, ::openQuiz,
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
                        saveCards = { cards ->val ok=update(study.copy(cards=study.cards+cards));if(ok && activeSetId!=null)runCatching {repository.sets.linkCards(activeSetId!!,cards.map {it.id})}.onFailure {libraryError="Карточки сохранены, но не прикреплены к набору"};ok }, onBack = ::goBack, onAccount = { accountReturn = page; page = Page.ACCOUNT }, initialTask = aiTask,studySetId=activeSetId)
                    Page.EDITOR -> editor.draft?.let { draft ->
                        if(draftSaving)StudyLoading("Сохранение теста") else DraftScreen(draft,
                            { editor.draft = draft.copy(quiz = it); draftError = null },
                            { index ->
                                editor.fields = QuestionFields(draft.quiz.questions.getOrNull(index) ?: emptyQuestion())
                                page = Page.QUESTION
                            }, ::goBack, {
                                if(!draftSaving) {
                                    draftSaving=true;draftError=null
                                    val ownerSet=activeSetId
                                    scope.launch {
                                        try {
                                            val saved=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                val trimmed=draft.quiz.copy(title=draft.quiz.title.trim());EditorRules.validate(trimmed.title,trimmed.questions)
                                                if(draft.original==null)repository.addQuiz(trimmed) else repository.saveQuiz(trimmed)
                                            }
                                            var linkError=false
                                            if(ownerSet!=null)try {kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){repository.sets.linkQuiz(ownerSet,saved.id)}}catch(e: kotlinx.coroutines.CancellationException){throw e}catch(_: Exception){linkError=true}
                                            quizzes=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){repository.listQuizzes()}
                                            editor.draft=null;openQuiz(saved);quizSetReturn=ownerSet!=null;draftSaving=false
                                            scope.launch {snackbar.showSnackbar(if(linkError)"Тест сохранён в библиотеке; связь с набором не записана" else "Тест сохранён")}
                                        }catch(e: kotlinx.coroutines.CancellationException){throw e}catch(e: Exception){draftError=e.message ?: "Не удалось сохранить тест"}finally{draftSaving=false}
                                    }
                                }
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
                        StartScreen(quiz,study.settings,
                            { openDraft(QuizDraft(quiz, original = quiz), Page.REVIEW) },{options->val saved=update(study.copy(settings=options));if(saved)start(quiz,options);saved}, { showCardCreation = true },{page=if(activeSetId!=null)Page.WORKSPACE else Page.LIBRARY})
                    }
                    Page.QUIZ -> prepared?.takeIf { questionIndex in it.questions.indices && answers.size == it.questions.size }?.let {
                        RunScreen(it, questionIndex, selections.getOrNull(questionIndex).orEmpty(), seconds.takeIf { deadline != 0L },
                            sessionHints, { option ->
                                if (QuizTimer.expired(deadline, SystemClock.elapsedRealtime())) finish()
                                else {
                                    val old=selections.getOrNull(questionIndex).orEmpty()
                                    val next=if(it.questions[questionIndex].isMultiAnswer) { if(option in old)old-option else old+option } else setOf(option)
                                    selections=selections.toMutableList().also { list -> list[questionIndex]=next }
                                    answers=answers.toMutableList().also { list -> list[questionIndex]=next.minOrNull() }
                                }
                            }, { if (questionIndex > 0) questionIndex-- }, {
                                if (QuizTimer.expired(deadline, SystemClock.elapsedRealtime())) finish() else questionIndex++
                            }, { confirmation = Confirmation.FINISH_QUIZ },examMode=sessionExam,bookmarked=it.questions[questionIndex].id in marked,onBookmark={marked=StudySessionPolicy.bookmark(marked.toSet(),it.questions[questionIndex].id).toList()},markedCount=marked.size)
                    }
                    Page.RESULTS -> prepared?.takeIf { answers.size == it.questions.size }?.let {
                        ResultsScreen(it, selections, expired, sessionReveal, { currentQuiz?.let {bank->start(bank)} }, ::goBack,
                            repeatMistakes={StudyResultRules.mistakes(it,selections)?.let {bank->start(bank)}},durationMillis=completedDuration ?: study.attempts.firstOrNull {a->a.id==attemptId}?.durationMillis,
                            onCardsFromMistakes={val cards=StudySessionPolicy.mistakeCards(it,selections,study.cards);if(update(study.copy(cards=study.cards+cards))){if(activeSetId!=null)runCatching {repository.sets.linkCards(activeSetId!!,cards.map {c->c.id})}.onFailure {libraryError="Карточки сохранены, но не связаны с набором"};scope.launch {snackbar.showSnackbar(if(cards.isEmpty())"Все карточки этих ошибок уже есть" else "Добавлено карточек: "+cards.size)}}})
                    }
                }
                }
            }
        }
    }
}
