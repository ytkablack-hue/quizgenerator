package kz.quizgenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.*

private enum class AiTask(val title: String) {QUIZ("Тест"),CARDS("Карточки"),REVIEW("Ошибки")}
private data class GenerationRequest(val task: AiTask,val topic: String,val material: String,val difficulty: String,val count: Int)

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun AiScreen(settings: StudySettings,attempt: Attempt?,repository: QuizRepository,
    saveQuiz: (Quiz)->Unit,saveCards: (List<Flashcard>)->Boolean,onBack: ()->Unit,onAccount: ()->Unit,initialTask: String="quizzes") {
    val auth=LocalAuth.current;val scope=rememberCoroutineScope();val context=LocalContext.current
    val model=remember(auth.account?.uid) {ViewModelProvider(context.studyActivity()).get("materials:"+auth.account?.uid,MaterialUiModel::class.java)}
    LaunchedEffect(repository) {model.bind(repository)}
    val client=remember(auth) {AiClient(settings,authToken={auth.token()})}
    var mode by rememberSaveable(initialTask,attempt?.id) {mutableStateOf(when(initialTask){"review"->AiTask.REVIEW;"cards"->AiTask.CARDS;else->AiTask.QUIZ})}
    var job by remember {mutableStateOf<Job?>(null)};val busy=job?.isActive==true
    var failure by remember {mutableStateOf<String?>(null)};var notice by remember {mutableStateOf<String?>(null)}
    var review by remember {mutableStateOf<AiReview?>(null)}
    var viewer by rememberSaveable {mutableStateOf(false)}
    var pending by remember {mutableStateOf<GenerationRequest?>(null)}
    var rangeStart by rememberSaveable(model.draft.sourceId,model.draft.part) {mutableStateOf("")}
    var rangeEnd by rememberSaveable(model.draft.sourceId,model.draft.part) {mutableStateOf("")}
    var rangeConfirmed by rememberSaveable(model.draft.sourceId,model.draft.part,rangeStart,rangeEnd) {mutableStateOf(false)}
    val draft=model.draft;val record=model.records.firstOrNull {it.id==draft.sourceId}
    val reviewText=remember(attempt) {attempt?.let {AiClient.reviewText(it)}.orEmpty()}
    val reviewParts=remember(reviewText) {MaterialSlices.plan(reviewText)}
    val parts=if(mode==AiTask.REVIEW)reviewParts else model.parts
    val selected=parts.getOrNull(draft.part.coerceAtMost((parts.size-1).coerceAtLeast(0)))
    val full=if(mode==AiTask.REVIEW)reviewText else model.text
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {it?.let(model::attach)}
    fun prepare() {
        try {
            require(mode!=AiTask.REVIEW || (attempt?.mistakes?.isNotEmpty()==true && selected!=null)) { "Нет ошибок для разбора" }
            require(mode==AiTask.REVIEW || draft.sourceId==null || (record!=null && model.text.isNotEmpty() && selected!=null)) { "Дождитесь открытия выбранного документа" }
            val body=if(selected?.oversized==true) {
                require(rangeConfirmed) {"Подтвердите выбор неполного фрагмента"}
                val a=(rangeStart.toIntOrNull() ?: error("Укажите первый символ"))-1
                val b=rangeEnd.toIntOrNull() ?: error("Укажите последний символ")
                require(a>=selected.start && b<=selected.end && a<b && b-a<=MaterialSlices.LIMIT) {"Диапазон должен быть внутри фрагмента и не длиннее9800символов"}
                require(!Character.isLowSurrogate(full[a]) && (b==full.length || !Character.isLowSurrogate(full[b]))) {"Граница разрезает символ Unicode"}
                full.substring(a,b)
            } else selected?.text(full).orEmpty()
            val material=MaterialSlices.compose(body,if(mode==AiTask.REVIEW)"" else draft.extra)
            require(material.isNotBlank() || draft.topic.isNotBlank()) {"Добавьте тему или материал"}
            pending=GenerationRequest(mode,if(mode==AiTask.REVIEW)"" else draft.topic,material,draft.difficulty,draft.count.toIntOrNull() ?: 0)
        } catch(e: Exception) {failure=e.message ?: "Проверьте выбранный фрагмент"}
    }
    pending?.let {request -> StudyConfirmation("Отправить выбранный материал?",
        "Будет выполнен 1 запрос через Cloudflare в Gemini: "+request.material.length+" символов. "+
        (if(parts.size>1)"Документ охвачен частично: выбрана одна часть из "+parts.size+". Остальные части автоматически не отправляются. " else "")+
        "Проверьте, что материал разрешено передавать в облако.","Отправить",{pending=null}) {
        pending=null;failure=null;notice=null
        job=scope.launch {
            try {
                when(request.task) {
                    AiTask.QUIZ -> saveQuiz(client.quiz(request.topic,request.material,request.difficulty,request.count))
                    AiTask.CARDS -> model.pendingCards=client.cards(request.topic,request.material,request.difficulty,request.count)
                    AiTask.REVIEW -> review=client.reviewMaterial(request.material)
                }
                notice="Выбранный фрагмент обработан. Для другого фрагмента выберите его и отправьте отдельный запрос"
            } catch(e: CancellationException){throw e}
              catch(_: java.io.IOException){failure="Нет соединения. Документы сохранены локально; повторите запрос позже"}
              catch(e: Exception){failure=e.message?.take(200) ?: "Не удалось выполнить запрос"}
            finally {job=null}
        }
    }}
    if(viewer && record!=null && model.text.isNotEmpty())MaterialViewer(record,model.text,draft.previewPage,
        {model.update(model.draft.copy(previewPage=it))},{viewer=false})
    StudyColumn("Учиться с ИИ",onBack) {
        Text("Документы хранятся на устройстве. В облако отправляется только явно выбранный фрагмент.")
        if(auth.account?.verified!=true)TextButton(onClick=onAccount) {Text("Подтвердить почту для Gemini")}
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {AiTask.entries.forEach {task -> FilterChip(mode==task,{mode=task;model.update(draft.copy(part=0))},label={Text(task.title)},enabled=!busy)}}
        if(mode!=AiTask.REVIEW) {
            OutlinedButton(enabled=model.ready && model.progress==null && !busy,onClick={picker.launch(arrayOf("text/plain","application/pdf","application/json","image/jpeg","image/png","application/vnd.openxmlformats-officedocument.wordprocessingml.document"))}) {Text("Добавить документ или фото")}
            if(model.records.isEmpty())StudyPanel {Text("Прикрепите материал. Его полный текст будет сохранён отдельно от поля ниже.")}
            model.records.take(50).forEach {item -> StudyPanel(tone=if(item.id==draft.sourceId)PanelTone.SKY else PanelTone.PLAIN) {
                Text(item.name,style=MaterialTheme.typography.titleMedium)
                Text(item.format.uppercase()+" · "+item.bytes+" байт · "+item.characters+" символов · Обработан")
                if(item.notice.isNotBlank())Text(item.notice)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled=!busy,onClick={model.select(item.id)}) {Text(if(item.id==draft.sourceId)"Выбран" else "Выбрать")}
                    TextButton(onClick={if(item.id!=draft.sourceId)model.select(item.id);viewer=true}) {Text("Просмотр")}
                    TextButton(enabled=!busy,onClick={job=scope.launch {try {saveQuiz(withContext(Dispatchers.IO) {repository.questionsFromMaterial(item.id).quiz})}catch(e: CancellationException){throw e}catch(e: Exception){failure=e.message ?: "Готовые вопросы не найдены. Используйте создание с ИИ"}finally{job=null}}}) {Text("Открыть готовые вопросы")}
                }
            }}
            if(model.records.size>50)Text("Показаны50последних документов. Всего: "+model.records.size)
            if(draft.sourceId!=null)TextButton(enabled=!busy,onClick={model.select(null)}) {Text("Использовать только тему и собственный текст")}
            StudyField(draft.topic,{model.update(draft.copy(topic=it))},"Тема")
            OutlinedTextField(draft.extra,{model.update(draft.copy(extra=it))},modifier=Modifier.fillMaxWidth(),label={Text("Дополнительный текст — необязательно")},minLines=2,maxLines=5)
            Text(draft.extra.length.toString()+" /2000 · Содержимое файла сюда не вставляется",style=MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {listOf("easy" to "Легко","medium" to "Средне","hard" to "Сложно").forEach {(id,title)-> FilterChip(draft.difficulty==id,{model.update(draft.copy(difficulty=id))},label={Text(title)},enabled=!busy)}}
            StudyField(draft.count,{model.update(draft.copy(count=it.filter(Char::isDigit).take(2)))},"Количество (1–25)")
            if(mode==AiTask.QUIZ)Text("Gemini создаёт вопросы с одним ключом. Несколько ключей доступны в импорте и редакторе. Ответы ИИ требуют подтверждения.")
        } else Text(attempt?.let {"Разбор ошибок: "+it.title} ?: "Сначала завершите тест с ошибками")
        if(parts.isNotEmpty())StudyPanel(tone=PanelTone.SKY) {
            Text("Выбран фрагмент "+(parts.indexOf(selected)+1)+" из "+parts.size+" · всего "+full.length+" символов")
            Text("На отправку: "+(selected?.let {it.end-it.start} ?: 0)+" символов. Полный документ сохранён; автоматической отправки всех частей нет.")
            Row {TextButton(enabled=!busy && draft.part>0,onClick={model.update(draft.copy(part=draft.part-1))}){Text("Предыдущий")};TextButton(enabled=!busy && draft.part+1<parts.size,onClick={model.update(draft.copy(part=draft.part+1))}){Text("Следующий")}}
            if(selected?.oversized==true) {
                Text("Этот блок нельзя автоматически разделить без риска разрезать вопрос. Выберите диапазон вручную (символы "+(selected.start+1)+"–"+selected.end+").")
                StudyField(rangeStart,{rangeStart=it.filter(Char::isDigit)},"С символа")
                StudyField(rangeEnd,{rangeEnd=it.filter(Char::isDigit)},"По символ включительно")
                Row {Checkbox(rangeConfirmed,{rangeConfirmed=it});Text("Понимаю, что это неполный блок; проверю контекст и результат")}
            }
        }
        Button(enabled=model.ready && !busy && model.progress==null && auth.account?.verified==true && (mode==AiTask.REVIEW || draft.count.toIntOrNull() in 1..25),onClick=::prepare,modifier=Modifier.fillMaxWidth()) {Text("Проверить и отправить 1 запрос")}
        if(model.pendingCards.isNotEmpty() && mode==AiTask.CARDS) {
            Text("Карточек создано: "+model.pendingCards.size+" · ответы ИИ требуют проверки")
            model.pendingCards.forEach {card->StudyPanel {Text(card.front);Text(card.back)}}
            Button(onClick={if(saveCards(model.pendingCards)){model.pendingCards=emptyList();notice="Карточки сохранены"}}) {Text("Сохранить карточки")}
        }
        review?.let {Text(it.summary);it.steps.forEach {step->Text("• "+step)}}
        model.progress?.let {StudyLoading("Загрузка и извлечение текста: "+it.document.name,it.fraction,model::cancel)}
        if(busy)StudyLoading("Генерация выбранного материала",cancel={job?.cancel();notice="Запрос отменён на устройстве. Если сервер уже начал обработку, запрос может учитываться в квоте"})
        model.error?.let {StudyError(it)};failure?.let {StudyError(it)};notice?.let {Text(it)}
    }
}
