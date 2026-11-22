package kz.quizgenerator
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.*

private enum class SetTab(val title: String) {MATERIAL("Материал"),TEST("Тест"),CARDS("Карточки"),PREPARE("Подготовка")}
@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable internal fun StudySetWorkspace(id: String,repository: QuizRepository,quizzes: List<Quiz>,study: StudyData,
    update: (StudyData)->Boolean,onBack: ()->Unit,onQuiz: (Quiz)->Unit,onEdit: (Quiz?)->Unit,onAi: (String)->Unit,
    onCardsFromQuiz: (Quiz)->Unit,onImportQuiz: (Quiz)->Unit,onOptions: ()->Unit,onExport: (StudySet)->Unit,onOwnText: ()->Unit) {
    val auth=LocalAuth.current;val context=LocalContext.current;val scope=rememberCoroutineScope()
    val model=remember(auth.account?.uid) {ViewModelProvider(context.studyActivity()).get("materials:"+auth.account?.uid,MaterialUiModel::class.java)}
    LaunchedEffect(repository){model.bind(repository)}
    var revision by remember {mutableIntStateOf(0)};var tab by rememberSaveable(id) {mutableStateOf(SetTab.MATERIAL)}
    var error by remember {mutableStateOf<String?>(null)};var viewer by rememberSaveable {mutableStateOf(false)}
    var notes by rememberSaveable(id) {mutableStateOf("")};var notesLoaded by rememberSaveable(id) {mutableStateOf(false)}
    var leaving by remember {mutableStateOf(false)};var saving by remember {mutableStateOf(false)}
    val state by produceState<Result<StudySet>?>(null,id,revision,model.records,model.setRevision) {
        value=withContext(Dispatchers.IO) {runCatching {repository.sets.list().firstOrNull {it.id==id && !it.archived} ?: error("Набор недоступен или находится в корзине")}}
    }
    val set=state?.getOrNull()
    LaunchedEffect(set?.id,model.ready,model.records) {
        if(set!=null && model.ready) {val next=AiGeneration.initialDocument(model.draft.inputMode,model.draft.sourceId,set.materialIds);if(next!=model.draft.sourceId)model.select(next)}
        if(set!=null && !notesLoaded){notes=set.notes;notesLoaded=true}
    }
    val dirty=set!=null && notesLoaded && notes!=set.notes
    fun back(){if(dirty)leaving=true else onBack()}
    BackHandler(onBack=::back)
    fun saveNotes(exit: Boolean=false) {saving=true;scope.launch {
        try {withContext(Dispatchers.IO){repository.sets.update(id){it.copy(notes=notes)}};revision++;if(exit)onBack()}
        catch(e: CancellationException){throw e}catch(e: Exception){error=e.message ?: "Не удалось сохранить заметку"}finally{saving=false}
    }}
    if(leaving)AlertDialog(onDismissRequest={leaving=false},title={Text("Сохранить заметку?")},text={Text("В заметке есть несохранённые изменения.")},
        confirmButton={TextButton(enabled=!saving,onClick={leaving=false;saveNotes(true)}){Text("Сохранить и выйти")}},
        dismissButton={TextButton(onClick={leaving=false;onBack()}){Text("Выйти без сохранения")}})
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null)model.attach(uri,id)}
    val selected=model.records.firstOrNull {it.id==model.draft.sourceId && it.id in set?.materialIds.orEmpty()}
    if(viewer && selected!=null && model.text.isNotEmpty())MaterialViewer(selected,model.text,model.draft.previewPage,{model.update(model.draft.copy(previewPage=it))},{viewer=false},sourceFile=repository.materials.source(selected.id))
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=::back,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)){Text("‹")}
            Text(set?.title ?: "Учебный набор",Modifier.weight(1f).horizontalScroll(rememberScrollState()),style=MaterialTheme.typography.titleLarge)
            if(set!=null)TextButton(onClick={onExport(set)}){Text("ZIP")}
        }
        if(state==null)StudyLoading("Открываем набор")
        state?.exceptionOrNull()?.let {StudyError(it.message ?: "Не удалось открыть набор");TextButton(onClick={revision++}){Text("Повторить")}}
        if(set!=null) {
            val banks=quizzes.filter {it.id in set.quizIds};val cards=WorkspaceIndex.cards(set,study);val history=WorkspaceIndex.attempts(set,study)
            StudyPanel {Text(set.title,style=MaterialTheme.typography.headlineSmall);Text(set.materialIds.size.toString()+" материалов · "+banks.sumOf {it.questions.size}+" вопросов · "+cards.size+" карточек",style=MaterialTheme.typography.bodySmall)}
            ScrollableTabRow(selectedTabIndex=tab.ordinal,edgePadding=0.dp) {SetTab.entries.forEach {item->Tab(tab==item,{tab=item},text={Text(item.title)})}}
            if(tab==SetTab.MATERIAL)FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={if(banks.isNotEmpty())onQuiz(banks.first()) else onAi("quizzes")}){Text("Быстрый тест")};OutlinedButton(onClick={tab=SetTab.CARDS}){Text("Учить карточки")}}
            error?.let {StudyError(it)}
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when(tab) {
                    SetTab.MATERIAL -> ScreenList {
                        item {Button(enabled=model.ready && model.progress==null,onClick={picker.launch(arrayOf("text/plain","application/pdf","application/json","image/jpeg","image/png","application/vnd.openxmlformats-officedocument.wordprocessingml.document"))},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text("Добавить документ или фото")}}
                        model.progress?.let {item {StudyLoading(it.phase+": "+it.document.name,it.fraction,model::cancel)}}
                        model.error?.let {item {StudyError(it)}}
                        if(set.materialIds.isEmpty())item {StudyPanel(tone=PanelTone.SKY) {StudyIcon(Glyph.FILE);Text("Здесь будет исходный материал");Text("Сохраните полный документ локально. Готовые вопросы можно открыть без Gemini.")}}
                        items(model.records.filter {it.id in set.materialIds},key={it.id}) {record->FileAttachmentCard(record,
                            onOpen={model.select(record.id);viewer=true},
                            onRecognize={scope.launch {try {val quiz=withContext(Dispatchers.IO){repository.questionsFromMaterial(record.id).quiz};onImportQuiz(quiz)}catch(e: CancellationException){throw e}catch(e: Exception){error=e.message ?: "Готовые вопросы не найдены"}}},
                            onChoose={model.update(model.draft.copy(inputMode=AiInputMode.DOCUMENT));model.select(record.id);onAi("quizzes")},
                            onDetach={scope.launch {try {withContext(Dispatchers.IO){repository.sets.update(id){it.copy(materialIds=it.materialIds-record.id)}};revision++}catch(e: CancellationException){throw e}catch(_: Exception){error="Не удалось открепить документ"}}})}
                        item {Text("Открепление не удаляет оригинал: материал остаётся в локальной библиотеке.",style=MaterialTheme.typography.bodySmall)}
                        item {SectionHeading("Моя заметка",if(dirty)"Есть несохранённые изменения" else "Только на этом устройстве")}
                        item {OutlinedTextField(notes,{if(it.length<=20000)notes=it else error="Заметка длиннее20000символов"},modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=8,label={Text("Заметка по материалу")})}
                        item {Button(enabled=dirty && !saving,onClick={saveNotes()}){Text("Сохранить заметку")}}
                        item {OutlinedButton(onClick=onOwnText){Text("Добавить вопросы из собственного текста")}}
                    }
                    SetTab.TEST -> ScreenList {
                        item {SectionHeading("Выберите способ подготовки","Существующие вопросы открываются локально; облако — только по отдельному подтверждению")}
                        item {MethodCard("Параметры прохождения","Общие настройки: таймер, перемешивание и число вопросов",Glyph.SETTINGS,onOptions)}
                        item {MethodCard("Создать тест с ИИ","Выберите материал, фрагмент, сложность и количество вопросов",Glyph.AI){onAi("quizzes")}}
                        item {MethodCard("Создать вопросы вручную","Один или несколько правильных вариантов, без интернета",Glyph.EDIT){onEdit(null)}}
                        if(banks.isEmpty())item {StudyPanel {Text("В наборе пока нет готовых вопросов. Откройте их из документа на вкладке «Материал» или создайте вручную.")}}
                        items(banks,key={it.id}) {bank ->StudyPanel {
                            Text(bank.title,style=MaterialTheme.typography.titleMedium);Text(bank.questions.size.toString()+" вопросов")
                            val unverified=bank.questions.count {!it.evidence.confirmed || it.correctIndices.isEmpty()}
                            if(unverified>0)Text("Без подтверждённого ключа: "+unverified+". Они не участвуют в строгой оценке.")
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {Button(onClick={onQuiz(bank)}){Text("Открыть тест")};TextButton(onClick={onEdit(bank)}){Text("Редактировать")};TextButton(onClick={onCardsFromQuiz(bank)}){Text("Сделать карточки")}}
                        }}
                    }
                    SetTab.CARDS -> Column(Modifier.fillMaxSize()) {
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick={onAi("cards")}) {Text("Добавить с Gemini")}
                            banks.forEach {bank->TextButton(onClick={onCardsFromQuiz(bank)}) {Text("Из вопросов: "+bank.title)}}
                        }
                        Box(Modifier.weight(1f)) {CoinFlashcardsScreen(cards,{updated ->
                            val old=cards.map {it.id}.toSet();val ok=update(study.copy(cards=study.cards.filterNot {it.id in old}+updated))
                            if(ok){val added=updated.filter {it.id !in old}.map {it.id};if(added.isNotEmpty())runCatching {repository.sets.linkCards(id,added)}.onFailure {error="Карточка сохранена в библиотеке, но связь с набором не записана"};revision++};ok
                        },{tab=SetTab.TEST},embedded=true)}
                    }
                    SetTab.PREPARE -> ScreenList {
                        item {SectionHeading("Подготовка по набору",history.size.toString()+" сохранённых попыток")}
                        items(banks,key={it.id}) {bank->val practice=WorkspaceIndex.practice(bank,history)
                            if(practice!=null)MethodCard("Повторить ошибки: "+bank.title,practice.questions.size.toString()+" доступных вопросов с подтверждённым ключом",Glyph.PLAN){onQuiz(practice)}
                        }
                        if(history.isEmpty())item {StudyPanel(tone=PanelTone.SKY){Text("История появится после первого завершённого теста. Статистика не заполняется примерными значениями.")}}
                        items(history.sortedByDescending {it.completedAt},key={it.id}) {attempt->StudyPanel {
                            Text(attempt.title,style=MaterialTheme.typography.titleMedium);Text(displayDate(attempt.completedAt))
                            Text("Правильно "+attempt.score.correct+" · Ошибок "+attempt.score.incorrect+" · Пропущено "+attempt.score.skipped)
                            Text("Без оценки: "+attempt.score.ungraded);Text("Время: "+StudySessionPolicy.durationLabel(attempt.durationMillis))
                            attempt.mistakes.forEach {mistake->Text(mistake.question);Text("Ваш ответ: "+(mistake.given ?: "Пропущен"));Text("Ключ: "+mistake.correct)}
                        }}
                    }
                }
            }
        }
    }
}

@Composable private fun MethodCard(title: String,detail: String,glyph: Glyph,onClick: ()->Unit) {
    StudyPanel(onClick=onClick,tone=PanelTone.SKY) {Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        StudyIcon(glyph);Column(Modifier.weight(1f)) {Text(title,style=MaterialTheme.typography.titleMedium);Text(detail,style=MaterialTheme.typography.bodyMedium)};Text("›")
    }}
}
