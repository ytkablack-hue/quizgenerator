package kz.quizgenerator

import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.*
import java.util.UUID

internal data class QuizDraft(val quiz: Quiz, val original: Quiz? = null,
                              val document: DocumentDetails? = null, val warnings: List<String> = emptyList(),
                              val detectedCount: Int? = document?.let { quiz.questions.size }) {
    val isDirty: Boolean get() = if (original != null) original != quiz
        else quiz.title.isNotBlank() || quiz.questions.isNotEmpty() || document != null
}

internal fun emptyQuiz() = Quiz(UUID.randomUUID().toString(), "", emptyList())
internal fun emptyQuestion() = QuizQuestion(UUID.randomUUID().toString(), "", listOf("", ""), null, null)

internal object EditorRules {
    fun move(questions: List<QuizQuestion>, from: Int, to: Int): List<QuizQuestion> {
        require(from in questions.indices && to in questions.indices)
        return questions.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun validate(title: String, questions: List<QuizQuestion>) {
        require(title.isNotBlank()) { "Введите название теста" }
        require(questions.size in 1..QuizLimits.QUESTIONS) { "Добавьте от 1 до 5000 вопросов" }
        questions.forEach(::validateQuestion)
    }

    fun validateQuestion(question: QuizQuestion) {
        require(question.text.isNotBlank()) { "Введите текст вопроса" }
        require(question.options.size in 2..QuizLimits.OPTIONS) { "Добавьте от двух до 64 вариантов ответа" }
        require(question.options.none(String::isBlank)) { "Заполните все варианты ответа" }
        require(question.correctIndices.all { it in question.options.indices }) { "Выберите существующий вариант" }
    }
}

internal class QuestionFields(val initial: QuizQuestion) {
    var text by mutableStateOf(initial.text)
    var options by mutableStateOf<List<String>>(initial.options.toList())
    var correct by mutableStateOf(initial.correctIndex)
    var explanation by mutableStateOf(initial.explanation.orEmpty())
}

internal class EditorModel : ViewModel() {
    var activeAnswers by mutableStateOf<List<Int?>>(emptyList())
    var fields by mutableStateOf<QuestionFields?>(null)
    var draft by mutableStateOf<QuizDraft?>(null)
    var load by mutableStateOf<ImportProgress?>(null)
        private set
    var failure by mutableStateOf<String?>(null)
        private set
    var imported by mutableStateOf<ImportResult?>(null)
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var current: Job? = null
    var importCharset by mutableStateOf<String?>(null)
    var sourceUri: Uri? = null
        private set
    var decodedPreview by mutableStateOf("")
    var detectedCharset by mutableStateOf("")

    fun import(repository: QuizRepository, uri: Uri, charset: String? = null) {
        if (current?.isActive == true) return
        sourceUri = uri
        importCharset = charset
        failure = null
        decodedPreview = ""
        load = ImportProgress(DocumentDetails("Документ", null), null)
        current = work.launch {
            try {
                imported = withContext(Dispatchers.IO) {
                    val worker = currentCoroutineContext()
                    var percent: Int? = -1
                    repository.previewImport(uri, charset, onDecoded = { decoded ->
                        decodedPreview = decoded.text.take(2000)
                        detectedCharset = decoded.charset
                    }) { info ->
                        worker.ensureActive()
                        val next = info.fraction?.let { (it * 100).toInt() }
                        if (next != percent) { percent = next; load = info }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("TestHelperImport", "Document import failed", error)
                failure = if (error.javaClass == IllegalArgumentException::class.java)
                    error.message ?: "У документа неподдерживаемый формат"
                else "Не удалось обработать документ. Проверьте формат файла или выберите другой."
            } finally {
                load = null
            }
        }
    }

    fun cancel() { current?.cancel() }
    override fun onCleared() { work.cancel(); super.onCleared() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DraftScreen(draft: QuizDraft, onChange: (Quiz) -> Unit, onQuestion: (Int) -> Unit,
                         onBack: () -> Unit, onSave: () -> Unit, error: String?,
                         encodingControls: @Composable () -> Unit = {}) {
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    var preview by rememberSaveable { mutableStateOf(false) }
    var firstRule by rememberSaveable { mutableStateOf(false) }
    val quiz = draft.quiz
    if (firstRule) StudyConfirmation("Считать первый вариант правильным?",
        "Это предположение пользователя, а не подтверждённый ключ. Оно применяется только к вопросам без ответа. Такие вопросы не влияют на оценку до ручного подтверждения.",
        "Применить предположение", { firstRule = false }) {
        onChange(AnswerPolicy.assumeFirst(quiz))
        firstRule = false
    }
    removing?.let { id ->
        StudyConfirmation("Удалить вопрос?", "Изменение будет применено при сохранении теста.", "Удалить",
            { removing = null }) {
            onChange(quiz.copy(questions = quiz.questions.filterNot { it.id == id }))
            removing = null
        }
    }
    val actions = @Composable {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!preview) OutlinedButton(enabled = quiz.questions.size < QuizLimits.QUESTIONS, onClick = { onQuestion(-1) }) { Text("Добавить вопрос") }
            OutlinedButton(onClick = { preview = !preview }) { Text(if (preview) "К редактированию" else "Предпросмотр") }
            Button(onClick = onSave) { Text("Сохранить тест") }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 360.dp
        Column(Modifier.fillMaxSize()) {
            ScreenList(Modifier.weight(1f)) {
                item {
                    TextButton(onClick = onBack) { Text("Назад") }
                    SectionHeading(if (preview) "Предпросмотр теста" else "Редактор теста",
                        "Вопросов: ${quiz.questions.size}" + if (quiz.generatedByAi) " · Создано ИИ" else "")
                }
                if (draft.document != null) item { encodingControls() }
                draft.document?.let { document -> item {
                    StudyPanel(tone = PanelTone.SKY) {
                        Text(documentCaption(document))
                        Text("Найдено ${draft.detectedCount ?: quiz.questions.size} вопросов, ${quiz.questions.sumOf { it.options.size }} вариантов, " +
                            "${quiz.questions.count { it.correctIndices.isNotEmpty() && it.evidence in setOf(AnswerEvidence.SOURCE, AnswerEvidence.MANUAL) }} подтверждённых ответов")
                    }
                } }
                if (draft.warnings.isNotEmpty()) item {
                    var show by rememberSaveable { mutableStateOf(false) }
                    StudyPanel(tone = PanelTone.LAVENDER) {
                        Text("Замечаний при импорте: ${draft.warnings.size}")
                        TextButton(onClick = { show = !show }) { Text(if (show) "Скрыть" else "Посмотреть замечания") }
                        if (show) draft.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                if (compact) error?.let { item { StudyError(it) } }
                item {
                    if (preview) Text(quiz.title.ifBlank { "Тест без названия" }, style = MaterialTheme.typography.titleLarge)
                    else StudyField(quiz.title, { onChange(quiz.copy(title = it.take(256))) }, "Название теста")
                }
                item {
                    StudyPanel(tone = PanelTone.SKY) {
                        val unknown = quiz.questions.count { it.correctIndices.isEmpty() }
                        val inferred = quiz.questions.count { it.evidence == AnswerEvidence.INFERRED }
                        val multiple = quiz.questions.count { it.isMultiAnswer }
                        Text("Без ключа: $unknown · Предположительных: $inferred" +
                            if (multiple > 0) " · С несколькими ответами: $multiple" else "")
                        Text("Предположения исключены из оценки. Подтвердите ответ вручную в вопросе.")
                        if (unknown > 0) OutlinedButton(onClick = { firstRule = true }) {
                            Text("Считать первый вариант правильным")
                        }
                    }
                }
                if (compact) item { actions() }
                if (quiz.questions.isEmpty()) item {
                    StudyPanel { Text("Добавьте первый вопрос и варианты ответа.") }
                }
                itemsIndexed(quiz.questions, key = { _, question -> question.id }) { index, question ->
                    StudyPanel {
                        Text("${index + 1}. ${question.text}", style = MaterialTheme.typography.titleMedium)
                        if (preview) {
                            question.options.forEachIndexed { i, value ->
                                Text(if (i in question.correctIndices) "${question.evidence.label} · $value" else "• $value")
                            }
                            question.explanation?.let { Text(it) }
                            question.sourceExcerpt?.let { Text("Цитата: $it", style = MaterialTheme.typography.bodySmall) }
                        } else {
                            Text(question.evidence.label + question.correctIndices
                                .joinToString(prefix = if (question.correctIndices.isEmpty()) "" else " · ") { question.options[it] })
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { onQuestion(index) }) { Text("Править") }
                                TextButton(enabled = index > 0, onClick = {
                                    onChange(quiz.copy(questions = EditorRules.move(quiz.questions, index, index - 1)))
                                }) { Text("Выше") }
                                TextButton(enabled = index < quiz.questions.lastIndex, onClick = {
                                    onChange(quiz.copy(questions = EditorRules.move(quiz.questions, index, index + 1)))
                                }) { Text("Ниже") }
                                TextButton(onClick = { removing = question.id }) { Text("Удалить") }
                            }
                        }
                    }
                }
            }
            if (!compact) {
                error?.let { StudyError(it) }
                actions()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuestionScreen(fields: QuestionFields, onCancel: () -> Unit, onApply: (QuizQuestion) -> Unit) {
    val question = fields.initial
    var text by fields::text
    var options by fields::options
    var correct by fields::correct
    var explanation by fields::explanation
    var failure by rememberSaveable { mutableStateOf<String?>(null) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var confirmed by rememberSaveable { mutableStateOf(false) }
    val dirty = text != question.text || options != question.options || correct != question.correctIndex || explanation != question.explanation.orEmpty() || confirmed
    fun back() { if (dirty) discard = true else onCancel() }
    BackHandler { back() }
    if (discard) StudyConfirmation("Отменить правки вопроса?", "Текст, введённый на этом экране, не сохранится.", "Отменить правки",
        { discard = false }, onCancel)
    ScreenList {
        item {
            TextButton(onClick = { back() }) { Text("Назад") }
            SectionHeading("Вопрос", "Отметьте правильный вариант или оставьте ключ неизвестным.")
        }
        failure?.let { item { StudyError(it) } }
        item { Text(question.evidence.label) }
        item { StudyField(text, { text = it.take(65_536) }, "Текст вопроса") }
        itemsIndexed(options) { i, option ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RadioButton(selected = correct == i, onClick = { correct = i; confirmed = true })
                OutlinedTextField(option, { value -> options = ArrayList(options).also { it[i] = value.take(65_536) } },
                    label = { Text("Вариант ${i + 1}") }, modifier = Modifier.weight(1f))
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = options.size < QuizLimits.OPTIONS, onClick = { options = ArrayList(options + "") }) { Text("Добавить вариант") }
                TextButton(enabled = options.size > 2, onClick = {
                    options = ArrayList(options.dropLast(1))
                    if (correct?.let { it >= options.size } == true) correct = null
                }) { Text("Убрать последний") }
                TextButton(onClick = { correct = null }) { Text(if (correct == null) "✓ Без ключа" else "Без ключа") }
            }
        }
        item { StudyField(explanation, { explanation = it.take(65_536) }, "Пояснение") }
        item { Button(onClick = {
            val changed = question.copy(text = text.trim(), options = options.map(String::trim),
                correctIndex = correct, additionalCorrectIndices = emptyList(),
                explanation = explanation.trim().ifBlank { null },
                evidence = if (correct == null) AnswerEvidence.UNKNOWN
                    else if (correct != question.correctIndex || text != question.text || options != question.options || confirmed)
                        AnswerEvidence.MANUAL else question.evidence)
            runCatching { EditorRules.validateQuestion(changed) }
                .onSuccess { onApply(changed) }.onFailure { failure = it.message }
        }, modifier = Modifier.fillMaxWidth()) { Text("Применить") } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ImportEncodingControls(editor: EditorModel, repository: QuizRepository) {
    if (editor.sourceUri == null) return
    var confirm by remember { mutableStateOf<String?>(null) }
    confirm?.let { choice ->
        StudyConfirmation("Перечитать исходный файл?", "Изменения текущего несохранённого импорта будут заменены новым предпросмотром.",
            "Перечитать", { confirm = null }) {
            editor.sourceUri?.let { editor.import(repository, it, choice.takeUnless { it == "Авто" }) }
            confirm = null
        }
    }
    StudyPanel {
        Text("Кодировка: " + editor.detectedCharset.ifBlank { "автоопределение" })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf("Авто") + TextDecoder.choices).forEach { choice ->
                FilterChip(selected = (editor.importCharset ?: "Авто") == choice,
                    onClick = { confirm = choice }, enabled = editor.load == null, label = { Text(choice) })
            }
        }
        if (editor.decodedPreview.isNotBlank()) {
            var show by remember { mutableStateOf(false) }
            TextButton(onClick = { show = !show }) { Text(if (show) "Скрыть текст" else "Предпросмотр текста") }
            if (show) Text(editor.decodedPreview, style = MaterialTheme.typography.bodySmall)
        }
        editor.load?.let { StudyLoading("Повторное чтение", it.fraction, editor::cancel) }
        editor.failure?.let { StudyError(it) }
    }
}
