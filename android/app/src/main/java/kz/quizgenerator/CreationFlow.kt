package kz.quizgenerator

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class CreationSource { CHOOSE, DOCUMENT, PHOTO, TEXT, AI }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CreationFlow(initial: CreationSource, onImport: () -> Unit, onAi: () -> Unit,
    onManual: () -> Unit, onDraft: (Quiz) -> Unit, onAccount: () -> Unit) {
    var source by rememberSaveable(initial) { mutableStateOf(initial) }
    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var count by rememberSaveable { mutableStateOf("5") }
    var difficulty by rememberSaveable { mutableStateOf("medium") }
    var selected by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val auth = LocalAuth.current
    val client = remember(auth) { AiClient(StudySettings(), authToken = { auth.token() }) }
    val scope = rememberCoroutineScope()
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (_: java.io.IOException) { error = "Нет соединения. Попробуйте позже или создайте тест вручную" }
            catch (e: Exception) { error = e.message?.take(180) ?: "Не удалось обработать материал" }
            finally { busy = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected = it }
    ScreenList {
        item {
            SectionHeading("Создать тест", "Любой источник → проверка вопросов → сохранение и прохождение")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(CreationSource.DOCUMENT to "PDF или Word", CreationSource.PHOTO to "Фотография", CreationSource.TEXT to "Текст").forEach { (key,label) ->
                    FilterChip(source == key, { source = key; error = null; selected = null }, label = { Text(label) }, enabled = !busy)
                }
            }
            Button(onClick = onAi, enabled = !busy) { Text("С помощью ИИ") }
        }
        if (source == CreationSource.CHOOSE) item {
            StudyPanel { Text("Выберите источник или создайте вопросы самостоятельно."); OutlinedButton(onClick = onManual) { Text("Создать вручную") } }
        }
        if (source == CreationSource.DOCUMENT || source == CreationSource.PHOTO) item {
            StudyPanel {
                if (source == CreationSource.DOCUMENT) {
                    Text("Готовые вопросы TXT, Word, JSON открываются локально в общем редакторе.")
                    Button(onClick = onImport, enabled = !busy) { Text("Открыть TXT, Word или JSON") }
                    HorizontalDivider()
                }
                Text(if (source == CreationSource.PHOTO) "Выберите фото с учебным текстом" else "PDF: распознать текст с ИИ")
                OutlinedButton(enabled = !busy, onClick = { picker.launch(if (source == CreationSource.PHOTO) arrayOf("image/jpeg","image/png") else arrayOf("application/pdf")) }) { Text("Выбрать файл") }
                Text(if (selected == null) "Файл не выбран" else "Файл выбран. Максимум 3 МБ")
                Text("По нажатию «Распознать» выбранный файл будет отправлен через сервер в Gemini. Не загружайте конфиденциальные данные. Проверьте текст после распознавания.")
                Button(enabled = selected != null && !busy && auth.account?.verified == true, onClick = {
                    val uri = selected ?: return@Button
                    run {
                        val attachment = withContext(Dispatchers.IO) {
                            require(uri.scheme == "content") { "Выберите документ через системный проводник" }
                            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytesBounded(3 * 1024 * 1024) } ?: error("Не удалось открыть файл")
                            val mime = AttachmentRules.detect(bytes)
                            mime to android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        }
                        text = client.extract(attachment.first, attachment.second)
                        selected = null; source = CreationSource.TEXT
                    }
                }) { Text("Распознать с ИИ") }
                if (auth.account?.verified != true) TextButton(onClick = onAccount) { Text("Войти для распознавания") }
            }
        }
        if (source == CreationSource.TEXT) item {
            StudyField(title, { title = it.take(160) }, "Название / тема")
            StudyField(text, { text = it.take(12_000) }, "Проверьте и исправьте текст (до 12 000 символов)")
            Text("Если в тексте уже есть вопросы и варианты, используйте локальный импорт. Для учебного текста — генерацию ИИ.")
            OutlinedButton(enabled = !busy && text.isNotBlank(), onClick = {
                run { val quiz = withContext(Dispatchers.Default) { QuizTextParser.parse(text,title.ifBlank { "Новый тест" }).quiz }; onDraft(quiz) }
            }) { Text("Открыть готовые вопросы в редакторе") }
            StudyField(count, { count = it.filter(Char::isDigit).take(2) }, "Вопросов с ИИ (1–25)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("easy" to "Легко","medium" to "Средне","hard" to "Сложно").forEach { (key,label) ->
                    FilterChip(difficulty==key,{difficulty=key},label={Text(label)},enabled=!busy)
                }
            }
            Text("Генерация отправит этот текст в Gemini. Проверьте ответы перед сохранением.")
            Button(enabled = !busy && text.isNotBlank() && count.toIntOrNull() in 1..25 && auth.account?.verified == true,
                onClick = { run { onDraft(client.quiz(title,text,difficulty,count.toInt())) } }) { Text("Создать вопросы с ИИ") }
            if (auth.account?.verified != true) TextButton(onClick = onAccount) { Text("Войти для ИИ") }
        }
        if (busy) item { StudyLoading("Обработка материала…") }
        error?.let { item { StudyError(it) } }
    }
}

internal object AttachmentRules {
    fun detect(bytes: ByteArray): String {
        require(bytes.isNotEmpty() && bytes.size <= 3 * 1024 * 1024) { "Файл пустой или больше 3 МБ" }
        return when {
            bytes.size >= 5 && bytes.take(5).toByteArray().contentEquals("%PDF-".toByteArray()) -> "application/pdf"
            bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "image/jpeg"
            bytes.size >= 8 && bytes.take(8).toByteArray().contentEquals(byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a)) -> "image/png"
            else -> error("Нужен неповреждённый PDF, JPEG или PNG")
        }
    }
}
