package kz.quizgenerator

import java.util.Locale
import java.util.UUID

data class ParsedQuiz(val quiz: Quiz, val warnings: List<String>)

object QuizTextParser {
    private val numbered = Regex("""^(?:№\s*)?(\d{1,6})[.)]\s*(.+)$""")
    private val named = Regex("""^(?:Вопрос|Сұрақ|Question)\s*(?:№\s*)?(\d{1,6})\s*[:.)-]\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val taggedQuestion = Regex("""^<question>\s*(.*?)(?:</question>)?$""", RegexOption.IGNORE_CASE)
    private val taggedVariant = Regex("""^<variant>\s*(.*?)(?:</variant>)?$""", RegexOption.IGNORE_CASE)
    private val option = Regex("""^(\*\s*)?([A-ZА-ЯІ]|\d{1,2})\s*[.)]\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val answer = Regex("""^(?:Ответ|Правильный ответ|Дұрыс жауап|Ключ|Correct(?:\s+answer)?)\s*[:\-]\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val explanation = Regex("""^(?:Объяснение|Пояснение|Түсіндірме|Explanation)\s*[:\-]\s*(.*)$""", RegexOption.IGNORE_CASE)
    private data class OptionDraft(val label: String, var text: String, val marked: Boolean)
    private class Draft(val number: String, var text: String, val tagged: Boolean) {
        val options = mutableListOf<OptionDraft>()
        var answer: String? = null
        var explanation: String? = null
    }

    fun parse(source: String, title: String): ParsedQuiz =
        parseFrom(title) { consume -> source.lineSequence().forEach(consume) }

    internal fun parseFrom(title: String, read: ((String) -> Unit) -> Unit): ParsedQuiz {
        var seen = 0L
        val warnings = mutableListOf<String>()
        var omittedWarnings = 0
        val questions = mutableListOf<QuizQuestion>()
        var current: Draft? = null
        var field = "question"
        var ordinal = 0

        fun warning(text: String) {
            if (warnings.size < 100) warnings.add(text.take(400)) else omittedWarnings++
        }
        fun append(old: String, next: String): String {
            require(old.length + next.length < 65_536) { "Вопрос, ответ или пояснение слишком длинные. Разделите документ." }
            return "$old $next".trim()
        }
        fun flush() {
            val draft = current ?: return
            if (draft.text.isBlank() || draft.options.size < 2 || draft.options.any { it.text.isBlank() }) {
                warning("Вопрос ${draft.number} пропущен: нужны текст и минимум два непустых варианта")
                return
            }
            val labels = draft.options.map { it.label }
            if (labels.distinct().size != labels.size) {
                warning("Вопрос ${draft.number} пропущен: повторяются обозначения вариантов")
                return
            }
            val marked = draft.options.indices.filter { draft.options[it].marked }
            val raw = draft.answer?.trim()
            val key = if (raw != null) {
                val normalized = raw.trimEnd('.', ')').uppercase(Locale.ROOT)
                labels.indexOf(normalized).takeIf { it >= 0 }
                    ?: normalized.toIntOrNull()?.minus(1)?.takeIf { it in draft.options.indices }
                    ?: draft.options.withIndex().filter { it.value.text.equals(raw, true) }.singleOrNull()?.index
            } else marked.singleOrNull()
            if (raw != null && key == null) warning("Вопрос ${draft.number}: ключ «${raw.take(100)}» не распознан; проверь ответ вручную")
            if (marked.size > 1 && raw == null) warning("Вопрос ${draft.number}: отмечено несколько правильных вариантов")
            require(questions.size < QuizLimits.QUESTIONS) { "В документе больше ${QuizLimits.QUESTIONS} вопросов. Разделите его на части." }
            questions.add(QuizQuestion(UUID.randomUUID().toString(), draft.text.trim(), draft.options.map { it.text.trim() },
                key, draft.explanation?.trim()?.ifBlank { null }))
        }
        read { rawLine ->
            seen += rawLine.length + 1L
            require(seen <= 8_000_000) { "В документе больше 8 млн символов текста. Разделите его на части." }
            require(rawLine.length <= 65_536) { "Строка документа слишком длинная. Разделите её на абзацы." }
            val line = rawLine.replace('\u00a0', ' ').replace('\u202f', ' ').replace("\uFEFF", "").trim()
            if (line.isEmpty()) return@read
            val tag = taggedQuestion.matchEntire(line)
            val header = named.matchEntire(line) ?: numbered.matchEntire(line)
            if (tag != null || (header != null && current?.tagged != true)) {
                flush()
                ordinal++
                current = if (tag != null) Draft(ordinal.toString(), tag.groupValues[1], true)
                    else Draft(header!!.groupValues[1], header.groupValues[2], false)
                field = "question"
                return@read
            }
            val draft = current ?: return@read
            val variant = taggedVariant.matchEntire(line)
            val choice = if (draft.tagged) null else option.matchEntire(line)
            val key = answer.matchEntire(line)
            val note = explanation.matchEntire(line)
            when {
                variant != null || choice != null -> {
                    require(draft.options.size < QuizLimits.OPTIONS) { "В вопросе больше ${QuizLimits.OPTIONS} вариантов. Разделите документ." }
                    draft.options.add(if (variant != null) OptionDraft((draft.options.size + 1).toString(), variant.groupValues[1], false)
                        else OptionDraft(choice!!.groupValues[2].uppercase(Locale.ROOT), choice.groupValues[3], choice.groupValues[1].isNotBlank()))
                    field = "option"
                }
                key != null -> { draft.answer = key.groupValues[1]; field = "answer" }
                note != null -> { draft.explanation = note.groupValues[1]; field = "explanation" }
                field == "explanation" -> draft.explanation = append(draft.explanation.orEmpty(), line)
                field == "option" -> draft.options.last().let { it.text = append(it.text, line) }
                field == "question" -> draft.text = append(draft.text, line)
            }
        }
        flush()
        require(questions.isNotEmpty()) { "Вопросы не найдены. Используйте номера с вариантами или теги <question> и <variant>." }
        if (omittedWarnings > 0) warnings.add("Других замечаний: $omittedWarnings")
        return ParsedQuiz(Quiz(UUID.randomUUID().toString(), title.ifBlank { "Импортированный тест" }, questions), warnings)
    }
}
