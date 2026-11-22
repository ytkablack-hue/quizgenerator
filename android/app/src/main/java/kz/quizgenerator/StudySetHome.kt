package kz.quizgenerator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

@Composable internal fun StudySetHome(repository: QuizRepository,quizzes: List<Quiz>,study: StudyData,
    onOpen: (String)->Unit,onCreate: ()->Unit,onExport: (StudySet)->Unit,onLegacy: ()->Unit,onPreparation: ()->Unit) {
    var revision by remember {mutableIntStateOf(0)}
    var error by remember {mutableStateOf<String?>(null)}
    var rename by remember {mutableStateOf<StudySet?>(null)}
    var title by rememberSaveable {mutableStateOf("")}
    var archiving by remember {mutableStateOf<StudySet?>(null)}
    val scope=rememberCoroutineScope();val user=LocalAuth.current.account
    val state by produceState<Result<List<StudySet>>?>(null,repository,quizzes,study.cards,revision) {
        value=withContext(Dispatchers.IO) {runCatching {
            val materials=repository.materials.list()
            require(materials.none {it.isFailure}) {"Один из документов повреждён. Старые тесты доступны отдельно"}
            WorkspaceIndex.build(repository.sets.list(),quizzes,materials.map {it.getOrThrow()},study.cards)
        }}
    }
    fun run(action: suspend ()->Unit) {scope.launch {
        try {action();revision++}catch(e: CancellationException){throw e}
        catch(e: Exception){error=e.message ?: "Не удалось изменить набор. Файлы сохранены"}
    }}
    fun open(set: StudySet)=run {
        withContext(Dispatchers.IO){repository.sets.ensure(set);repository.sets.update(set.id){it.copy(lastOpened=System.currentTimeMillis())}}
        onOpen(set.id)
    }
    rename?.let {set->AlertDialog(onDismissRequest={rename=null},title={Text("Название набора")},
        text={OutlinedTextField(title,{title=it.take(160)},label={Text("Название")})},
        confirmButton={TextButton(enabled=title.isNotBlank(),onClick={run {withContext(Dispatchers.IO){repository.sets.ensure(set);repository.sets.update(set.id){it.copy(title=title.trim())}};rename=null}}){Text("Сохранить")}},
        dismissButton={TextButton(onClick={rename=null}){Text("Отмена")}})}
    archiving?.let {set->StudyConfirmation("Переместить набор в корзину?","Исходные материалы, вопросы и история останутся на устройстве. Набор можно восстановить из корзины.","В корзину",{archiving=null}) {
        run {withContext(Dispatchers.IO){repository.sets.ensure(set);repository.sets.update(set.id){it.copy(archived=true)}};archiving=null}
    }}
    StudyHomeFeed(
        name=user?.name?.ifBlank {null} ?: user?.email?.substringBefore('@') ?: "друг",
        sets=state?.getOrNull().orEmpty(),quizzes=quizzes,study=study,loading=state==null,
        problems=listOfNotNull(state?.exceptionOrNull()?.let {it.message ?: "Не удалось открыть библиотеку наборов"},error)+repository.libraryWarnings,
        onOpen=::open,onCreate=onCreate,onRename={title=it.title;rename=it},onExport=onExport,onArchive={archiving=it},
        onRestore={set->run {withContext(Dispatchers.IO){repository.sets.update(set.id){it.copy(archived=false)}}}},
        onPreparation=onPreparation,onRetry={revision++},onLegacy=onLegacy)
}

/** Display-only numbers. A review is not mastery, and an ungraded attempt is not a score. */
internal data class HomeProgress(val label: String,val completed: Int,val total: Int) {
    val fraction: Float get()=if(total>0)(completed.toFloat()/total).coerceIn(0f,1f) else 0f
}
internal fun homeProgress(cards: List<Flashcard>,attempts: List<Attempt>): HomeProgress? {
    val reviewed=cards.count {it.reviews>0}
    if(reviewed>0)return HomeProgress("Повторено карточек",reviewed,cards.size)
    val latest=attempts.maxByOrNull {it.completedAt} ?: return null
    return latest.score.takeIf {it.gradedTotal>0}?.let {HomeProgress("Последний тест",it.correct,it.gradedTotal)}
}
internal fun homeFilteredSets(sets: List<StudySet>,query: String,trash: Boolean)=sets
    .filter {it.archived==trash && (query.isBlank() || it.title.contains(query.trim(),true))}.sortedByDescending {it.lastOpened}

@Composable internal fun StudyHomeHeader(name: String,onProfile: ()->Unit,onPreparation: ()->Unit) {
    val colors=MaterialTheme.colorScheme
    Surface(color=colors.surface,contentColor=colors.onSurface) {
        Column(Modifier.windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal))) {
            Row(Modifier.fillMaxWidth().heightIn(min=56.dp).padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                kz.quizgenerator.ui.components.TestHelperMark(Modifier.size(36.dp))
                Column(Modifier.weight(1f)) {
                    Text("TestHelper",fontSize=16.sp,lineHeight=20.sp,fontWeight=FontWeight.Bold)
                    Text("Учебный ассистент",fontSize=11.sp,lineHeight=16.sp,color=colors.onSurfaceVariant)
                }
                IconButton(onClick=onPreparation,modifier=Modifier.semantics {contentDescription="История и план экзаменов"}){StudyIcon(Glyph.PLAN,Modifier.size(22.dp))}
                IconButton(onClick=onProfile,modifier=Modifier.semantics {contentDescription="Личный кабинет"}) {
                    Surface(shape=CircleShape,color=colors.primaryContainer,contentColor=colors.onPrimaryContainer) {
                        Box(Modifier.size(32.dp),contentAlignment=Alignment.Center){
                            val value=name.trim().ifBlank {"T"}
                            Text(value.substring(0,value.offsetByCodePoints(0,1)).uppercase(),fontSize=14.sp,lineHeight=18.sp,fontWeight=FontWeight.SemiBold)
                        }
                    }
                }
            }
            HorizontalDivider(color=colors.outlineVariant.copy(alpha=.65f))
        }
    }
}

/** Compact visual bar; the whole item remains a >=48dp accessible tab. Insets belong to this bar only. */
@Composable internal fun StudyHomeNavigation(items: List<Pair<String,Glyph>>,selected: Int,onSelect: (Int)->Unit) {
    val colors=MaterialTheme.colorScheme
    Surface(color=colors.surface,contentColor=colors.onSurface) {
        Column {
            HorizontalDivider(color=colors.outlineVariant.copy(alpha=.65f))
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom+WindowInsetsSides.Horizontal))) {
                items.forEachIndexed {index,(label,icon)->
                    Column(Modifier.weight(1f).selectable(selected==index,role=Role.Tab,onClick={onSelect(index)})
                        .heightIn(min=64.dp).padding(horizontal=2.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Surface(shape=CircleShape,color=if(selected==index)colors.primaryContainer else Color.Transparent,contentColor=if(selected==index)colors.primary else colors.onSurfaceVariant) {
                            Box(Modifier.width(56.dp).height(28.dp),contentAlignment=Alignment.Center){StudyIcon(icon,Modifier.size(22.dp))}
                        }
                        Text(label,fontSize=12.sp,lineHeight=16.sp,fontWeight=if(selected==index)FontWeight.SemiBold else FontWeight.Normal,
                            color=if(selected==index)colors.primary else colors.onSurfaceVariant,textAlign=TextAlign.Center,maxLines=2)
                    }
                }
            }
        }
    }
}

@Composable internal fun StudyHomeFeed(name: String,sets: List<StudySet>,quizzes: List<Quiz>,study: StudyData,
    loading: Boolean,problems: List<String>,onOpen: (StudySet)->Unit,onCreate: ()->Unit,onRename: (StudySet)->Unit,
    onExport: (StudySet)->Unit,onArchive: (StudySet)->Unit,onRestore: (StudySet)->Unit,onPreparation: ()->Unit,
    onRetry: ()->Unit,onLegacy: ()->Unit) {
    var query by rememberSaveable {mutableStateOf("")}
    var trash by rememberSaveable {mutableStateOf(false)}
    val colors=MaterialTheme.colorScheme
    val shown=homeFilteredSets(sets,query,trash)
    val progress=homeProgress(study.cards,study.attempts)
    val exam=study.exams.filter {!it.date.isBefore(java.time.LocalDate.now())}.minByOrNull {it.date}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=16.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="greeting") {
            Column(Modifier.fillMaxWidth().padding(bottom=4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("Привет, $name 👋",fontSize=24.sp,lineHeight=30.sp,fontWeight=FontWeight.Bold)
                Text(exam?.let {it.title+" · "+examCountdown(it.date)} ?: "Твои материалы и подготовка",fontSize=13.sp,lineHeight=18.sp,color=colors.onSurfaceVariant)
            }
        }
        item(key="search"){HomeSearch(query,{query=it},trash,sets.count {it.archived},{trash=it})}
        progress?.let {value->item(key="progress") {HomeProgressCard(value,onPreparation)}}
        item(key="create"){HomeCreateCard(onCreate)}
        item(key="section") {
            Row(Modifier.fillMaxWidth().padding(top=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(trash)"Корзина" else "Мои учебные наборы",Modifier.weight(1f),fontSize=18.sp,lineHeight=24.sp,fontWeight=FontWeight.Bold)
                Text(shown.size.toString(),fontSize=13.sp,lineHeight=18.sp,color=colors.primary,fontWeight=FontWeight.SemiBold)
            }
        }
        if(trash)item(key="trash-back"){TextButton(onClick={trash=false}){Text("Вернуться к наборам")}}
        if(loading)item(key="loading"){StudyLoading("Открываем библиотеку")}
        if(problems.isNotEmpty())item(key="problems") {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {problems.forEach {StudyError(it)}
                Row {TextButton(onClick=onRetry){Text("Повторить")};TextButton(onClick=onLegacy){Text("Сохранённые тесты")}}}
        }
        if(!loading && problems.isEmpty() && shown.isEmpty())item(key="empty") {
            HomeSurface {Text(if(query.isNotBlank())"Ничего не найдено" else if(trash)"Корзина пуста" else "Здесь появятся твои наборы",fontSize=16.sp,lineHeight=22.sp,fontWeight=FontWeight.SemiBold)
                Text(if(query.isNotBlank())"Попробуй другое название" else "Создай набор и добавь материал для подготовки.",fontSize=13.sp,lineHeight=18.sp,color=colors.onSurfaceVariant)}
        }
        items(shown,key={it.id}) {set->HomeSetCard(set,quizzes,study,{onOpen(set)},{onRename(set)},{onExport(set)},{onArchive(set)},{onRestore(set)})}
    }
}

@Composable private fun HomeSurface(modifier: Modifier=Modifier,content: @Composable ColumnScope.()->Unit) {
    val colors=MaterialTheme.colorScheme
    Card(modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=colors.surface),
        border=BorderStroke(1.dp,colors.outlineVariant.copy(alpha=.7f)),elevation=CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
    }
}

@Composable private fun HomeSearch(query: String,onQuery: (String)->Unit,trash: Boolean,archived: Int,onTrash: (Boolean)->Unit) {
    var menu by remember {mutableStateOf(false)}
    val colors=MaterialTheme.colorScheme;val focus=LocalFocusManager.current
    Surface(shape=RoundedCornerShape(12.dp),color=colors.surface,border=BorderStroke(1.dp,colors.outlineVariant)) {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(start=12.dp,end=0.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Canvas(Modifier.size(20.dp)) {val stroke=1.7.dp.toPx();drawCircle(colors.onSurfaceVariant,size.minDimension*.28f,Offset(size.width*.4f,size.height*.4f),style=Stroke(stroke));drawLine(colors.onSurfaceVariant,Offset(size.width*.62f,size.height*.62f),Offset(size.width*.9f,size.height*.9f),stroke)}
            BasicTextField(query,onQuery,Modifier.weight(1f).padding(vertical=12.dp).semantics {contentDescription="Поиск учебных наборов"},
                textStyle=TextStyle(color=colors.onSurface,fontSize=14.sp,lineHeight=20.sp),singleLine=true,cursorBrush=SolidColor(colors.primary),
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={focus.clearFocus()}),
                decorationBox={inner->Box {if(query.isEmpty())Text("Поиск учебных наборов",fontSize=14.sp,lineHeight=20.sp,color=colors.onSurfaceVariant);inner()}})
            if(query.isNotEmpty())IconButton(onClick={onQuery("")},modifier=Modifier.semantics {contentDescription="Очистить поиск"}){Text("×",fontSize=22.sp)}
            Box {
                IconButton(onClick={menu=true},modifier=Modifier.semantics {contentDescription="Фильтр наборов и корзина"}) {
                    Canvas(Modifier.size(20.dp)) {val c=colors.onSurfaceVariant;val stroke=1.7.dp.toPx();drawLine(c,Offset(0f,size.height*.3f),Offset(size.width,size.height*.3f),stroke);drawLine(c,Offset(0f,size.height*.7f),Offset(size.width,size.height*.7f),stroke);drawCircle(colors.surface,size.width*.12f,Offset(size.width*.35f,size.height*.3f));drawCircle(c,size.width*.12f,Offset(size.width*.35f,size.height*.3f),style=Stroke(stroke));drawCircle(colors.surface,size.width*.12f,Offset(size.width*.7f,size.height*.7f));drawCircle(c,size.width*.12f,Offset(size.width*.7f,size.height*.7f),style=Stroke(stroke))}
                }
                DropdownMenu(menu,{menu=false}) {
                    DropdownMenuItem(text={Text(if(!trash)"✓ Активные наборы" else "Активные наборы")},onClick={menu=false;onTrash(false)})
                    DropdownMenuItem(text={Text((if(trash)"✓ " else "")+"Корзина ($archived)")},onClick={menu=false;onTrash(true)})
                }
            }
        }
    }
}

@Composable private fun HomeCreateCard(onCreate: ()->Unit) {
    val colors=MaterialTheme.colorScheme
    val dark=colors.surface.red+colors.surface.green+colors.surface.blue<1.5f
    val ink=if(dark)colors.onPrimaryContainer else Color.White
    val gradient=if(dark)listOf(colors.primaryContainer,colors.secondaryContainer) else listOf(Color(0xFF0061A6),colors.primary)
    Surface(onClick=onCreate,modifier=Modifier.fillMaxWidth().padding(vertical=4.dp),shape=RoundedCornerShape(16.dp),color=Color.Transparent,contentColor=ink) {
        Row(Modifier.background(Brush.horizontalGradient(gradient)).heightIn(min=104.dp).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Surface(shape=RoundedCornerShape(12.dp),color=ink.copy(alpha=.16f),contentColor=ink) {Box(Modifier.size(44.dp),contentAlignment=Alignment.Center){StudyIcon(Glyph.ADD)}}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("Создать новый набор",fontSize=16.sp,lineHeight=22.sp,fontWeight=FontWeight.Bold)
                Text("Тесты по теме и твоим материалам",fontSize=13.sp,lineHeight=18.sp)
            }
            Text("›",fontSize=24.sp,lineHeight=28.sp)
        }
    }
}

@Composable private fun HomeProgressCard(value: HomeProgress,onHistory: ()->Unit) {
    HomeSurface {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp),contentAlignment=Alignment.Center) {
                CircularProgressIndicator(progress={value.fraction},modifier=Modifier.fillMaxSize(),strokeWidth=4.dp)
                Text((value.fraction*100).toInt().toString()+"%",fontSize=12.sp,lineHeight=16.sp,fontWeight=FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f)) {Text(value.label,fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.SemiBold);Text("${value.completed} из ${value.total}",fontSize=13.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            TextButton(onClick=onHistory){Text("История",fontSize=13.sp,lineHeight=18.sp)}
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun HomeSetCard(set: StudySet,quizzes: List<Quiz>,study: StudyData,onOpen: ()->Unit,onRename: ()->Unit,onExport: ()->Unit,onArchive: ()->Unit,onRestore: ()->Unit) {
    val colors=MaterialTheme.colorScheme
    val banks=quizzes.filter {it.id in set.quizIds};val cards=WorkspaceIndex.cards(set,study);val attempts=WorkspaceIndex.attempts(set,study)
    val progress=homeProgress(cards,attempts);val last=attempts.maxByOrNull {it.completedAt}
    var menu by remember {mutableStateOf(false)}
    HomeSurface(Modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled=!set.archived,onClick=onOpen)) {
        Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Surface(color=colors.primaryContainer,contentColor=colors.onPrimaryContainer,shape=RoundedCornerShape(12.dp)) {Box(Modifier.size(40.dp),contentAlignment=Alignment.Center){StudyIcon(Glyph.LIBRARY,Modifier.size(22.dp))}}
            Column(Modifier.weight(1f).padding(top=2.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(set.title,fontSize=16.sp,lineHeight=21.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                val detail=when {set.materialIds.isNotEmpty()->"Материалов: "+set.materialIds.size;last!=null->"Занятие: "+displayDate(last.completedAt);set.notes.isNotBlank()->set.notes.lineSequence().firstOrNull().orEmpty();else->null}
                detail?.let {Text(it,fontSize=13.sp,lineHeight=18.sp,color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)}
            }
            Box {
                IconButton(onClick={menu=true},modifier=Modifier.size(48.dp).semantics {contentDescription="Действия набора "+set.title}){Text("⋮",fontSize=22.sp)}
                DropdownMenu(menu,{menu=false}) {
                    if(!set.archived) {
                        DropdownMenuItem(text={Text("Открыть")},onClick={menu=false;onOpen()})
                        DropdownMenuItem(text={Text("Переименовать")},onClick={menu=false;onRename()})
                        DropdownMenuItem(text={Text("Экспортировать ZIP")},onClick={menu=false;onExport()})
                        DropdownMenuItem(text={Text("В корзину")},onClick={menu=false;onArchive()})
                    } else DropdownMenuItem(text={Text("Восстановить")},onClick={menu=false;onRestore()})
                }
            }
        }
        progress?.let {value->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text(value.label,fontSize=12.sp,lineHeight=18.sp,color=colors.onSurfaceVariant);Text("${value.completed}/${value.total}",fontSize=12.sp,lineHeight=18.sp,color=colors.tertiary,fontWeight=FontWeight.SemiBold)}
            LinearProgressIndicator(progress={value.fraction},modifier=Modifier.fillMaxWidth().height(5.dp),color=colors.tertiary)
        }
        HorizontalDivider(color=colors.outlineVariant.copy(alpha=.65f))
        FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(Modifier.heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                StudyIcon(Glyph.FILE,Modifier.size(16.dp));Text(banks.sumOf {it.questions.size}.toString()+" вопр.",fontSize=13.sp,lineHeight=18.sp,color=colors.onSurfaceVariant)
                Spacer(Modifier.width(4.dp));StudyIcon(Glyph.CARDS,Modifier.size(16.dp));Text(cards.size.toString()+" карт.",fontSize=13.sp,lineHeight=18.sp,color=colors.onSurfaceVariant)
            }
            FilledTonalButton(onClick=if(set.archived)onRestore else onOpen,modifier=Modifier.heightIn(min=48.dp),shape=RoundedCornerShape(10.dp),contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp)) {
                Text(if(set.archived)"Восстановить" else "Учить  ›",fontSize=13.sp,lineHeight=18.sp,fontWeight=FontWeight.SemiBold)
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
