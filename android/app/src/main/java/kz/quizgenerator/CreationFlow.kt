package kz.quizgenerator
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

internal enum class CreationSource {CHOOSE,DOCUMENT,PHOTO,TEXT,AI}
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun CreationFlow(initial: CreationSource,onImport: ()->Unit,onAi: ()->Unit,
    onManual: ()->Unit,onDraft: (Quiz)->Unit,onAccount: ()->Unit) {
    var source by rememberSaveable(initial) {mutableStateOf(initial)}
    var title by rememberSaveable {mutableStateOf("")};var text by rememberSaveable {mutableStateOf("")}
    var count by rememberSaveable {mutableStateOf("5")};var difficulty by rememberSaveable {mutableStateOf("medium")}
    var error by remember {mutableStateOf<String?>(null)};var pending by remember {mutableStateOf(false)}
    var job by remember {mutableStateOf<Job?>(null)};val busy=job?.isActive==true
    val scope=rememberCoroutineScope();val auth=LocalAuth.current
    val client=remember(auth) {AiClient(StudySettings(),authToken={auth.token()})}
    if(pending)StudyConfirmation("Отправить собственный текст?","Тема: "+title+". Материал: "+(if(source==CreationSource.AI)0 else text.length)+" символов. Один запрос; документ не передаётся.","Отправить",{pending=false}) {
        pending=false;job=scope.launch {try {onDraft(client.quiz(title,if(source==CreationSource.AI)"" else text,difficulty,count.toInt()))}
            catch(e: CancellationException){throw e}catch(e: Exception){error=e.message ?: "Не удалось создать вопросы"}finally{job=null}}
    }
    ScreenList {
        item {SectionHeading("Создать учебный материал","Выберите источник и способ подготовки")}
        item {FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf(CreationSource.AI to "По теме",CreationSource.DOCUMENT to "Документ",CreationSource.PHOTO to "Фото",CreationSource.TEXT to "Свой текст").forEach {(id,label)->FilterChip(source==id,{source=id;error=null},label={Text(label)},enabled=!busy)}
        }}
        if(source==CreationSource.TEXT || source==CreationSource.AI) {
            item {StudyField(title,{title=it.take(160)},"Тема / название")}
            if(source==CreationSource.TEXT)item {OutlinedTextField(text,{if(it.length<=12000)text=it else error="Текст длиннее12000символов. Прикрепите TXT: полный документ будет сохранён отдельно"},modifier=Modifier.fillMaxWidth(),label={Text("Собственный текст")},minLines=5,maxLines=10)}
            if(source==CreationSource.TEXT)item {Text(text.length.toString()+" /12000 · файлы не вставляются в это поле")}
            if(source==CreationSource.TEXT)item {OutlinedButton(enabled=!busy,onClick={runCatching {QuizTextParser.parse(text,title).quiz}.onSuccess(onDraft).onFailure {error=it.message ?: "Не удалось найти готовые вопросы"}}) {Text("Открыть готовые вопросы")}}
            item {StudyField(count,{count=it.filter(Char::isDigit).take(2)},"Вопросов (1–25)")}
            item {FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {listOf("easy" to "Легко","medium" to "Средне","hard" to "Сложно").forEach {(id,label)->FilterChip(difficulty==id,{difficulty=id},label={Text(label)})}}}
            item {Button(enabled=!busy && (if(source==CreationSource.AI)title.isNotBlank() else text.isNotBlank()) && count.toIntOrNull() in 1..25 && auth.account?.verified==true,onClick={pending=true}) {Text("Создать тест с Gemini")}}
        } else {
            item {StudyPanel(tone=PanelTone.SKY) {
                StudyIcon(if(source==CreationSource.PHOTO)Glyph.FILE else Glyph.LIBRARY)
                Text("Материал отдельно от инструкций",style=MaterialTheme.typography.titleLarge)
                Text("Прикрепите PDF, Word, TXT, JSON или фото. Полный текст сохранится на устройстве; для Gemini выберете один фрагмент.")
                Button(onClick=onAi,modifier=Modifier.fillMaxWidth()) {Text("Добавить материал и выбрать действие")}
            }}
            item {StudyPanel {
                Text("В файле уже есть тестовые вопросы?")
                OutlinedButton(onClick=onImport) {Text("Импортировать готовые вопросы локально")}
                Text("Это не вызывает Gemini и не отправляет файл в сеть.")
            }}
        }
        item {TextButton(onClick=onManual,enabled=!busy) {Text("Создать вопросы вручную")}}
        if(auth.account?.verified!=true)item {TextButton(onClick=onAccount) {Text("Подтвердить почту для Gemini")}}
        if(busy)item {StudyLoading("Генерация выбранного текста",cancel={job?.cancel()})}
        error?.let {item {StudyError(it)}}
    }
}

internal object AttachmentRules {
    fun detect(bytes: ByteArray): String {
        require(bytes.isNotEmpty() && bytes.size<=3*1024*1024) {"Файл пустой или больше3МБ"}
        return when {
            bytes.size>=5 && bytes.take(5).toByteArray().contentEquals("%PDF-".toByteArray())->"application/pdf"
            bytes.size>=3 && bytes[0]==0xff.toByte() && bytes[1]==0xd8.toByte() && bytes[2]==0xff.toByte()->"image/jpeg"
            bytes.size>=8 && bytes.take(8).toByteArray().contentEquals(byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a))->"image/png"
            else -> error("Нужен неповреждённый PDF, JPEG или PNG")
        }
    }
}
