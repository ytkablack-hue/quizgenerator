package kz.quizgenerator.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kz.quizgenerator.ui.components.*
import kz.quizgenerator.ui.theme.*

data class QuizRow(val id: String, val title: String, val questionCount: Int, val cards: Int, val date: Long, val readOnly: Boolean)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateLibraryScreen(quizzes: List<QuizRow>, query: String, onQueryChange: (String) -> Unit,
                        onCreate: () -> Unit, onImport: () -> Unit, onOpen: (String) -> Unit,
                        onEdit: (String) -> Unit, onDelete: (String) -> Unit, onCards: () -> Unit,
                        alphabetical: Boolean, onSort: () -> Unit, error: String?, onQuizCards: (String) -> Unit = { onCards() }) {
    val results = quizzes.filter { it.title.contains(query.trim(), true) }.let { if (alphabetical) it.sortedBy { row -> row.title.lowercase() } else it.sortedByDescending { row -> row.date } }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SteamHeading("Мои тесты")
        SteamButton("⊞  Создать тест", onClick = onCreate)
        SteamButton("⇧  Импорт из файла", tone = SteamTone.PALE, onClick = onImport)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(color = SteamPaleGreen, shape = RoundedCornerShape(7.dp)) { Text("Тесты", Modifier.padding(12.dp)) }
            OutlinedButton(onClick = onCards) { Text("Карточки") }
        }
        SteamField("Поиск по названию", query, onQueryChange, Modifier.fillMaxWidth())
        TextButton(onClick = onSort) { Text(if (alphabetical) "Сортировка: по названию" else "Сортировка: недавно изменённые") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
            if (results.isEmpty()) item { SteamCard { Text(if (query.isBlank()) "Пока нет тестов" else "Ничего не найдено", fontWeight = FontWeight.Bold); if (query.isBlank()) SteamButton("Создать тест", onClick = onCreate) } }
            items(results, key = { it.id }) { row ->
                SteamCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = SteamBlue, shape = RoundedCornerShape(6.dp)) { Text("▤", Modifier.padding(10.dp), color = MaterialTheme.colorScheme.onSecondary) }
                        Column(Modifier.weight(1f)) { Text(row.title, fontWeight = FontWeight.Bold); Text("Вопросов: ${row.questionCount} · Карточек: ${row.cards}", color = SteamMuted) }
                        var menu by remember(row.id) { mutableStateOf(false) }
                        Box { TextButton(onClick = { menu = true }) { Text("⋮") }; DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Редактировать") }, onClick = { menu = false; onEdit(row.id) })
                            if (!row.readOnly) DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; onDelete(row.id) })
                        } }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onOpen(row.id) }) { Text("▶  Пройти") }
                        OutlinedButton(onClick = { onQuizCards(row.id) }) { Text("▣  Карточки") }
                    }
                }
            }
    }
}
