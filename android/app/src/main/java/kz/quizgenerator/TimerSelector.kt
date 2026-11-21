package kz.quizgenerator

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimerSelector(minutes: Int, onMinutes: (Int) -> Unit) {
    var custom by rememberSaveable(minutes) { mutableStateOf(if (minutes in QuizTimer.presets) "" else minutes.toString()) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuizTimer.presets.forEach { time ->
            FilterChip(time == minutes, { onMinutes(time) }, label = { Text(if (time == 0) "Без ограничения" else "$time мин") })
        }
    }
    OutlinedTextField(custom, { custom = it.filter(Char::isDigit).take(3) },
        label = { Text("Своё время: 1–180 минут") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        isError = custom.isNotEmpty() && custom.toIntOrNull() !in 1..QuizTimer.MAX_MINUTES)
    OutlinedButton(enabled = custom.toIntOrNull() in 1..QuizTimer.MAX_MINUTES,
        onClick = { custom.toIntOrNull()?.takeIf { it in 1..QuizTimer.MAX_MINUTES }?.let(onMinutes) }) {
        Text("Применить своё время")
    }
    Text(if (minutes == 0) "Тест без ограничения времени" else "Выбрано: $minutes мин")
}
