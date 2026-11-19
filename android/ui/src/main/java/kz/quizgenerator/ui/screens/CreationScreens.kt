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

@Composable
fun CreateLibraryScreen(quizzes: List<QuizRow>, query: String, onQueryChange: (String) -> Unit,
                        onCreate: () -> Unit, onImport: () -> Unit, onOpen: (String) -> Unit,
                        onEdit: (String) -> Unit, onDelete: (String) -> Unit, onCards: () -> Unit,
                        alphabetical: Boolean, onSort: () -> Unit, error: String?) {
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SteamHeading("Мои тесты")
        SteamButton("⊞  Создать тест", onClick = onCreate)
        SteamButton("⇧  Импорт из файла", tone = SteamTone.PALE, onClick = onImport)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {}) { Text("Тесты") }
            OutlinedButton(onClick = onCards) { Text("Карточки") }
        }
        Row(Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⌕", color = SteamMuted); Spacer(Modifier.width(8.dp))
            BasicTextField(query, onQueryChange, Modifier.weight(1f), singleLine = true, decorationBox = { inner -> Box { if (query.isEmpty()) Text("Поиск по названию", color = SteamMuted); inner() } })
        }
        TextButton(onClick = onSort) { Text(if (alphabetical) "Сортировка: по названию" else "Сортировка: недавно изменённые") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val results = quizzes.filter { it.title.contains(query.trim(), true) }.let { if (alphabetical) it.sortedBy { row -> row.title.lowercase() } else it.sortedByDescending { row -> row.date } }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (results.isEmpty()) item { SteamCard { Text(if (query.isBlank()) "Пока нет тестов" else "Ничего не найдено", fontWeight = FontWeight.Bold); if (query.isBlank()) SteamButton("Создать тест", onClick = onCreate) } }
            items(results, key = { it.id }) { row ->
                SteamCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = SteamBlue, shape = RoundedCornerShape(6.dp)) { Text("▤", Modifier.padding(10.dp), color = Color.White) }
                        Column(Modifier.weight(1f)) { Text(row.title, fontWeight = FontWeight.Bold); Text("Вопросов: ${row.questionCount} · Карточек: ${row.cards}", color = SteamMuted) }
                        var menu by remember(row.id) { mutableStateOf(false) }
                        Box { TextButton(onClick = { menu = true }) { Text("⋮") }; DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Редактировать") }, onClick = { menu = false; onEdit(row.id) })
                            if (!row.readOnly) DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; onDelete(row.id) })
                        } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(Modifier.weight(1f).clickable { onOpen(row.id) }, color = SteamGreen, shape = RoundedCornerShape(7.dp)) { Text("▶  Пройти", Modifier.padding(10.dp), color = Color.White, fontWeight = FontWeight.Bold) }
                        Surface(Modifier.weight(1f).clickable { onCards() }, color = Color(0xFFD1DCE8), shape = RoundedCornerShape(7.dp)) { Text("▣  Карточки", Modifier.padding(10.dp), color = SteamText, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}
