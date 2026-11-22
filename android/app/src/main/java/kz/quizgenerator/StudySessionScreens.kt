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
            Text("Сложность задана исходным банком. Генерация по теме, тексту и документу настраивается отдельно (Gemini: до 25).",style=MaterialTheme.typography.bodySmall)
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

        item {FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick=onEdit){Text("Редактировать банк")};OutlinedButton(onClick=onCards){Text("Создать карточки")}}}
    }
    StudyPrimary("Начать тестирование",enabled=count.toIntOrNull() in 1..quiz.questions.size) {
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
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = index > 0, onClick = onPrevious) { Text("← Назад") }
            Button(onClick = if (last) onFinish else onNext) { Text(if (last) "Завершить тест" else "Далее") }
            if (!last && answer.isEmpty()) TextButton(onClick=onNext){Text("Пропустить")}
            TextButton(onClick = onFinish) { Text("Завершить") }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 360.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            key(question.id) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Row(verticalAlignment=Alignment.CenterVertically) {Text(if(examMode)"Экзамен" else "Обучение",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                            IconToggleButton(checked=bookmarked,onCheckedChange={onBookmark()},modifier=Modifier.semantics {contentDescription=if(bookmarked)"Убрать закладку" else "Отметить сложный вопрос"}) {StudyIcon(Glyph.BOOKMARK)}}
                        Text(quiz.title,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(enabled=markedCount>0,onClick=onNextBookmark){Text("К закладкам ($markedCount)")}
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Вопрос ${index + 1} из ${quiz.questions.size}")
                            seconds?.let { Text(String.format(Locale.ROOT, "%02d:%02d", it / 60, it % 60),
                                color = if (QuizTimer.warning(it)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
                        }
                        if (seconds != null && QuizTimer.warning(seconds)) Text("Осталось меньше минуты. Ответы сохранятся автоматически",
                            color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { (index + 1f) / quiz.questions.size }, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        StudyPanel {
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
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Surface(shape=androidx.compose.foundation.shape.CircleShape,color=if(selected)colors.primary else colors.primaryContainer,contentColor=if(selected)colors.onPrimary else colors.onPrimaryContainer) {
                                            Box(Modifier.size(36.dp),contentAlignment=Alignment.Center){Text(if(selected)"✓" else if(option<6)('А'.code+option).toChar().toString() else (option+1).toString(),style=MaterialTheme.typography.titleMedium)}
                                        }
                                        Text(label, style = MaterialTheme.typography.bodyLarge)
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
                        OutlinedButton(enabled=answer.isNotEmpty(),onClick={feedback=true}){Text("Проверить выбранный ответ")}
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
        item {StudyPanel(tone=PanelTone.SKY) {
            Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {Box(Modifier.size(144.dp),contentAlignment=Alignment.Center) {
                CircularProgressIndicator(progress={(percent ?: 0)/100f},modifier=Modifier.fillMaxSize(),strokeWidth=10.dp)
                Column(horizontalAlignment=Alignment.CenterHorizontally){Text(percent?.let {"$it%"} ?: "—",style=MaterialTheme.typography.headlineLarge);Text("по ключам",style=MaterialTheme.typography.bodySmall)}
            }}
            Text("Время: "+StudySessionPolicy.durationLabel(durationMillis))
            if(percent==null)Text("Нет вопросов с подтверждённым ключом")
            Text("Верно: "+score.correct+" · Ошибок: "+score.incorrect+" · Пропущено: "+score.skipped)
            Text("Без подтверждённого ключа: "+score.ungraded)
            percent?.let {LinearProgressIndicator(progress={it/100f},modifier=Modifier.fillMaxWidth())}
            if(expired)Text("Время вышло. Тест завершён автоматически, ответы сохранены.")
        }}
        item {FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick=repeat){Text("Повторить тест")}
            if(repeatMistakes!=null && score.incorrect+score.skipped>0)OutlinedButton(onClick=repeatMistakes){Text("Повторить ошибки ("+(score.incorrect+score.skipped)+")")}
            if(onCardsFromMistakes!=null && score.incorrect+score.skipped>0)OutlinedButton(onClick=onCardsFromMistakes){Text("Карточки из ошибок")}
            repeatBookmarks?.let {OutlinedButton(onClick=it){Text("Повторить закладки")}}
            TextButton(onClick=library){Text("Вернуться к материалам")}
        }}
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
                    q.explanation?.let {Text(it)}
                }
            }
        }
    }
}
