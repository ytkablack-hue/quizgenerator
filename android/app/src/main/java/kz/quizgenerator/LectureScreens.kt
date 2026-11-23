package kz.quizgenerator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun LectureScreen(store: LectureStore,id: String,onBack: ()->Unit,materials: MaterialStore?=null,onAi: ((Lecture,String)->Unit)?=null) {
    var sourceOpen by remember {mutableStateOf(false)};var sourcePage by remember {mutableIntStateOf(0)}
    var revision by remember {mutableIntStateOf(0)}
    val loaded by produceState<Result<Lecture>?>(null,id,revision){value=withContext(Dispatchers.IO){runCatching {store.load(id)}}}
    var editing by rememberSaveable(id){mutableStateOf(false)}
    var title by rememberSaveable(id){mutableStateOf("")};var body by remember(id){mutableStateOf("")}
    var actions by remember {mutableStateOf(false)}
    var deleting by remember {mutableStateOf(false)};var discard by remember {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)};val scope=rememberCoroutineScope()
    fun back(){if(editing)discard=true else onBack()}
    BackHandler(onBack=::back)
    if(discard)StudyConfirmation("Отменить правки лекции?","Несохранённый текст будет потерян.","Выйти",{discard=false}){discard=false;editing=false}
    if(deleting)StudyConfirmation("Удалить эту лекцию?","Прикреплённый исходный документ и тесты останутся на устройстве.","Удалить",{deleting=false}){scope.launch {try {withContext(Dispatchers.IO){store.delete(id)};onBack()}catch(_: Exception){error="Не удалось удалить лекцию"}finally{deleting=false}}}
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        TextButton(onClick=::back){Text("‹ К материалам")}
        if(loaded==null)StudyLoading("Открываем лекцию")
        loaded?.exceptionOrNull()?.let {StudyError("Не удалось прочитать лекцию. Исходный файл не изменён")}
        error?.let {StudyError(it)}
        loaded?.getOrNull()?.let {lecture->
            val source by produceState<Result<Pair<MaterialRecord,String>>?>(null,lecture.sourceRef,materials) {
                val id=lecture.sourceRef?.takeIf {it.startsWith("material:")}?.removePrefix("material:")
                if(id!=null && materials!=null)value=withContext(Dispatchers.IO){runCatching {materials.load(id) to materials.text(id)}}
            }
            if(sourceOpen)source?.getOrNull()?.let {(record,text)->MaterialViewer(record,text,sourcePage,{sourcePage=it},{sourceOpen=false},sourceFile=materials?.source(record.id))}
            if(editing) {
                OutlinedTextField(title,{if(it.length<=160)title=it},label={Text("Название")},modifier=Modifier.fillMaxWidth())
                Text("Заголовки сохраняются с #, списки с -. Формулы — обычным текстом.",fontSize=13.sp)
                OutlinedTextField(body,{if(it.length<=8_000_000)body=it else error="Лекция слишком длинная"},modifier=Modifier.weight(1f).fillMaxWidth(),label={Text("Содержимое лекции")})
                StitchButton(enabled=!busy && title.isNotBlank() && body.isNotBlank(),onClick={busy=true;scope.launch {try {withContext(Dispatchers.IO){store.save(lecture.copy(title=title.trim(),body=body,edited=true))};editing=false;revision++}catch(_: Exception){error="Не удалось сохранить правки. Оригинал не перезаписан"}finally{busy=false}}}){Text("Сохранить лекцию")}
            } else {
                Text(lecture.title,style=MaterialTheme.typography.titleLarge)
                Text(if(lecture.complete)"Сохранена для офлайн-чтения" else "Частичный материал — генерация ещё не завершена",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if(lecture.edited)"Отредактировано пользователем. Формулировки и цитаты требуют проверки." else "Материал подготовлен ИИ. Пояснения не являются подтверждённым источником.",fontSize=13.sp)
                if(lecture.sourceRef?.startsWith("material:")==true) {
                    source?.getOrNull()?.let {(record,_)->TextButton(onClick={sourceOpen=true}){Text("Источник: "+record.name)}}
                    source?.exceptionOrNull()?.let {StudyError("Исходный документ недоступен. Сохранённая лекция остаётся на устройстве")}
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    StitchOutlinedButton(onClick={title=lecture.title;body=lecture.body;editing=true}){Text("Редактировать")}
                    Box {
                        TextButton(onClick={actions=true}){Text("Действия ⋮")}
                        DropdownMenu(actions,{actions=false}) {
                            if(onAi!=null){DropdownMenuItem(text={Text("Создать тест")},onClick={actions=false;onAi(lecture,"quizzes")});DropdownMenuItem(text={Text("Создать карточки")},onClick={actions=false;onAi(lecture,"cards")})}
                            DropdownMenuItem(text={Text("Переименовать")},onClick={actions=false;title=lecture.title;body=lecture.body;editing=true})
                            DropdownMenuItem(text={Text("Удалить лекцию")},onClick={actions=false;deleting=true})
                        }
                    }
                }
                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=20.dp)) {
                    items(lecture.body.lineSequence().toList()){line->
                        when {line.startsWith("#")->Text(line.trimStart('#',' '),fontSize=if(line.startsWith("###"))18.sp else 20.sp,lineHeight=26.sp,fontWeight=FontWeight.SemiBold)
                            line.startsWith(">")->Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.small){Text(line.removePrefix(">").trim(),Modifier.fillMaxWidth().padding(12.dp),fontSize=15.sp,lineHeight=23.sp)}
                            line.isNotBlank()->Text(line,fontSize=16.sp,lineHeight=24.sp)}
                    }
                }
            }
        }
    }
}
internal fun localLecture(setId: String)=Lecture(UUID.randomUUID().toString(),setId,"Новая лекция","## Введение\nДобавьте учебный материал.",null,null,true,edited=true)
