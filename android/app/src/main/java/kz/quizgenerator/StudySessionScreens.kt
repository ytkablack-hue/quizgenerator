package kz.quizgenerator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun StartScreen(quiz: Quiz,settings: StudySettings,
    onEdit: ()->Unit,onStart: (StudySettings)->Boolean,onCards: ()->Unit,onSource: ()->Unit) {
    var error by remember {mutableStateOf<String?>(null)}
    var count by rememberSaveable(quiz.id) {mutableStateOf(if(settings.limit==0)quiz.questions.size.toString() else settings.limit.coerceAtMost(quiz.questions.size).toString())}
    var value by rememberSaveable(quiz.id,stateSaver=listSaver<StudySettings,Any>(
        save={listOf(it.shuffleQuestions,it.shuffleOptions,it.showHints,it.showAnswers,it.minutes,it.examMode)},
        restore={settings.copy(shuffleQuestions=it[0] as Boolean,shuffleOptions=it[1] as Boolean,showHints=it[2] as Boolean,showAnswers=it[3] as Boolean,minutes=it[4] as Int,examMode=it[5] as Boolean)})) {mutableStateOf(settings)}
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    ScreenList(Modifier.weight(1f)) {
        item {SectionHeading("Настройка теста",quiz.title)}
        item {SectionHeading("Источник вопросов");StudyPanel {Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {StudyIcon(Glyph.FILE);Column(Modifier.weight(1f)){Text(quiz.title,style=MaterialTheme.typography.titleMedium);Text(quiz.questions.size.toString()+" вопросов в локальном банке")};TextButton(onClick=onSource){Text("Сменить")}};Text(if(quiz.generatedByAi)"Ранее создано ИИ; предположения требуют подтверждения" else "Готовые вопросы из вашей библиотеки")}}
        item {SectionHeading("Количество вопросов");StudyPanel {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {(listOf(10,20,30).filter {it<quiz.questions.size}+quiz.questions.size).distinct().forEach {n->FilterChip(count.toIntOrNull()==n,{count=n.toString()},label={Text(if(n==quiz.questions.size)"Все ($n)" else n.toString())})}}
            OutlinedTextField(count,{count=it.filter(Char::isDigit).take(4)},label={Text("От 1 до "+quiz.questions.size)},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
            Text("Вопросы берутся из выбранного банка без изменения содержания.",style=MaterialTheme.typography.bodySmall)
        }}
        item {SectionHeading("Режим прохождения");FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(!value.examMode,{value=value.copy(examMode=false)},label={Text("Обучение")})
            FilterChip(value.examMode,{value=value.copy(examMode=true,minutes=value.minutes.takeIf {it>0} ?: 30)},label={Text("Экзамен")})
        };Text(if(value.examMode)"Без подсказок и раскрытия ключей до завершения. Настройки фиксируются на всю попытку." else "Подсказки и проверка подтверждённого ответа по вашему действию.")}
        item {StudyPanel {
            Text("Параметры прохождения",style=MaterialTheme.typography.titleMedium)
            SessionSwitch("Перемешивать вопросы",value.shuffleQuestions){value=value.copy(shuffleQuestions=it)}
            SessionSwitch("Перемешивать варианты",value.shuffleOptions){value=value.copy(shuffleOptions=it)}
            if(!value.examMode)SessionSwitch("Разрешить подсказки",value.showHints){value=value.copy(showHints=it)}
            SessionSwitch("Разбор после завершения",value.showAnswers){value=value.copy(showAnswers=it)}
            Text("Таймер сессии");TimerSelector(value.minutes){value=value.copy(minutes=it)}
        }}
        val unverified=quiz.questions.count {!it.evidence.confirmed || it.correctIndices.isEmpty()}
        if(unverified>0)item {Text("Без подтверждённого ключа: $unverified. Эти вопросы не входят в строгую оценку.")}
        error?.let {item {StudyError(it)}}

        item {FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){StitchOutlinedButton(onClick=onEdit){Text("Редактировать банк")};StitchOutlinedButton(onClick=onCards){Text("Создать карточки")}}}
    }
    StudyPrimary("Начать тестирование (${count.toIntOrNull() ?: 0}) →",enabled=count.toIntOrNull() in 1..quiz.questions.size) {
        val n=count.toIntOrNull() ?: return@StudyPrimary
        if(!onStart(value.copy(limit=if(n==quiz.questions.size)0 else n)))error="Не удалось сохранить настройки. Попробуйте снова"
    }
    }
}
@Composable private fun SessionSwitch(label: String,checked: Boolean,change: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Text(label,Modifier.weight(1f));Switch(checked,change)}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RunScreen(quiz: Quiz, index: Int, answer: Set<Int>, seconds: Int?, hints: Boolean,
                       onAnswer: (Int) -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
                       onFinish: () -> Unit,examMode: Boolean=false,bookmarked: Boolean=false,onBookmark: ()->Unit={},markedCount: Int=0,onNextBookmark: ()->Unit={}) {
    val question = quiz.questions[index]
    val last = index == quiz.questions.lastIndex
    var feedback by rememberSaveable(question.id,answer.toString()) {mutableStateOf(false)}
    val controls = @Composable {
        Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            StitchOutlinedButton(enabled=index>0,onClick=onPrevious,modifier=Modifier.weight(1f)){Text("← Назад")}
            if(!last)TextButton(enabled=answer.isEmpty(),onClick=onNext,modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text("Пропустить")}
            StitchButton(onClick=if(last)onFinish else onNext,modifier=Modifier.weight(1f)){Text(if(last)"Завершить" else "Далее →")}
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 360.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            key(question.id) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                            TextButton(onClick=onFinish){Text("Завершить")}
                            StitchPill(seconds?.let {String.format(Locale.ROOT,"%02d:%02d",it/60,it%60)} ?: if(examMode)"Экзамен" else "Обучение")
                            IconToggleButton(checked=bookmarked,onCheckedChange={onBookmark()},modifier=Modifier.semantics {contentDescription=if(bookmarked)"Убрать закладку" else "Отметить сложный вопрос"}){StudyIcon(Glyph.BOOKMARK)}
                        }
                        StudyPanel {
                            Text("Вопрос ${index+1} из ${quiz.questions.size}",style=MaterialTheme.typography.titleLarge)
                            LinearProgressIndicator(progress={(index+1f)/quiz.questions.size},modifier=Modifier.fillMaxWidth().height(6.dp))
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                                TextButton(enabled=markedCount>0,onClick=onNextBookmark){Text("На повтор: $markedCount")}
                                Text("Осталось ${quiz.questions.size-index-1}",style=MaterialTheme.typography.labelMedium)
                            }
                        }
                        if(seconds!=null && QuizTimer.warning(seconds))Text("Осталось меньше минуты. Ответы сохраняются",color=MaterialTheme.colorScheme.error,modifier=Modifier.semantics {liveRegion=LiveRegionMode.Polite})
                    }
                    item {
                        StudyPanel {
                            Text(quiz.title,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                            Text(question.text, style = MaterialTheme.typography.headlineSmall)
                            if(question.isMultiAnswer)Text("Выберите все правильные варианты")
                        }
                    }
                    item {
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            question.options.forEachIndexed { option, label ->
                                val selected = option in answer
                                val colors = MaterialTheme.colorScheme
                                Surface(
                                    modifier = Modifier.fillMaxWidth()
                                        .selectable(selected, role = if(question.isMultiAnswer)Role.Checkbox else Role.RadioButton, onClick = { onAnswer(option) }),
                                    color = if (selected) colors.secondaryContainer else colors.surface,
                                    contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
                                    shape = MaterialTheme.shapes.medium,
                                    border = BorderStroke(if(selected)2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant)
                                ) {
                                    Row(Modifier.heightIn(min=56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Surface(shape=androidx.compose.foundation.shape.CircleShape,color=if(selected)colors.primary else colors.primaryContainer,contentColor=if(selected)colors.onPrimary else colors.onPrimaryContainer) {
                                            Box(Modifier.size(36.dp),contentAlignment=Alignment.Center){Text(if(option<5)listOf("А","Б","В","Г","Д")[option] else (option+1).toString(),style=MaterialTheme.typography.titleMedium)}
                                        }
                                        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge,fontWeight=if(selected)androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium)
                                        if(selected)Surface(shape=androidx.compose.foundation.shape.CircleShape,color=colors.secondaryContainer,border=BorderStroke(1.dp,colors.primary)){Box(Modifier.size(22.dp),contentAlignment=Alignment.Center){Text("✓",color=colors.primary,style=MaterialTheme.typography.labelSmall)}}
                                    }
                                }
                            }
                        }
                    }
                    if (StudySessionPolicy.hints(examMode,hints) && !question.explanation.isNullOrBlank()) item {
                        var show by rememberSaveable(question.id) { mutableStateOf(false) }
                        TextButton(onClick = { show = !show }) { Text(if (show) "Скрыть подсказку" else "Показать подсказку") }
                        if (show) StudyPanel(tone = PanelTone.SKY) {
                            Text(question.explanation)
                            question.sourceExcerpt?.let { Text("Цитата: $it") }
                        }
                    }
                    if(StudySessionPolicy.feedback(examMode,question))item {
                        StitchOutlinedButton(enabled=answer.isNotEmpty(),onClick={feedback=true}){Text("Проверить выбранный ответ")}
                        if(feedback)StudyPanel(tone=PanelTone.SKY){Text(if(answer==question.correctIndices.toSet())"Верно" else "Нужно повторить");Text("Ключ: "+question.correctIndices.joinToString {question.options[it]});question.explanation?.let {Text(it)}}
                    }
                    if (compact) item { controls() }
                }
            }
            if (!compact) controls()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ResultsScreen(quiz: Quiz,answers: List<Set<Int>>,expired: Boolean,showAnswers: Boolean,
    repeat: ()->Unit,library: ()->Unit,repeatMistakes: (() -> Unit)?=null,durationMillis: Long?=null,onCardsFromMistakes: (() -> Unit)?=null,repeatBookmarks: (() -> Unit)?=null) {
    val score=QuizScorer.scoreSelections(quiz,answers)
    val percent=if(score.gradedTotal>0)(100f*score.correct/score.gradedTotal).roundToInt() else null
    var errorsOnly by rememberSaveable(quiz.id) {mutableStateOf(false)}
    val results=quiz.questions.indices.filter {i->!errorsOnly || StudyResultRules.outcome(quiz.questions[i],answers[i]) in setOf(QuestionOutcome.INCORRECT,QuestionOutcome.SKIPPED)}
    ScreenList {
        item {SectionHeading("Результат",quiz.title)}
        item {StudyPanel {
            Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {Box(Modifier.size(132.dp),contentAlignment=Alignment.Center) {
                CircularProgressIndicator(progress={(percent ?: 0)/100f},modifier=Modifier.fillMaxSize(),strokeWidth=12.dp,trackColor=MaterialTheme.colorScheme.primaryContainer)
                Column(horizontalAlignment=Alignment.CenterHorizontally){Text(percent?.let {"$it%"} ?: "—",style=MaterialTheme.typography.displayLarge);Text("по ключам",style=MaterialTheme.typography.labelSmall)}
            }}
            Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){StitchPill(if(percent==null)"Нет подтверждённых ключей" else "Результат сохранён")}
            Text(quiz.title,style=MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                StitchMetric("Верно",score.correct.toString(),Modifier.weight(1f));StitchMetric("Ошибки",score.incorrect.toString(),Modifier.weight(1f));StitchMetric("Время",StudySessionPolicy.durationLabel(durationMillis),Modifier.weight(1f))
            }
            if(score.skipped>0 || score.ungraded>0)Text("Пропущено: ${score.skipped} · Без ключа: ${score.ungraded}",style=MaterialTheme.typography.bodySmall)
            if(expired)Text("Время вышло. Ответы сохранены.")
        }}
        if(repeatMistakes!=null && score.incorrect+score.skipped>0)item {StudyPrimary("Повторить ошибки (${score.incorrect+score.skipped}) →",onClick=repeatMistakes)}
        item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            StitchOutlinedButton(onClick=repeat,modifier=Modifier.weight(1f)){Text("Пройти заново")}
            if(onCardsFromMistakes!=null && score.incorrect+score.skipped>0)StitchOutlinedButton(onClick=onCardsFromMistakes,modifier=Modifier.weight(1f)){Text("Создать карточки")}
        }}
        repeatBookmarks?.let {item {TextButton(onClick=it){Text("Повторить закладки")}}}
        item {TextButton(onClick=library){Text("← К материалам")}}
        item {FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {FilterChip(!errorsOnly,{errorsOnly=false},label={Text("Все вопросы")});FilterChip(errorsOnly,{errorsOnly=true},label={Text("Ошибки и пропуски")})}}
        if(results.isEmpty())item {StudyPanel {Text("Среди оцениваемых вопросов нет ошибок и пропусков.")}}
        items(results,key={quiz.questions[it].id}) {index->
            val q=quiz.questions[index];val outcome=StudyResultRules.outcome(q,answers[index])
            StudyPanel(tone=if(outcome==QuestionOutcome.CORRECT)PanelTone.SKY else PanelTone.PLAIN) {
                Text("Вопрос "+(index+1)+" · "+outcome.label,style=MaterialTheme.typography.labelLarge)
                Text(q.text,style=MaterialTheme.typography.titleMedium)
                Surface(color=if(outcome==QuestionOutcome.INCORRECT || outcome==QuestionOutcome.SKIPPED)MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,shape=MaterialTheme.shapes.small) {Text("Ваш ответ: "+answers[index].sorted().joinToString {q.options[it]}.ifBlank {"Не выбран"},Modifier.fillMaxWidth().padding(12.dp))}
                if(showAnswers && q.correctIndices.isNotEmpty()) {
                    Surface(color=if(q.evidence.confirmed)MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,contentColor=if(q.evidence.confirmed)MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,shape=MaterialTheme.shapes.small){Text((if(q.evidence.confirmed)"Подтверждённый ключ: " else "Предположение, не ключ: ")+q.correctIndices.joinToString {q.options[it]},Modifier.fillMaxWidth().padding(12.dp))}
                    Text(q.evidence.label)
                    q.explanation?.let {StudyPanel(tone=PanelTone.LAVENDER){Text("Объяснение",style=MaterialTheme.typography.labelLarge);Text(it,style=MaterialTheme.typography.bodyMedium)}}
                }
            }
        }
    }
}
