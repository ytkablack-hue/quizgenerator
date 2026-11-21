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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StartScreen(quiz: Quiz, minutes: Int, onMinutes: (Int) -> Unit,
                         onEdit: () -> Unit, onStart: () -> Unit, onCards: () -> Unit) {
    ScreenList {
        item { SectionHeading(quiz.title, "Вопросов: ${quiz.questions.size}" + if (quiz.generatedByAi) " · Создано ИИ" else "") }
        item {
            StudyPanel(tone = PanelTone.LAVENDER) {
                Text("Время на тест", style = MaterialTheme.typography.titleMedium)
                TimerSelector(minutes, onMinutes)
            }
        }
        item {
            val inferred = quiz.questions.count { it.evidence == AnswerEvidence.INFERRED }
            if (inferred > 0) Text("Предположительных ключей: $inferred. Они исключены из оценки до ручного подтверждения.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart) { Text("Начать тест") }
                OutlinedButton(onClick = onEdit) { Text("Редактировать") }
                OutlinedButton(onClick = onCards) { Text("Создать карточки") }
            }
        }
        items(quiz.questions, key = { it.id }) { question ->
            StudyPanel {
                Text(question.text, style = MaterialTheme.typography.titleMedium)
                Text(question.evidence.label + (question.correctIndex?.let { " · ${question.options[it]}" } ?: ""))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RunScreen(quiz: Quiz, index: Int, answer: Int?, seconds: Int?, hints: Boolean,
                       onAnswer: (Int) -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
                       onFinish: () -> Unit) {
    val question = quiz.questions[index]
    val last = index == quiz.questions.lastIndex
    val controls = @Composable {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = index > 0, onClick = onPrevious) { Text("Назад") }
            Button(onClick = if (last) onFinish else onNext) { Text(if (last) "Завершить тест" else "Далее") }
            if (!last) TextButton(onClick = onFinish) { Text("Завершить") }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 360.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            key(question.id) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text(quiz.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                        StudyPanel(tone = PanelTone.LAVENDER) {
                            Text(question.text, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                    item {
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            question.options.forEachIndexed { option, label ->
                                val selected = answer == option
                                val colors = MaterialTheme.colorScheme
                                Surface(
                                    modifier = Modifier.fillMaxWidth()
                                        .selectable(selected, role = Role.RadioButton, onClick = { onAnswer(option) }),
                                    color = if (selected) colors.secondaryContainer else colors.surface,
                                    contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
                                    shape = MaterialTheme.shapes.medium,
                                    border = BorderStroke(1.dp, if (selected) colors.secondary else colors.outlineVariant)
                                ) {
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        RadioButton(selected, onClick = null)
                                        Text(label, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        }
                    }
                    if (hints && !question.explanation.isNullOrBlank()) item {
                        var show by rememberSaveable(question.id) { mutableStateOf(false) }
                        TextButton(onClick = { show = !show }) { Text(if (show) "Скрыть подсказку" else "Показать подсказку") }
                        if (show) StudyPanel(tone = PanelTone.SKY) {
                            Text(question.explanation)
                            question.sourceExcerpt?.let { Text("Цитата: $it") }
                        }
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
internal fun ResultsScreen(quiz: Quiz, answers: List<Int?>, expired: Boolean, showAnswers: Boolean,
                           repeat: () -> Unit, library: () -> Unit) {
    val score = QuizScorer.score(quiz, answers)
    val percent = if (score.gradedTotal > 0) (100f * score.correct / score.gradedTotal).roundToInt() else null
    val missed = quiz.questions.indices.filter { quiz.questions[it].correctIndex != null && quiz.questions[it].evidence in setOf(AnswerEvidence.SOURCE, AnswerEvidence.MANUAL) && answers[it] != quiz.questions[it].correctIndex }
    ScreenList {
        item { SectionHeading("Результат", quiz.title) }
        item {
            StudyPanel(tone = PanelTone.LAVENDER) {
                Text(percent?.let { "$it%" } ?: "Нет оцениваемых вопросов", style = MaterialTheme.typography.headlineMedium)
                Text("Правильных ответов: ${score.correct} из ${score.gradedTotal}")
                percent?.let { LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth()) }
                Text("Ошибок: ${score.incorrect} · Пропущено: ${score.skipped}")
                if (score.ungraded > 0) Text("Без подтверждённого ключа: ${score.ungraded}")
                if (expired) Text("Время вышло. Тест завершён автоматически.")
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = repeat) { Text("Повторить тест") }
                OutlinedButton(onClick = library) { Text("Вернуться в библиотеку") }
            }
        }
        item { SectionHeading("Работа над ошибками") }
        if (missed.isEmpty()) item { StudyPanel(tone = PanelTone.SKY) { Text("Среди оцениваемых вопросов нет ошибок или пропусков.") } }
        items(missed, key = { quiz.questions[it].id }) { index ->
            val question = quiz.questions[index]
            StudyPanel {
                Text(question.text, style = MaterialTheme.typography.titleMedium)
                Text("Твой ответ · ${answers[index]?.let(question.options::get) ?: "Пропущен"}")
                if (showAnswers) {
                    question.correctIndex?.let { Text("Правильный ответ · ${question.options[it]}") }
                    question.explanation?.let { Text(it) }
                }
            }
        }
    }
}
