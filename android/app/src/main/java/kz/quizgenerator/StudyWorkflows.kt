package kz.quizgenerator

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File

private fun BatchKind.title()=when(this){BatchKind.QUIZ->"Большой тест";BatchKind.ANSWERS->"Определить ответы";BatchKind.LECTURE->"Учебная лекция"}
private fun BatchStatus.title()=when(this){BatchStatus.READY->"Готово к запуску";BatchStatus.RUNNING->"Обрабатывается";BatchStatus.PAUSED->"На паузе";BatchStatus.LIMIT->"Остановлено из-за лимита";BatchStatus.AUTH->"Требуется вход";BatchStatus.UNAVAILABLE->"Ожидается обновление сервера";BatchStatus.UNCERTAIN->"Исход запроса неизвестен";BatchStatus.REVIEW->"Нужна проверка";BatchStatus.CANCELLED->"Отменено";BatchStatus.COMPLETE->"Завершено"}

@Composable internal fun StudyWorkflows(repository: QuizRepository,setId: String,quizzes: List<Quiz>,initial: BatchKind?,initialQuizId: String?,
    onBack: ()->Unit,onStored: (Quiz)->Unit,onQuiz: (Quiz)->Unit,onLectureAi: (Lecture,String)->Unit) {
    val auth=LocalAuth.current;val uid=auth.account?.uid ?: return
    val store=remember(repository,uid){BatchJobStore(repository.workspaceRoot,uid)}
    val lectures=remember(repository){LectureStore(repository.workspaceRoot)}
    var form by rememberSaveable {mutableStateOf(initial!=null)}
    var kind by rememberSaveable {mutableStateOf(initial ?: BatchKind.QUIZ)}
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    var lectureId by rememberSaveable {mutableStateOf<String?>(null)}
    var revision by remember {mutableIntStateOf(0)}
    val jobs by produceState<Result<List<BatchJob>>?>(null,repository,revision) {
        while(isActive){value=withContext(Dispatchers.IO){runCatching {store.list(setId).map {j->
            if(j.status==BatchStatus.RUNNING && BatchService.active!=(uid to j.id))store.restore(j).also {store.save(it)} else j
        }}};delay(1000)}
    }
    val scope=rememberCoroutineScope();var error by remember {mutableStateOf<String?>(null)}
    error?.let {AlertDialog(onDismissRequest={error=null},title={Text("Не удалось сохранить")},text={Text(it)},confirmButton={TextButton(onClick={error=null}){Text("Понятно")}})}
    fun back(){when {lectureId!=null->lectureId=null;selected!=null->selected=null;form->form=false;else->onBack()}}
    BackHandler(onBack=::back)
    when {
        lectureId!=null->LectureScreen(lectures,lectureId!!,{lectureId=null},repository.materials,onLectureAi)
        form->BatchForm(repository,setId,quizzes,kind,initialQuizId,::back){plan->scope.launch {try {withContext(Dispatchers.IO){store.save(plan)};form=false;selected=plan.id;revision++}catch(e: CancellationException){throw e}catch(_: Exception){error="Не удалось сохранить задание. Отправки в облако не было"}}}
        selected!=null->{val j=jobs?.getOrNull()?.firstOrNull {it.id==selected}
            if(j==null)StudyColumn("Задание",::back){StudyLoading("Открываем сохранённый результат");jobs?.exceptionOrNull()?.let {StudyError("Не удалось прочитать задания. Файлы сохранены")}}
            else BatchDetails(j,store,repository,::back,onStored,onQuiz,onLecture={lectureId=it},onRefresh={revision++})}
        else->ScreenList {
            item {TextButton(onClick=onBack){Text("‹ К набору")};SectionHeading("Обработка материала","Сохранённые серии и частичные результаты")}
            item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){StitchButton(onClick={kind=BatchKind.QUIZ;form=true}){Text("Новый тест")};StitchOutlinedButton(onClick={kind=BatchKind.LECTURE;form=true}){Text("Лекция с ИИ")}}}
            if(jobs==null)item {StudyLoading("Открываем задания")}
            jobs?.exceptionOrNull()?.let {item {StudyError("Не удалось прочитать задания. Готовые файлы не изменены")}}
            error?.let {item {StudyError(it)}}
            if(jobs?.getOrNull()?.isEmpty()==true)item {Text("Заданий пока нет. Серия начнётся только после вашего согласия и запуска.")}
            items(jobs?.getOrNull().orEmpty(),key={it.id}) {j->StudyPanel(onClick={selected=j.id}) {Text(j.title,style=MaterialTheme.typography.titleMedium);Text(j.kind.title()+" · "+j.status.title());Text("Сохранено ${j.actual} / ${j.target} · ${j.done} из ${j.parts.size} партий");TextButton(onClick={selected=j.id}){Text("Открыть")}}}
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun BatchForm(repository: QuizRepository,setId: String,quizzes: List<Quiz>,kind: BatchKind,initialQuizId: String?,onBack: ()->Unit,onPlan: (BatchJob)->Unit) {
    val auth=LocalAuth.current;val scope=rememberCoroutineScope()
    var mode by rememberSaveable {mutableStateOf(AiInputMode.TOPIC)}
    var topic by rememberSaveable {mutableStateOf("")};var own by rememberSaveable {mutableStateOf("")}
    var title by rememberSaveable {mutableStateOf("")};var count by rememberSaveable {mutableStateOf("200")}
    var level by rememberSaveable {mutableStateOf("university")};var length by rememberSaveable {mutableStateOf("detailed")}
    var difficulty by rememberSaveable {mutableStateOf("medium")};var document by rememberSaveable {mutableStateOf<String?>(null)}
    var bankId by rememberSaveable {mutableStateOf(initialQuizId)}
    var busy by remember {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)}
    var pending by remember {mutableStateOf<BatchJob?>(null)}
    val materialList by produceState<Result<List<MaterialRecord>>?>(null,repository) {value=withContext(Dispatchers.IO){runCatching {val set=repository.sets.list().first {it.id==setId};repository.materials.list().map {it.getOrThrow()}.filter {it.id in set.materialIds}}}}
    val banks=quizzes.filter {repository.canDelete(it)}
    pending?.let {plan->BatchConsent(plan,{pending=null}){pending=null;onPlan(plan.accepted(System.currentTimeMillis()))}}
    StudyColumn(kind.title(),onBack) {
        if(kind==BatchKind.ANSWERS) {
            Text("Исходные вопросы сохранят текст и варианты. ИИ предложит ключи для проверки, а не создаст новый тест.")
            banks.forEach {q->FilterChip(bankId==q.id,{bankId=q.id},label={Text(q.title+" · "+q.questions.count {!it.evidence.confirmed}+" без подтверждения")})}
        } else {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){AiInputMode.entries.forEach {v->FilterChip(mode==v,{mode=v},label={Text(v.title)},enabled=!busy)}}
            OutlinedTextField(topic,{if(it.length<=160)topic=it},label={Text("Тема")},modifier=Modifier.fillMaxWidth())
            if(mode==AiInputMode.TOPIC)Text("Передаётся только тема. Прикреплённые документы не отправляются.",style=MaterialTheme.typography.bodySmall)
            if(mode==AiInputMode.TEXT){OutlinedTextField(own,{if(it.length<=120000)own=it else error="Для текста больше120000символов прикрепите TXT"},label={Text("Собственный текст")},modifier=Modifier.fillMaxWidth(),minLines=4,maxLines=8);Text("${own.length} символов",style=MaterialTheme.typography.bodySmall)}
            if(mode==AiInputMode.DOCUMENT) {
                Text("Выберите сохранённый документ. Новый файл можно прикрепить на вкладке «Материал».")
                Box(Modifier.heightIn(min=48.dp,max=180.dp)){LazyColumn {items(materialList?.getOrNull().orEmpty(),key={it.id}){m->FilterChip(document==m.id,{document=m.id},label={Text(m.name+" · "+m.characters+" символов")})}}}
                materialList?.exceptionOrNull()?.let {StudyError("Не удалось прочитать список материалов")}
            }
            OutlinedTextField(title,{if(it.length<=160)title=it},label={Text("Название результата — необязательно")},modifier=Modifier.fillMaxWidth())
            if(kind==BatchKind.QUIZ){OutlinedTextField(count,{count=it.filter(Char::isDigit).take(3)},label={Text("Всего вопросов (1–500)")},modifier=Modifier.fillMaxWidth());FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("easy" to "Легко","medium" to "Средне","hard" to "Сложно").forEach {(v,label)->FilterChip(difficulty==v,{difficulty=v},label={Text(label)})}}}
            if(kind==BatchKind.LECTURE){FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("school" to "Школа","university" to "Университет","advanced" to "Углублённо").forEach {(v,label)->FilterChip(level==v,{level=v},label={Text(label)})}};FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("brief" to "Краткая","detailed" to "Подробная").forEach {(v,label)->FilterChip(length==v,{length=v},label={Text(label)})}};Text("ИИ-лекции станут доступны после обновления сервера. Пока можно сохранить план или создать лекцию вручную.",style=MaterialTheme.typography.bodySmall)}
        }
        error?.let {StudyError(it)}
        StitchButton(enabled=!busy,onClick={busy=true;error=null;scope.launch {try {
            val plan=withContext(Dispatchers.IO){val uid=auth.account?.uid ?: error("Войдите в аккаунт")
                if(kind==BatchKind.ANSWERS)BatchPlanning.answers(uid,setId,banks.firstOrNull {it.id==bankId} ?: error("Выберите тест"))
                else {val source=if(mode==AiInputMode.DOCUMENT)repository.materials.text(document ?: error("Выберите документ")) else if(mode==AiInputMode.TEXT)own else ""
                    val name=title.ifBlank {topic.ifBlank {"Учебный материал"}}
                    if(kind==BatchKind.QUIZ)BatchPlanning.quiz(uid,setId,name,mode,topic,source,count.toIntOrNull() ?: 0,difficulty,if(mode==AiInputMode.DOCUMENT)"material:"+document else null)
                    else BatchPlanning.lecture(uid,setId,name,mode,topic,source,level,length,if(mode==AiInputMode.DOCUMENT)"material:"+document else null)
                }};pending=plan
        }catch(e: CancellationException){throw e}catch(e: Exception){error=e.message?.takeIf {it.any {c->c in 'А'..'я'}} ?: "Не удалось составить план. Проверьте источник и количество"}finally{busy=false}}}){Text("Подготовить серию")}
        if(busy)StudyLoading("Проверяем материал и число запросов")
    }
}

@Composable private fun BatchConsent(job: BatchJob,onDismiss: ()->Unit,onAccept: ()->Unit) {
    var preview by remember {mutableStateOf(false)};var index by remember {mutableIntStateOf(0)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Разрешить эту серию?")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(job.kind.title()+": "+job.title);Text("Запланировано ${job.parts.size} запросов. Цель: ${job.target}. Успехом считается фактически сохранённый результат.")
        Text("Выбранная тема/текст будут переданы через Cloudflare в Gemini. Запросы идут последовательно, не более 3 в минуту. Дневная квота может остановить серию; продолжение — по вашему действию.")
        Text("Изменение источника или назначения потребует нового согласия. После сохранения плана запуск выполняется отдельной кнопкой.")
        TextButton(onClick={preview=!preview}){Text(if(preview)"Скрыть отправляемый текст" else "Проверить отправляемый текст")}
        if(preview){val p=job.parts[index];val body=JSONObject(p.payload);Text("Запрос ${index+1} из ${job.parts.size}");Row {TextButton(enabled=index>0,onClick={index--}){Text("Назад")};TextButton(enabled=index<job.parts.lastIndex,onClick={index++}){Text("Далее")}};Text(body.optString("topic"));if(body.optString("context").isNotBlank())Text("Контекст: "+body.getString("context"));if(body.has("questions")){val q=body.getJSONArray("questions");for(i in 0 until q.length()){val v=q.getJSONObject(i);Text(v.getString("text"));val options=v.getJSONArray("options");for(k in 0 until options.length())Text("• "+options.getString(k))}} else Text(body.optString("material").ifBlank {"Документ не передаётся"})}
    }},confirmButton={TextButton(onClick=onAccept){Text("Разрешить серию")}},dismissButton={TextButton(onClick=onDismiss){Text("Отмена")}})
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun BatchDetails(job: BatchJob,store: BatchJobStore,repository: QuizRepository,onBack: ()->Unit,onStored: (Quiz)->Unit,onQuiz: (Quiz)->Unit,onLecture: (String)->Unit,onRefresh: ()->Unit) {
    val context=LocalContext.current;val auth=LocalAuth.current;val scope=rememberCoroutineScope()
    val client=remember(auth){AiClient(StudySettings(),authToken={auth.token()})};val engine=remember(store){BatchEngine(store)}
    var error by remember {mutableStateOf<String?>(null)};var busy by remember {mutableStateOf(false)}
    var capabilities by remember {mutableStateOf(setOf("quizzes"))};var checked by remember {mutableStateOf(false)}
    var cancel by remember {mutableStateOf(false)};var retry by remember {mutableStateOf(false)}
    var permissionJob by remember {mutableStateOf<String?>(null)}
    var reviewId by rememberSaveable {mutableStateOf<String?>(null)};var chosen by rememberSaveable {mutableStateOf<List<Int>>(emptyList())}
    var shown by rememberSaveable {mutableIntStateOf(20)}
    fun start(){if(auth.account?.uid!=job.uid || auth.account?.verified!=true){error="Подтвердите вход и почту в профиле";return};if(BatchService.active!=null){error="Сначала приостановите текущую обработку";return}
        try {if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){permissionJob=job.id;return};BatchService.start(context,job.uid,job.id)}catch(_: Exception){error="Android не разрешил фоновый запуск. Задание сохранено; повторите из открытого приложения"}}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted){permissionJob=null;start()}else {permissionJob=null;error="Задание сохранено. Разрешите уведомления для фоновой обработки"}}
    LaunchedEffect(permissionJob){if(permissionJob!=null && Build.VERSION.SDK_INT>=33)permission.launch(Manifest.permission.POST_NOTIFICATIONS)}
    fun action(block: suspend ()->Unit){busy=true;error=null;scope.launch {try {block();onRefresh()}catch(e: CancellationException){throw e}catch(e: Exception){error=e.message?.takeIf {it.any {c->c in 'А'..'я'}} ?: "Не удалось выполнить действие. Сохранённые данные не изменены"}finally{busy=false}}}
    if(cancel)StudyConfirmation("Отменить обработку?","Готовые партии останутся доступными. Уже начатый запрос может учитываться в квоте.","Отменить",{cancel=false}){cancel=false;action {withContext(Dispatchers.IO){engine.pause(job.id,true)};BatchService.pause(context,job.uid,job.id)}}
    if(retry)StudyConfirmation("Разрешить один новый запрос?","Сервер мог выполнить прошлый запрос и учесть его в квоте, но ответ не получен. Старые результаты не отправятся повторно. Новый запрос может ещё раз расходовать квоту.","Разрешить повтор",{retry=false}){retry=false;action {withContext(Dispatchers.IO){engine.retryUnknown(job.id)};start()}}
    val route=job.parts.first().route;val available=route in capabilities
    ScreenList {
        item {TextButton(onClick=onBack){Text("‹ К заданиям")};SectionHeading(job.title,job.status.title())}
        item {StudyPanel {Text("${job.actual} / ${job.target} "+if(job.kind==BatchKind.LECTURE)"частей лекции" else "вопросов сохранено");Text("${job.done} из ${job.parts.size} партий завершено");LinearProgressIndicator(progress={job.done.toFloat()/job.parts.size},modifier=Modifier.fillMaxWidth());if(job.notice.isNotBlank())Text(job.notice)}}
        if(!available)item {StudyPanel {Text(if(checked)"Функция ожидает обновления сервера. План и готовые результаты сохранены на устройстве." else "Функция ожидает обновления сервера. Генерация ещё не запускалась.");TextButton(enabled=!busy,onClick={action {capabilities=client.capabilities();checked=true}}){Text("Проверить доступность")}}}
        error?.let {item {StudyError(it)}}
        item {FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(job.status==BatchStatus.RUNNING || BatchService.active==(job.uid to job.id))StitchOutlinedButton(enabled=!busy,onClick={action {withContext(Dispatchers.IO){engine.pause(job.id)};BatchService.pause(context,job.uid,job.id)}}){Text("Приостановить")}
            else if(job.status !in listOf(BatchStatus.COMPLETE,BatchStatus.CANCELLED,BatchStatus.UNCERTAIN,BatchStatus.REVIEW))StitchButton(enabled=!busy && available,onClick={action {if(job.status!=BatchStatus.READY)withContext(Dispatchers.IO){engine.resume(job.id)};start()}}){Text(if(job.done==0)"Начать" else "Продолжить")}
            if(job.status !in listOf(BatchStatus.COMPLETE,BatchStatus.CANCELLED))TextButton(enabled=!busy,onClick={cancel=true}){Text("Отменить")}
            if(job.parts.any {it.status==BatchPartStatus.UNKNOWN})StitchOutlinedButton(enabled=!busy && available,onClick={retry=true}){Text("Проверить и повторить запрос")}
        }}
        if(job.kind==BatchKind.QUIZ && job.actual>0)item {StitchButton(enabled=!busy,onClick={action {val saved=withContext(Dispatchers.IO){BatchPublication.saveQuiz(store.load(job.id),repository,store)};onStored(saved);onQuiz(saved)}}){Text(if(job.status==BatchStatus.COMPLETE)"Открыть тест" else "Открыть частичный тест (${job.actual})")}}
        if(job.kind==BatchKind.LECTURE && job.actual>0)item {StitchButton(enabled=!busy,onClick={action {val id=withContext(Dispatchers.IO){val records=LectureStore(repository.workspaceRoot);val current=store.load(job.id);val existing=if(records.exists(job.id))records.load(job.id) else null;if(existing==null || !existing.edited)records.save(current.lecture().copy(createdAt=existing?.createdAt ?: current.createdAt));job.id};onLecture(id)}}){Text(if(job.status==BatchStatus.COMPLETE)"Открыть лекцию" else "Открыть частичный материал")}}
        if(job.kind==BatchKind.ANSWERS && job.actual>0) {
            val original=QuizJson.parse(requireNotNull(job.sourceQuiz));val questions=original.questions.associateBy {it.id};val answers=job.proposals()
            item {Text("Все ответы ИИ требуют проверки. Ключи источника, ручные подтверждения и изменённые вопросы не заменяются.")
                StitchOutlinedButton(enabled=!busy,onClick={action {val saved=withContext(Dispatchers.IO){val file=File(repository.workspaceRoot,"quiz_banks/"+original.id+".json");require(file.exists()) {"Исходный тест удалён"};repository.saveQuiz(BatchAnswerMerge.apply(job,QuizJson.parse(file.readText())))};onStored(saved)}}){Text("Сохранить предположения в исходный тест")}}
            items(answers.take(shown),key={it.getString("id")}) {a->val q=questions.getValue(a.getString("id"));StudyPanel {
                Text(q.text,style=MaterialTheme.typography.titleMedium);Text(if(a.getBoolean("uncertain"))"Ответ требует проверки" else "Предположение ИИ — не подтверждённый ключ")
                val indices=a.getJSONArray("indices");Text("Предполагается: "+List(indices.length()){q.options[indices.getInt(it)]}.joinToString().ifBlank {"Неизвестно"});Text(a.getString("explanation"))
                TextButton(onClick={reviewId=if(reviewId==q.id)null else q.id;chosen=List(indices.length()){indices.getInt(it)}}){Text("Проверить / исправить")}
                if(reviewId==q.id){q.options.forEachIndexed {index,option->Row {Checkbox(index in chosen,{value->chosen=if(value)(chosen+index).distinct() else chosen-index});Text(option,Modifier.weight(1f))}}
                    StitchButton(enabled=!busy && chosen.isNotEmpty(),onClick={action {val saved=withContext(Dispatchers.IO){val file=File(repository.workspaceRoot,"quiz_banks/"+original.id+".json");require(file.exists()) {"Исходный тест удалён"};val current=QuizJson.parse(file.readText());val actual=current.questions.firstOrNull {it.id==q.id} ?: error("Вопрос удалён");require(!actual.evidence.confirmed) {"Ответ уже подтверждён и не будет заменён"};require(AnswerPolicy.fingerprint(actual)==AnswerPolicy.fingerprint(q)) {"Вопрос изменён: проверьте его в редакторе"};repository.saveQuiz(BatchAnswerMerge.apply(job,current,q.id,chosen.toSet()))};onStored(saved);reviewId=null}}){Text("Подтвердить выбранные ответы")}
                }
            }}
            if(answers.size>shown)item {TextButton(onClick={shown+=20}){Text("Показать ещё · всего ${answers.size}")}}
        }
        if(busy)item {StudyLoading("Сохраняем действие")}
    }
}
