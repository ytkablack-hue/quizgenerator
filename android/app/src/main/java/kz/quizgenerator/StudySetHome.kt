package kz.quizgenerator
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun StudySetHome(repository: QuizRepository,quizzes: List<Quiz>,study: StudyData,
    onOpen: (String)->Unit,onCreate: ()->Unit,onProfile: ()->Unit,onExport: (StudySet)->Unit,onLegacy: ()->Unit) {
    var query by rememberSaveable {mutableStateOf("")};var revision by remember {mutableIntStateOf(0)}
    var trash by rememberSaveable {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)}
    var rename by remember {mutableStateOf<StudySet?>(null)};var title by rememberSaveable {mutableStateOf("")}
    var archiving by remember {mutableStateOf<StudySet?>(null)}
    val scope=rememberCoroutineScope();val user=LocalAuth.current.account
    val state by produceState<Result<List<StudySet>>?>(null,repository,quizzes,study.cards,revision) {
        value=withContext(Dispatchers.IO) {runCatching {
            val materials=repository.materials.list();require(materials.none {it.isFailure}) {"Один из документов повреждён. Старые тесты доступны отдельно"}
            WorkspaceIndex.build(repository.sets.list(),quizzes,materials.map {it.getOrThrow()},study.cards)
        }}
    }
    fun run(action: suspend ()->Unit) {scope.launch {try {action();revision++}catch(e: CancellationException){throw e}catch(e: Exception){error=e.message ?: "Не удалось изменить набор. Файлы сохранены"}}}
    fun open(set: StudySet) = run {
        withContext(Dispatchers.IO) {repository.sets.ensure(set);repository.sets.update(set.id){it.copy(lastOpened=System.currentTimeMillis())}}
        onOpen(set.id)
    }
    rename?.let {set -> AlertDialog(onDismissRequest={rename=null},title={Text("Название набора")},
        text={OutlinedTextField(title,{title=it.take(160)},label={Text("Название")})},
        confirmButton={TextButton(enabled=title.isNotBlank(),onClick={run {withContext(Dispatchers.IO){repository.sets.ensure(set);repository.sets.update(set.id){it.copy(title=title.trim())}};rename=null}}){Text("Сохранить")}},
        dismissButton={TextButton(onClick={rename=null}){Text("Отмена")}})}
    archiving?.let {set -> StudyConfirmation("Переместить набор в корзину?","Исходные материалы, вопросы и история останутся на устройстве. Набор можно восстановить из корзины.","В корзину",{archiving=null}) {
        run {withContext(Dispatchers.IO){repository.sets.ensure(set);repository.sets.update(set.id){it.copy(archived=true)}};archiving=null}
    }}
    val all=state?.getOrNull().orEmpty()
    val shown=all.filter {it.archived==trash && (query.isBlank() || it.title.contains(query,true))}.sortedByDescending {it.lastOpened}
    ScreenList {
        item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Привет, "+(user?.name?.ifBlank {null} ?: user?.email?.substringBefore('@') ?: "друг"),style=MaterialTheme.typography.headlineSmall)
                Text("Что будем изучать сегодня?",color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick=onProfile,modifier=Modifier.semantics {contentDescription="Личный кабинет"}) {StudyIcon(Glyph.PROFILE)}
        }}
        item {OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),label={Text("Поиск учебных наборов")},singleLine=true)}
        item {StudyPanel(tone=PanelTone.SKY,onClick=onCreate) {Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            StudyIcon(Glyph.ADD,Modifier.size(36.dp));Column(Modifier.weight(1f)){Text("Создать новый набор",style=MaterialTheme.typography.titleLarge);Text("Тема · PDF · DOCX · TXT · Фото",style=MaterialTheme.typography.bodySmall)};Text("›")
        };StudyPrimary("Создать набор",onClick=onCreate)}}
        study.exams.filter {!it.date.isBefore(java.time.LocalDate.now())}.minByOrNull {it.date}?.let {exam->item {Text(exam.title+" · "+examCountdown(exam.date),color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        val today=java.time.LocalDate.now()
        val todayAttempts=study.attempts.filter {java.time.Instant.ofEpochMilli(it.completedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()==today}
        if(todayAttempts.isNotEmpty())item {StudyPanel {SectionHeading("Сегодня",todayAttempts.size.toString()+" завершённых попыток · "+todayAttempts.sumOf {it.score.correct+it.score.incorrect+it.score.skipped+it.score.ungraded}+" вопросов")}}
        item {SectionHeading("Мои учебные наборы",all.count {!it.archived}.toString()+" активных")}
        if(all.any {it.archived} || trash)item {TextButton(onClick={trash=!trash}) {Text(if(trash)"Вернуться к наборам" else "Корзина · "+all.count {it.archived})}}
        if(state==null)item {StudyLoading("Открываем библиотеку")}
        state?.exceptionOrNull()?.let {item {StudyError(it.message ?: "Не удалось открыть библиотеку наборов");TextButton(onClick={revision++}){Text("Повторить")};TextButton(onClick=onLegacy){Text("Открыть сохранённые тесты")}}}
        repository.libraryWarnings.forEach {warning->item {StudyError(warning)}}
        error?.let {item {StudyError(it)}}
        if(state?.isSuccess==true && shown.isEmpty())item {StudyPanel(tone=PanelTone.SKY) {
            StudyIcon(Glyph.LIBRARY)
            SectionHeading(if(query.isNotBlank())"Ничего не найдено" else if(trash)"Корзина пуста" else "Готов к первому набору?",
                if(query.isNotBlank())"Попробуйте другое название" else "Добавьте материал, выберите способ подготовки и начните заниматься.")

        }}
        items(shown,key={it.id}) {set ->
            var menu by remember {mutableStateOf(false)}
            val banks=quizzes.filter {it.id in set.quizIds};val cards=WorkspaceIndex.cards(set,study)
            val attempts=WorkspaceIndex.attempts(set,study);val reviewed=cards.count {it.reviews>0}
            StudyPanel(onClick={if(!set.archived)open(set)}) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    StudyIcon(Glyph.LIBRARY);Spacer(Modifier.width(10.dp));Text(set.title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    Box {IconButton(onClick={menu=true},modifier=Modifier.semantics {contentDescription="Действия набора "+set.title}) {Text("⋮")}
                        DropdownMenu(menu,{menu=false}) {
                            if(!set.archived) {
                                DropdownMenuItem(text={Text("Открыть")},onClick={menu=false;open(set)})
                                DropdownMenuItem(text={Text("Переименовать")},onClick={title=set.title;rename=set;menu=false})
                                DropdownMenuItem(text={Text("Экспортировать ZIP")},onClick={menu=false;onExport(set)})
                                DropdownMenuItem(text={Text("В корзину")},onClick={menu=false;archiving=set})
                            } else DropdownMenuItem(text={Text("Восстановить")},onClick={menu=false;run {withContext(Dispatchers.IO){repository.sets.update(set.id){it.copy(archived=false)}}}})
                        }
                    }
                }
                Text(set.materialIds.size.toString()+" материалов · "+banks.sumOf {it.questions.size}+" вопросов · "+cards.size+" карточек")
                if(cards.isNotEmpty()) {LinearProgressIndicator(progress={reviewed.toFloat()/cards.size},modifier=Modifier.fillMaxWidth());Text("Повторено карточек: "+reviewed+" из "+cards.size)}
                val last=attempts.maxByOrNull {it.completedAt}
                Text(last?.let {"Последняя попытка: "+displayDate(it.completedAt)+if(it.score.gradedTotal==0)" · без подтверждённого ключа" else " · "+it.score.correct+" из "+it.score.gradedTotal} ?: "Попыток пока нет")
                if(set.lastOpened>0)Text("Последнее открытие: "+displayDate(set.lastOpened),style=MaterialTheme.typography.bodySmall)
                if(!set.archived)OutlinedButton(onClick={open(set)}){Text("Начать подготовку →")}
            }
        }
    }
}

@Composable internal fun NewStudySetScreen(repository: QuizRepository,onCreated: (String)->Unit,onBack: ()->Unit) {
    var title by rememberSaveable {mutableStateOf("")};var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)};val scope=rememberCoroutineScope()
    StudyColumn("Новый учебный набор",onBack) {
        StudyIcon(Glyph.LIBRARY,Modifier.size(48.dp))
        Text("Один набор — весь материал и подготовка",style=MaterialTheme.typography.headlineSmall)
        Text("Назовите тему, затем добавьте документ, вопросы или карточки. Сеть для этого не нужна.")
        OutlinedTextField(title,{title=it.take(160)},label={Text("Например, Механика · экзамен")},modifier=Modifier.fillMaxWidth())
        Button(enabled=!busy && title.isNotBlank(),onClick={busy=true;scope.launch {
            try {val set=withContext(Dispatchers.IO){repository.sets.create(title)};title="";onCreated(set.id)}
            catch(e: CancellationException){throw e}catch(e: Exception){error=e.message ?: "Не удалось создать набор"}finally{busy=false}
        }},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text("Создать набор")}
        if(busy)StudyLoading("Сохраняем набор");error?.let {StudyError(it)}
    }
}
