package kz.quizgenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.*

private enum class AiTask(val title: String) {QUIZ("Тест"),CARDS("Карточки"),REVIEW("Ошибки")}
private data class GenerationRequest(val task: AiTask,val topic: String,val material: String,val difficulty: String,val count: Int,val series: String,val part: Int,val total: Int,val wholePart: Boolean)

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun AiScreen(settings: StudySettings,attempt: Attempt?,repository: QuizRepository,
    saveQuiz: (Quiz)->Unit,saveCards: (List<Flashcard>)->Boolean,onBack: ()->Unit,onAccount: ()->Unit,initialTask: String="quizzes",studySetId: String?=null) {
    val auth=LocalAuth.current;val scope=rememberCoroutineScope();val context=LocalContext.current
    val model=remember(auth.account?.uid) {ViewModelProvider(context.studyActivity()).get("materials:"+auth.account?.uid,MaterialUiModel::class.java)}
    LaunchedEffect(repository) {model.bind(repository)}
    var currentSet by remember(studySetId) {mutableStateOf<StudySet?>(null)}
    var setReady by remember(studySetId) {mutableStateOf(studySetId==null)}
    LaunchedEffect(studySetId,model.records,model.ready,model.setRevision,model.draft.inputMode) {
        if(studySetId!=null && model.ready)try {
            val set=withContext(Dispatchers.IO){repository.sets.list().first {it.id==studySetId && !it.archived}}
            currentSet=set;setReady=true
            val next=AiGeneration.initialDocument(model.draft.inputMode,model.draft.sourceId,set.materialIds)
            if(next!=model.draft.sourceId)model.select(next)
        } catch(e: CancellationException){throw e}catch(_: Exception){setReady=false}
    }
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
    var documentLimit by rememberSaveable {mutableIntStateOf(20)}
    val draft=model.draft;val visibleRecords=model.records.filter {studySetId==null || it.id in currentSet?.materialIds.orEmpty()};val record=model.records.firstOrNull {it.id==draft.sourceId}
    val reviewText=remember(attempt) {attempt?.let {AiClient.reviewText(it)}.orEmpty()}
    val reviewParts=remember(reviewText) {MaterialSlices.plan(reviewText)}
    val parts=if(mode==AiTask.REVIEW)reviewParts else if(draft.inputMode==AiInputMode.DOCUMENT)model.parts else emptyList()
    val selected=parts.getOrNull(draft.part.coerceAtMost((parts.size-1).coerceAtLeast(0)))
    val full=if(mode==AiTask.REVIEW)reviewText else model.text
    val series=MaterialSeries.key(if(mode==AiTask.REVIEW)attempt?.id.orEmpty() else draft.sourceId ?: "manual",mode.name+draft.inputMode.name,draft.topic,draft.extra+draft.ownText,draft.difficulty,draft.count)
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null)model.attach(uri,studySetId)}
    fun prepare() {
        try {
            require(mode!=AiTask.REVIEW || (attempt?.mistakes?.isNotEmpty()==true && selected!=null)) { "Нет ошибок для разбора" }
            require(mode==AiTask.REVIEW || draft.inputMode!=AiInputMode.DOCUMENT || (record!=null && model.text.isNotEmpty() && selected!=null)) { "Дождитесь открытия выбранного документа" }
            require(setReady && (mode==AiTask.REVIEW || draft.inputMode!=AiInputMode.DOCUMENT || studySetId==null || draft.sourceId in currentSet?.materialIds.orEmpty())) {"Дождитесь открытия материала этого набора"}
            val body=if(selected?.oversized==true) {
                require(rangeConfirmed) {"Подтвердите выбор неполного фрагмента"}
                val a=(rangeStart.toIntOrNull() ?: error("Укажите первый символ"))-1
                val b=rangeEnd.toIntOrNull() ?: error("Укажите последний символ")
                require(a>=selected.start && b<=selected.end && a<b && b-a<=MaterialSlices.LIMIT) {"Диапазон должен быть внутри фрагмента и не длиннее9800символов"}
                require(!Character.isLowSurrogate(full[a]) && (b==full.length || !Character.isLowSurrogate(full[b]))) {"Граница разрезает символ Unicode"}
                full.substring(a,b)
            } else selected?.text(full).orEmpty()
            val material=if(mode==AiTask.REVIEW)MaterialSlices.compose(body,"") else AiGeneration.build(
                draft.inputMode,draft.topic,draft.ownText,body,draft.extra,draft.difficulty,draft.count.toIntOrNull() ?: 0).getString("material")
            require(material.isNotBlank() || draft.topic.isNotBlank()) {"Добавьте тему или материал"}
            pending=GenerationRequest(mode,if(mode==AiTask.REVIEW)"" else draft.topic,material,draft.difficulty,draft.count.toIntOrNull() ?: 0,series,parts.indexOf(selected),parts.size,selected!=null && !selected.oversized)
        } catch(e: Exception) {failure=e.message ?: "Проверьте выбранный фрагмент"}
    }
    pending?.let {request ->
        var preview by remember(request) {mutableStateOf(false)}
        AlertDialog(onDismissRequest={pending=null},title={Text("Отправить один запрос?")},text={
            Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Через Cloudflare в Gemini будет передано "+request.material.length+" символов. Остальные части автоматически не отправляются.")
                if(request.total>0)Text("Выбрана часть "+(request.part+1)+" из "+request.total+". Охват документа частичный.")
                record?.notice?.takeIf {it.isNotBlank()}?.let {Text(it)}
                Text("Не отправляйте конфиденциальные материалы без права передачи.")
                TextButton(onClick={preview=!preview}){Text(if(preview)"Скрыть текст" else "Посмотреть точный текст запроса")}
                if(preview)androidx.compose.foundation.text.selection.SelectionContainer {Column {Text("Тема: "+request.topic);Text("Материал: "+request.material.ifBlank {"Не передаётся"});Text("Количество: "+request.count+" · "+request.difficulty)}}
            }
        },dismissButton={TextButton(onClick={pending=null}){Text("Отмена")}},confirmButton={TextButton(onClick={
        pending=null;failure=null;notice=null
        job=scope.launch {
            try {
                when(request.task) {
                    AiTask.QUIZ -> {val quiz=client.quiz(request.topic,request.material,request.difficulty,request.count);if(request.wholePart)model.completed(request.series,request.part);saveQuiz(quiz)}
                    AiTask.CARDS -> {model.pendingCards=client.cards(request.topic,request.material,request.difficulty,request.count);if(request.wholePart)model.completed(request.series,request.part)}
                    AiTask.REVIEW -> {review=client.reviewMaterial(request.material);if(request.wholePart)model.completed(request.series,request.part)}
                }
                notice=if(request.wholePart)"Получен ответ для части "+(request.part+1)+" из "+request.total+". Остальные части не отправлялись" else "Получен ответ для выбранного диапазона или темы; это не полный охват документа"
            } catch(e: CancellationException){throw e}
              catch(_: java.io.IOException){failure="Нет соединения. Документы сохранены локально; повторите запрос позже"}
              catch(e: Exception){failure=e.message?.take(200) ?: "Не удалось выполнить запрос"}
            finally {job=null}
        }
        }){Text("Отправить")}})
    }
    if(viewer && record!=null && model.text.isNotEmpty())MaterialViewer(record,model.text,draft.previewPage,
        {model.update(model.draft.copy(previewPage=it))},{viewer=false},sourceFile=repository.materials.source(record.id))
    StudyColumn("Учиться с ИИ",onBack) {
        StudyPanel(tone=PanelTone.SKY){Text("Источник и метод подготовки",style=MaterialTheme.typography.titleMedium);Text("Файлы обрабатываются локально. Только после подтверждения выбранный текст передаётся через Cloudflare в Gemini.",style=MaterialTheme.typography.bodySmall)}
        if(auth.account?.verified!=true)TextButton(onClick=onAccount) {Text("Подтвердить почту для Gemini")}
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {AiTask.entries.forEach {task -> FilterChip(mode==task,{mode=task;model.update(draft.copy(part=0))},label={Text(task.title)},enabled=!busy)}}
        if(mode!=AiTask.REVIEW) {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {AiInputMode.entries.forEach {input->FilterChip(draft.inputMode==input,{model.update(draft.copy(inputMode=input,part=0));failure=null},label={Text(input.title)},enabled=!busy && model.progress==null)}}
            if(draft.inputMode==AiInputMode.DOCUMENT) {
            OutlinedButton(enabled=model.ready && model.progress==null && !busy,onClick={picker.launch(arrayOf("text/plain","application/pdf","application/json","image/jpeg","image/png","application/vnd.openxmlformats-officedocument.wordprocessingml.document"))}) {Text("Добавить документ или фото")}
            if(visibleRecords.isEmpty())StudyPanel {Text("Прикрепите материал. Его полный текст будет сохранён отдельно от поля ниже.")}
            visibleRecords.take(documentLimit).forEach {item ->FileAttachmentCard(item,item.id==draft.sourceId,
                onChoose={model.select(item.id)},onOpen={if(item.id!=draft.sourceId)model.select(item.id);viewer=true},
                onRecognize={if(!busy)job=scope.launch {try {saveQuiz(withContext(Dispatchers.IO){repository.questionsFromMaterial(item.id).quiz})}catch(e: CancellationException){throw e}catch(e: Exception){failure=e.message ?: "Готовые вопросы не найдены"}finally{job=null}}},
                onDetach={if(!busy){model.select(null);notice="Выбор снят. Полный документ остаётся в библиотеке"}},detachLabel="Снять выбор")}
            if(visibleRecords.size>documentLimit)TextButton(onClick={documentLimit=(documentLimit+20).coerceAtMost(visibleRecords.size)}) {Text("Показать ещё · всего "+visibleRecords.size)}
            if(draft.sourceId!=null)TextButton(enabled=!busy,onClick={model.select(null)}) {Text("Снять выбор документа")}
            }
            if(draft.inputMode==AiInputMode.TOPIC)Text("Отправится только тема. Прикреплённые документы и собственный текст не передаются.")
            if(draft.inputMode==AiInputMode.TEXT) {
                OutlinedTextField(draft.ownText,{model.update(draft.copy(ownText=it))},modifier=Modifier.fillMaxWidth(),label={Text("Собственный текст")},minLines=5,maxLines=10)
                Text(draft.ownText.length.toString()+" / "+AiLimits.MATERIAL+" символов")
            }
            StudyField(draft.topic,{model.update(draft.copy(topic=it))},"Тема")
            if(draft.inputMode==AiInputMode.DOCUMENT)OutlinedTextField(draft.extra,{model.update(draft.copy(extra=it))},modifier=Modifier.fillMaxWidth(),label={Text("Дополнительный текст — необязательно")},minLines=2,maxLines=5)
            if(draft.inputMode==AiInputMode.DOCUMENT)Text(draft.extra.length.toString()+" /2000 · Содержимое файла сюда не вставляется",style=MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {listOf("easy" to "Легко","medium" to "Средне","hard" to "Сложно").forEach {(id,title)-> FilterChip(draft.difficulty==id,{model.update(draft.copy(difficulty=id))},label={Text(title)},enabled=!busy)}}
            StudyField(draft.count,{model.update(draft.copy(count=it.filter(Char::isDigit).take(2)))},"Количество (1–25)")
            if(mode==AiTask.QUIZ)Text("Gemini создаёт вопросы с одним ключом. Несколько ключей доступны в импорте и редакторе. Ответы ИИ требуют подтверждения.")
        } else Text(attempt?.let {"Разбор ошибок: "+it.title} ?: "Сначала завершите тест с ошибками")
        if(parts.isNotEmpty())StudyPanel(tone=PanelTone.SKY) {
            Text("Выбран фрагмент "+(parts.indexOf(selected)+1)+" из "+parts.size+" · всего "+full.length+" символов")
            Text("Получен ответ для "+draft.completed[series].orEmpty().count {it in parts.indices}+" из "+parts.size+" частей при текущих настройках")
            Text("На отправку: "+(selected?.let {it.end-it.start} ?: 0)+" символов. Полный документ сохранён; автоматической отправки всех частей нет.")
            Row {TextButton(enabled=!busy && draft.part>0,onClick={model.update(draft.copy(part=draft.part-1))}){Text("Предыдущий")};TextButton(enabled=!busy && draft.part+1<parts.size,onClick={model.update(draft.copy(part=draft.part+1))}){Text("Следующий")}}
            if(selected?.oversized==true) {
                Text("Этот блок нельзя автоматически разделить без риска разрезать вопрос. Выберите диапазон вручную (символы "+(selected.start+1)+"–"+selected.end+").")
                StudyField(rangeStart,{rangeStart=it.filter(Char::isDigit)},"С символа")
                StudyField(rangeEnd,{rangeEnd=it.filter(Char::isDigit)},"По символ включительно")
                Row {Checkbox(rangeConfirmed,{rangeConfirmed=it});Text("Понимаю, что это неполный блок; проверю контекст и результат")}
            }
        }
        StudyPrimary(if(mode==AiTask.CARDS)"Создать карточки с ИИ" else if(mode==AiTask.REVIEW)"Разобрать ошибки с ИИ" else "Создать тест с ИИ",enabled=model.ready && setReady && !busy && model.progress==null && auth.account?.verified==true && (mode==AiTask.REVIEW || draft.count.toIntOrNull() in 1..AiLimits.COUNT),onClick=::prepare)
        if(model.pendingCards.isNotEmpty() && mode==AiTask.CARDS) {
            Text("Карточек создано: "+model.pendingCards.size+" · ответы ИИ требуют проверки")
            model.pendingCards.forEach {card->StudyPanel {Text(card.front);Text(card.back)}}
            Button(onClick={if(saveCards(model.pendingCards)){model.pendingCards=emptyList();notice="Карточки сохранены"}}) {Text("Сохранить карточки")}
        }
        review?.let {Text(it.summary);it.steps.forEach {step->Text("• "+step)}}
        model.progress?.let {StudyLoading(it.phase+": "+it.document.name,it.fraction,model::cancel)}
        if(busy)StudyLoading("Генерация выбранного материала",cancel={job?.cancel();notice="Запрос отменён на устройстве. Если сервер уже начал обработку, запрос может учитываться в квоте"})
        model.error?.let {StudyError(it)};failure?.let {StudyError(it)};notice?.let {Text(it)}
    }
}
