package kz.quizgenerator

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kz.quizgenerator.ui.screens.CardRow
import kz.quizgenerator.ui.screens.CardEditorScreen
import kz.quizgenerator.ui.screens.FlashcardsScreen
import java.util.UUID

internal object QuizCards {
    fun create(quiz: Quiz, existing: List<Flashcard>): List<Flashcard> {
        val known = existing.mapNotNull(Flashcard::sourceKey).toSet()
        return quiz.questions.filter { it.correctIndex != null && it.evidence != AnswerEvidence.UNKNOWN }
            .mapNotNull { question ->
                val source = quiz.id + ":" + AnswerPolicy.fingerprint(question) + ":" + question.correctIndex
                if (source in known) null else Flashcard(
                    UUID.randomUUID().toString(), question.text, question.options[question.correctIndex!!],
                    quiz.generatedByAi, sourceKey = source, sourceQuizId = quiz.id, evidence = question.evidence
                )
            }
    }

    fun rate(card: Flashcard, rating: String): Flashcard {
        require(rating in setOf("hard", "medium", "easy"))
        return card.copy(reviews = card.reviews + 1, lastRating = rating)
    }
}

@Composable
internal fun CoinFlashcardsScreen(cards: List<Flashcard>, save: (List<Flashcard>) -> Boolean, onBack: () -> Unit) {
    var form by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var front by rememberSaveable { mutableStateOf("") }
    var back by rememberSaveable { mutableStateOf("") }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    var discard by rememberSaveable { mutableStateOf(false) }
    fun close() { form = false; editingId = null; front = ""; back = "" }
    fun leave() {
        val original = cards.firstOrNull { it.id == editingId }
        if (front != original?.front.orEmpty() || back != original?.back.orEmpty()) discard = true else close()
    }
    BackHandler(form) { leave() }
    if (discard) StudyConfirmation("Отменить правки?", "Введённый текст карточки не сохранится.", "Отменить",
        { discard = false }) { discard = false; close() }
    cards.firstOrNull { it.id == removing }?.let { target ->
        StudyConfirmation("Удалить карточку?", target.front, "Удалить", { removing = null }) {
            if (save(cards.filterNot { it.id == target.id })) removing = null
        }
    }
    if (form) CardEditorScreen(front, back, null, { front = it.take(65_536) }, { back = it.take(65_536) }, {
        val original = cards.firstOrNull { it.id == editingId }
        val card = original?.copy(front = front.trim(), back = back.trim(), evidence = AnswerEvidence.MANUAL)
            ?: Flashcard(UUID.randomUUID().toString(), front.trim(), back.trim(), evidence = AnswerEvidence.MANUAL)
        if (save(if (original == null) cards + card else cards.map { if (it.id == original.id) card else it })) close()
    }, ::leave)
    else FlashcardsScreen(
        cards.map { CardRow(it.id, it.front, it.back, it.reviews, it.lastRating, it.evidence.label) }, onBack,
        onAdd = { editingId = null; front = ""; back = ""; form = true },
        onEdit = { id -> cards.firstOrNull { it.id == id }?.let { editingId = id; front = it.front; back = it.back; form = true } },
        onDelete = { removing = it },
        onRate = { id, rating -> save(cards.map { if (it.id == id) QuizCards.rate(it, rating) else it }) }
    )
}
