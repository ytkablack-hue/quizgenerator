package kz.quizgenerator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal enum class StudySection(val title: String) { TESTS("Мои тесты"), CARDS("Карточки"), MISTAKES("Ошибки"), PROGRESS("Прогресс"), EXAMS("Экзамены") }
internal data class ProgressSummary(val attempts: Int, val graded: Int, val correct: Int) {
    val percent: Int? get() = if (graded == 0) null else (100.0 * correct / graded).toInt()
    companion object { fun from(attempts: List<Attempt>) = ProgressSummary(attempts.size, attempts.sumOf { it.score.gradedTotal }, attempts.sumOf { it.score.correct }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PreparationHub(data: StudyData, quizzes: List<Quiz>, saveExams: (List<Exam>)->Boolean,
    library: @Composable () -> Unit, onCards: ()->Unit, onAi: (Attempt?)->Unit, onRepeat: (Quiz)->Unit) {
    var section by rememberSaveable { mutableStateOf(StudySection.TESTS) }
    Column(Modifier.fillMaxSize()) {
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            StudySection.entries.forEach { tab -> FilterChip(section==tab,{section=tab},label={Text(tab.title)}) }
        }
        Box(Modifier.weight(1f)) {
            when(section) {
                StudySection.TESTS -> library()
                StudySection.CARDS -> ScreenList {
                    item { SectionHeading("Карточки", "Сохранено: ${data.cards.size} · повторений: ${data.cards.sumOf { it.reviews }}") }
                    item { Button(onClick=onCards) { Text("Повторять и создавать карточки") } }
                    item { OutlinedButton(onClick={onAi(null)}) { Text("Сгенерировать карточки с ИИ") } }
                }
                StudySection.MISTAKES -> ScreenList {
                    item { SectionHeading("Работа над ошибками", "Ошибки из реальных сохранённых попыток") }
                    if(data.attempts.none { it.mistakes.isNotEmpty() }) item { Text("Пока нет ошибок в оцениваемых вопросах") }
                    items(data.attempts.filter { it.mistakes.isNotEmpty() }.asReversed(),key=Attempt::id) { attempt ->
                        StudyPanel {
                            Text(attempt.title,style=MaterialTheme.typography.titleLarge)
                            attempt.mistakes.forEach { mistake ->
                                Text(mistake.question,style=MaterialTheme.typography.titleMedium)
                                Text("Ваш ответ: ${mistake.given ?: "Пропущен"}")
                                Text("Правильно: ${mistake.correct}")
                                mistake.explanation?.let { Text(it) }
                            }
                            OutlinedButton(onClick={onAi(attempt)}) { Text("Объяснить с ИИ") }
                            quizzes.firstOrNull { it.id==attempt.quizId }?.let { quiz ->
                                TextButton(onClick={onRepeat(mistakeQuiz(quiz,attempt))}) { Text("Повторить проблемные вопросы") }
                            }
                        }
                    }
                }
                StudySection.PROGRESS -> ScreenList {
                    val summary=ProgressSummary.from(data.attempts)
                    item { SectionHeading("Прогресс", "Результаты только с этого устройства") }
                    item { StudyPanel {
                        Text("Пройдено тестов: ${summary.attempts}")
                        Text(summary.percent?.let { "Правильных ответов: $it% (${summary.correct} из ${summary.graded})" } ?: "Пока нет оцениваемых ответов")
                        summary.percent?.let { LinearProgressIndicator(progress={it/100f},modifier=Modifier.fillMaxWidth()) }
                    } }
                    val weak=data.attempts.groupBy(Attempt::title).mapValues { (_, attempts) -> attempts.sumOf { it.mistakes.size } }.filterValues { it>0 }.entries.sortedByDescending { it.value }.take(5)
                    if(weak.isNotEmpty()) item { StudyPanel { Text("Темы для повторения",style=MaterialTheme.typography.titleMedium);weak.forEach { Text("${it.key}: ошибок и пропусков ${it.value}") } } }
                    item { Text("Динамика: последние 10 попыток",style=MaterialTheme.typography.titleLarge) }
                    items(data.attempts.sortedByDescending(Attempt::completedAt).take(10),key=Attempt::id) { attempt ->
                        StudyPanel {
                            Text("${displayDate(attempt.completedAt)} · ${attempt.title}")
                            Text("${attempt.score.correct} из ${attempt.score.gradedTotal}")
                            if(attempt.score.gradedTotal>0) LinearProgressIndicator(progress={attempt.score.correct.toFloat()/attempt.score.gradedTotal},modifier=Modifier.fillMaxWidth())
                        }
                    }
                    item { Text("История: ${data.attempts.size} попыток",style=MaterialTheme.typography.titleLarge) }
                    items(data.attempts.sortedByDescending(Attempt::completedAt),key={"history:"+it.id}) { attempt ->
                        StudyPanel { Text("${displayDate(attempt.completedAt)} · ${attempt.title}"); Text("${attempt.score.correct} / ${attempt.score.gradedTotal}") }
                    }
                }
                StudySection.EXAMS -> PreparationScreen(data,quizzes,saveExams)
            }
        }
    }
}

internal fun mistakeQuiz(quiz: Quiz, attempt: Attempt): Quiz {
    val texts=attempt.mistakes.map(Mistake::question).toSet()
    val selected=quiz.questions.filter { it.text in texts }
    return if(selected.isEmpty()) quiz else quiz.copy(questions=selected,title=quiz.title+" · Повторение ошибок")
}
