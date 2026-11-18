package kz.quizgenerator

import java.util.Locale
import java.util.UUID

data class ParsedQuiz(val quiz: Quiz, val warnings: List<String>)

/**
 * Conservative, entirely offline parser for numbered multiple-choice questions.
 * If an answer key is missing or ambiguous, correctIndex stays null.
 */
object QuizTextParser {
    private val numberedQuestion = Regex("""^(?:№\s*)?(\d{1,3})[.)]\s*(.+)$""")
    private val namedQuestion = Regex(
        """^(?:Вопрос|Сұрақ|Question)\s*(?:№\s*)?(\d{1,3})\s*[:.)-]\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val option = Regex("""^(\*\s*)?([A-HА-ЗІ])\s*[.)]\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val answer = Regex(
        """^(?:Ответ|Правильный ответ|Дұрыс жауап|Ключ|Correct(?:\s+answer)?)\s*[:\-]\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val explanation = Regex(
        """^(?:Объяснение|Пояснение|Түсіндірме|Explanation)\s*[:\-]\s*(.*)$""",
        RegexOption.IGNORE_CASE
    )

    private data class OptionDraft(val label: String, var text: String, val marked: Boolean)
    private class QuestionDraft(val number: String, var text: String) {
        val options = mutableListOf<OptionDraft>()
        var answer: String? = null
        var explanation: String? = null
    }

    fun parse(source: String, title: String): ParsedQuiz {
        require(source.length <= 2_000_000) { "Документ слишком большой для распознавания" }
        val warnings = mutableListOf<String>()
        val parsedQuestions = mutableListOf<QuizQuestion>()
        var current: QuestionDraft? = null
        var field = "question"

        fun flush() {
            val draft = current ?: return
            if (draft.options.size !in 2..8 || draft.text.isBlank()) {
                warnings += "Вопрос ${draft.number} пропущен: нужен текст и 2–8 вариантов ответа"
                return
            }
            val labels = draft.options.map { it.label }
            if (labels.distinct().size != labels.size) {
                warnings += "Вопрос ${draft.number} пропущен: повторяются обозначения вариантов"
                return
            }
            val marked = draft.options.indices.filter { draft.options[it].marked }
            var index: Int? = null
            if (draft.answer != null) {
                val raw = draft.answer!!.trim().trimEnd('.', ')').uppercase(Locale.ROOT)
                index = labels.indexOf(raw).takeIf { it >= 0 }
                    ?: raw.toIntOrNull()?.minus(1)?.takeIf { it in draft.options.indices }
                    ?: draft.options.withIndex()
                        .filter { (_, option) -> option.text.equals(draft.answer!!.trim(), ignoreCase = true) }
                        .singleOrNull()?.index
                if (index == null) warnings += "Вопрос ${draft.number}: ключ «${draft.answer}» не распознан; проверь ответ вручную"
            } else if (marked.size == 1) {
                index = marked.single()
            } else if (marked.size > 1) {
                warnings += "Вопрос ${draft.number}: отмечено несколько правильных вариантов"
            }
            parsedQuestions += QuizQuestion(
                id = UUID.randomUUID().toString(),
                text = draft.text.trim(),
                options = draft.options.map { it.text.trim() },
                correctIndex = index,
                explanation = draft.explanation?.trim()?.ifBlank { null }
            )
        }

        source.replace("\r\n", "\n").replace('\r', '\n').lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@forEach
            val questionMatch = namedQuestion.matchEntire(line) ?: numberedQuestion.matchEntire(line)
            if (questionMatch != null) {
                flush()
                current = QuestionDraft(questionMatch.groupValues[1], questionMatch.groupValues[2])
                field = "question"
                return@forEach
            }
            val draft = current ?: return@forEach
            val optionMatch = option.matchEntire(line)
            val answerMatch = answer.matchEntire(line)
            val explanationMatch = explanation.matchEntire(line)
            when {
                optionMatch != null -> {
                    draft.options += OptionDraft(
                        optionMatch.groupValues[2].uppercase(Locale.ROOT),
                        optionMatch.groupValues[3],
                        optionMatch.groupValues[1].isNotBlank()
                    )
                    field = "option"
                }
                answerMatch != null -> {
                    draft.answer = answerMatch.groupValues[1]
                    field = "answer"
                }
                explanationMatch != null -> {
                    draft.explanation = explanationMatch.groupValues[1]
                    field = "explanation"
                }
                field == "explanation" -> draft.explanation =
                    (draft.explanation.orEmpty() + " " + line).trim()
                field == "option" && draft.options.isNotEmpty() -> {
                    val previous = draft.options.last()
                    previous.text += " " + line
                }
                field == "question" -> draft.text += " " + line
            }
        }
        flush()

        require(parsedQuestions.isNotEmpty()) {
            "Вопросы не найдены. Нужны номера 1., 2. и варианты A), B) или А), Б)."
        }
        require(parsedQuestions.size <= 500) { "Больше 500 вопросов в одном тесте не поддерживается" }
        return ParsedQuiz(
            Quiz(UUID.randomUUID().toString(), title.ifBlank { "Импортированный тест" }, parsedQuestions),
            warnings
        )
    }
}
