package kz.quizgenerator

import java.util.Locale
import java.util.UUID

data class ParsedQuiz(val quiz: Quiz, val warnings: List<String>)

/**
 * Streaming, context-aware parser for university-style tests.
 *
 * It is deliberately a small state machine rather than one huge regular expression:
 * each line is classified (header, option, key, explanation, continuation), and the
 * current question context decides whether a numeric line is a new question or an
 * answer option. Separate helpers handle answer-key blocks and label normalisation.
 */
object QuizTextParser {
    private enum class Field { QUESTION, OPTION, ANSWER, EXPLANATION }

    private data class OptionDraft(val label: String, var text: String, val marked: Boolean)

    private class Draft(val ordinal: Int, val number: Int?, var text: String, val tagged: Boolean) {
        val raw = StringBuilder()
        val options = mutableListOf<OptionDraft>()
        val plain = mutableListOf<String>()
        var answerRaw: String? = null
        var explanation: String? = null
        var field = Field.QUESTION
        var lastNumericOption: Int? = null
        var ambiguousWrap = false
    }

    private data class ParsedEntry(val number: Int?, val question: QuizQuestion, val labels: List<String>, val source: String)

    fun parse(source: String, title: String): ParsedQuiz =
        parseFrom(title) { consume -> source.lineSequence().forEach(consume) }

    internal fun parseFrom(
        title: String,
        checkCancelled: () -> Unit = {},
        read: ((String) -> Unit) -> Unit
    ): ParsedQuiz {
        var seen = 0L
        var lineCount = 0
        val warnings = mutableListOf<String>()
        var omittedWarnings = 0
        val entries = mutableListOf<ParsedEntry>()
        val problems = mutableListOf<ImportProblem>()
        fun problem(number: Int?, reason: String, source: String) {
            require(problems.size < QuizLimits.QUESTIONS) { "Слишком много проблемных фрагментов. Разделите документ" }
            problems.add(ImportProblem(number, reason, source))
        }
        val keys = mutableMapOf<Int, List<String>>()
        var keyMode = false
        var current: Draft? = null
        var expectedNumber: Int? = null
        var ordinal = 0
        var blankBefore = true

        fun warning(text: String) {
            if (warnings.size < 100) warnings.add(text.take(400)) else omittedWarnings++
        }

        fun append(old: String, next: String): String {
            require(old.length + next.length < 65_536) {
                "Вопрос, ответ или пояснение слишком длинные. Разделите документ."
            }
            if (old.endsWith('\u00ad')) return old.dropLast(1) + next.trimStart()
            if (old.endsWith('-') && next.firstOrNull()?.isLowerCase() == true) {
                current?.ambiguousWrap = true
                return old + next.trimStart()
            }
            return if (old.isBlank()) next.trim() else "$old $next".trim()
        }

        fun looksLikeOptionList(draft: Draft, lines: List<String>): Boolean {
            if (lines.size !in 2..QuizLimits.OPTIONS) return false
            if (lines.any { it.length > 300 }) return false
            if (lines.count { it.length <= 140 } < 2) return false
            return ParserPatterns.looksLikeQuestionText(draft.text)
        }

        fun resolvePlain(asOptions: Boolean) {
            val draft = current ?: return
            if (draft.plain.isEmpty()) return
            val lines = draft.plain.toList()
            draft.plain.clear()
            if (asOptions && draft.options.isEmpty() && looksLikeOptionList(draft, lines)) {
                lines.forEachIndexed { index, value ->
                    draft.options.add(OptionDraft((index + 1).toString(), value, false))
                }
                warning("Вопрос ${draft.number ?: draft.ordinal}: варианты без обозначений распознаны предположительно — проверьте")
                problem(draft.number ?: draft.ordinal, "Варианты без обозначений требуют ручной проверки", draft.raw.toString())
            } else {
                draft.text = lines.fold(draft.text, ::append)
                if (lines.size >= 2 && draft.options.isEmpty()) {
                    warning("Вопрос ${draft.number ?: draft.ordinal}: не удалось надёжно отделить варианты от текста — проверьте")
                }
            }
        }

        fun addOption(draft: Draft, label: String, text: String, marked: Boolean) {
            require(draft.options.size < QuizLimits.OPTIONS) {
                "В вопросе больше ${QuizLimits.OPTIONS} вариантов. Разделите документ."
            }
            val clean = ParserPatterns.normalizeLabel(label)
            draft.options.add(OptionDraft(clean, text.trim(), marked))
            draft.field = Field.OPTION
            if (clean.all(Char::isDigit)) draft.lastNumericOption = clean.toIntOrNull()
        }

        fun flush() {
            val draft = current ?: return
            resolvePlain(asOptions = true)
            current = null
            if (draft.text.isBlank()) {
                warning("Запись ${draft.number ?: draft.ordinal} пропущена: нет текста вопроса")
                problem(draft.number ?: draft.ordinal, "нет текста вопроса", draft.raw.toString())
                return
            }
            if (draft.options.size < 2) {
                warning("Вопрос ${draft.number ?: draft.ordinal} пропущен: нужны текст и минимум два непустых варианта")
                problem(draft.number ?: draft.ordinal, "нужны текст и минимум два непустых варианта", draft.raw.toString())
                return
            }
            if (draft.options.any { it.text.isBlank() }) {
                warning("Вопрос ${draft.number ?: draft.ordinal} пропущен: есть пустой вариант")
                problem(draft.number ?: draft.ordinal, "есть пустой вариант", draft.raw.toString())
                return
            }

            if (draft.ambiguousWrap) {
                warning("Вопрос ${draft.number ?: draft.ordinal}: перенос слова с дефисом требует проверки")
                problem(draft.number ?: draft.ordinal, "Проверьте перенос слова с дефисом", draft.raw.toString())
            }
            val labels = draft.options.map { ParserPatterns.canonicalLabel(it.label) }
            if (labels.distinct().size != labels.size) {
                warning("Вопрос ${draft.number ?: draft.ordinal}: повторяются обозначения вариантов — проверьте ключ вручную")
            }

            val marked = draft.options.indices.filter { draft.options[it].marked }
            val inline = draft.answerRaw?.let { resolveAnswer(it, draft.options) }
            val indices = when {
                inline != null && inline.first.isNotEmpty() -> inline.first
                marked.size == 1 -> marked
                marked.size > 1 -> {
                    warning("Вопрос ${draft.number ?: draft.ordinal}: отмечено несколько вариантов — проверьте")
                    marked
                }
                else -> emptyList()
            }
            if (inline != null && inline.second.isNotEmpty()) {
                warning("Вопрос ${draft.number ?: draft.ordinal}: ключ «${inline.second.joinToString().take(100)}» не распознан; проверь ответ вручную")
            }
            if (inline != null && marked.isNotEmpty() && inline.first.toSet() != marked.toSet()) {
                warning("Вопрос ${draft.number ?: draft.ordinal}: ответ и отметка варианта расходятся — проверьте")
            }

            if (inline?.second?.isNotEmpty() == true ||
                (inline != null && marked.isNotEmpty() && inline.first.toSet() != marked.toSet())) {
                problem(draft.number ?: draft.ordinal, "Неоднозначный ключ ответа", draft.raw.toString())
            }
            val primary = indices.firstOrNull()
            require(entries.size < QuizLimits.QUESTIONS) { "В документе больше ${QuizLimits.QUESTIONS} вопросов" }
            entries.add(
                ParsedEntry(
                    draft.number ?: draft.ordinal,
                    QuizQuestion(
                        UUID.randomUUID().toString(),
                        draft.text.trim(),
                        draft.options.map { it.text.trim() },
                        primary,
                        draft.explanation?.trim()?.ifBlank { null },
                        evidence = when {
                            indices.isEmpty() -> AnswerEvidence.UNKNOWN
                            inline?.second?.isNotEmpty() == true ||
                                (inline != null && marked.isNotEmpty() && inline.first.toSet() != marked.toSet()) -> AnswerEvidence.INFERRED
                            else -> AnswerEvidence.SOURCE
                        },
                        additionalCorrectIndices = indices.filter { it != primary }.distinct()
                    ),
                    draft.options.map { it.label }, draft.raw.toString()
                )
            )
        }

        fun startQuestion(number: Int?, text: String, tagged: Boolean) {
            flush()
            ordinal++
            current = Draft(ordinal, number, text.trim(), tagged)
            if (number != null) expectedNumber = number + 1
        }

        fun handleKeyLine(line: String) {
            val parsed = AnswerKeyParser.parse(line)
            if (parsed.isEmpty()) {
                warning("Строка ключа не распознана: ${line.take(120)}")
                return
            }
            parsed.forEach { entry ->
                if (keys.containsKey(entry.number)) warning("Ключ для вопроса ${entry.number} указан повторно")
                keys[entry.number] = entry.labels
            }
        }

        checkCancelled()
        read { rawLine ->
            try {
            lineCount++
            if (lineCount % 256 == 0) checkCancelled()
            seen += rawLine.length + 1L
            require(seen <= 8_000_000) { "В документе больше 8 млн символов текста. Разделите его на части." }
            require(rawLine.length <= 65_536) { "Строка документа слишком длинная. Разделите её на абзацы." }

            val line = rawLine.replace('\u00a0', ' ').replace('\u202f', ' ').replace("\uFEFF", "").trim()
            if (line.isEmpty()) {
                blankBefore = true
                return@read
            }
            val wasBlank = blankBefore
            blankBefore = false

            // Inside a key block a compact line such as "1 — Б" is a key entry, not a question.
            if (keyMode) {
                if (AnswerKeyParser.isCompactEntry(line)) {
                    AnswerKeyParser.parse(line).forEach { entry ->
                        if (keys.containsKey(entry.number)) warning("Ключ для вопроса ${entry.number} указан повторно")
                        keys[entry.number] = entry.labels
                    }
                    return@read
                }
                keyMode = false
            }

            val tagged = ParserPatterns.taggedQuestion.matchEntire(line)
            if (tagged != null) {
                startQuestion(null, tagged.groupValues[1], true)
                return@read
            }

            val named = ParserPatterns.namedQuestion.matchEntire(line)
                ?: ParserPatterns.reversedNamedQuestion.matchEntire(line)
            if (named != null) {
                startQuestion(named.groupValues[1].toIntOrNull(), named.groupValues[2], false)
                return@read
            }

            val keyHeader = ParserPatterns.keyHeader.matchEntire(line)
            val answer = ParserPatterns.answer.matchEntire(line)
            if (keyHeader != null && answer == null) {
                flush()
                keyMode = true
                val rest = keyHeader.groupValues[1]
                if (rest.isNotBlank()) handleKeyLine(rest)
                return@read
            }

            val draft = current
            if (draft == null) {
                val number = ParserPatterns.numbered.matchEntire(line)
                if (number != null && number.groupValues[3].isNotBlank()) {
                    startQuestion(number.groupValues[1].toIntOrNull(), number.groupValues[3], false)
                } else if (ParserPatterns.looksLikeQuestionText(line)) {
                    // Format E: a question without a number or a "Вопрос" prefix.
                    startQuestion(null, line, false)
                }
                return@read
            }

            val variant = ParserPatterns.taggedVariant.matchEntire(line)
            if (variant != null) {
                resolvePlain(asOptions = false)
                addOption(draft, (draft.options.size + 1).toString(), variant.groupValues[1], false)
                return@read
            }

            if (answer != null) {
                resolvePlain(asOptions = true)
                draft.answerRaw = answer.groupValues[1]
                draft.field = Field.ANSWER
                return@read
            }

            val explanation = ParserPatterns.explanation.matchEntire(line)
            if (explanation != null) {
                resolvePlain(asOptions = true)
                draft.explanation = explanation.groupValues[1]
                draft.field = Field.EXPLANATION
                return@read
            }

            val number = ParserPatterns.numbered.matchEntire(line)
            if (number != null && shouldStartQuestion(number, draft, wasBlank, expectedNumber)) {
                startQuestion(number.groupValues[1].toIntOrNull(), number.groupValues[3], false)
                return@read
            }

            if (draft.field == Field.QUESTION && draft.plain.size >= 2 && wasBlank && ParserPatterns.looksLikeQuestionText(line)) {
                startQuestion(null, line, false)
                return@read
            }

            val option = ParserPatterns.option.matchEntire(line)
            if (option != null) {
                resolvePlain(asOptions = false)
                addOption(draft, option.groupValues[2], option.groupValues[4], option.groupValues[1].isNotBlank())
                return@read
            }

            when (draft.field) {
                Field.EXPLANATION -> draft.explanation = append(draft.explanation.orEmpty(), line)
                Field.ANSWER -> draft.answerRaw = append(draft.answerRaw.orEmpty(), line)
                Field.OPTION -> draft.options.last().text = append(draft.options.last().text, line)
                Field.QUESTION -> draft.plain.add(line)
            }
            } finally {
                current?.raw?.append(rawLine)?.append('\n')
            }
        }

        checkCancelled()
        resolvePlain(asOptions = true)
        flush()

        // A key block may appear before or after the questions, so apply it at the end.
        val resolved = entries.map { entry ->
            val raw = keys[entry.number] ?: return@map entry
            val (indices, unresolved) = resolveLabels(raw, entry.labels, entry.question.options)
            if (indices.isEmpty()) {
                warning("Ключ для вопроса ${entry.number} не распознан: ${raw.joinToString().take(100)}")
                return@map entry
            }
            if (unresolved.isNotEmpty()) {
                warning("Вопрос ${entry.number}: часть ключа не распознана — ${unresolved.joinToString().take(100)}")
            }
            if (entry.question.correctIndices.isNotEmpty() && entry.question.correctIndices.toSet() != indices.toSet()) {
                warning("Вопрос ${entry.number}: ключ в блоке отличается от ответа рядом с вопросом — использован блок ключей")
            }
            val primary = indices.first()
            entry.copy(
                question = entry.question.copy(
                    correctIndex = primary,
                    additionalCorrectIndices = indices.filter { it != primary }.distinct(),
                    evidence = if (unresolved.isNotEmpty() ||
                        (entry.question.correctIndices.isNotEmpty() && entry.question.correctIndices.toSet() != indices.toSet()))
                        AnswerEvidence.INFERRED else AnswerEvidence.SOURCE
                )
            )
        }

        keys.keys.filter { number -> resolved.none { it.number == number } }.forEach {
            warning("В ключах есть ответ для вопроса $it, но такой вопрос не найден")
        }

        val seenSignatures = HashSet<String>()
        var duplicates = 0
        val deduped = resolved.mapNotNull { entry ->
            val question = entry.question
            val signature = question.text.trim().lowercase(Locale.ROOT) + "\u0000" +
                question.options.joinToString("\u0001") { it.trim().lowercase(Locale.ROOT) }
            if (seenSignatures.add(signature)) question else {
                duplicates++
                problem(entry.number, "Повтор вопроса — проверьте необходимость сохранения", entry.source)
                null
            }
        }
        if (duplicates > 0) warning("Удалено повторяющихся вопросов: $duplicates")

        require(deduped.isNotEmpty()) {
            "Вопросы не найдены. Используйте номера с вариантами или теги <question> и <variant>."
        }
        if (omittedWarnings > 0) warnings.add("Других замечаний: $omittedWarnings")
        return ParsedQuiz(
            Quiz(UUID.randomUUID().toString(), title.ifBlank { "Импортированный тест" }, deduped, importProblems = problems),
            warnings
        )
    }

    /**
     * Numeric lines are the hardest ambiguity. A numeric line is a new question only
     * when the surrounding context supports it; otherwise it is an answer option.
     */
    private fun shouldStartQuestion(
        match: MatchResult,
        draft: Draft,
        wasBlank: Boolean,
        expectedNumber: Int?
    ): Boolean {
        val number = match.groupValues[1].toIntOrNull() ?: return false
        val delimiter = match.groupValues[2]
        val text = match.groupValues[3]
        val last = draft.lastNumericOption
        val count = draft.options.size

        // A continuing numeric option run such as 1) 2) 3) is almost always options.
        if (last != null && number == last + 1 && delimiter != ".") return false
        if (last != null && number == last + 1 && count < 2) return false

        if (expectedNumber != null && number == expectedNumber) {
            if (wasBlank && count >= 2) return true
            if (count == 0) return true
            if (ParserPatterns.looksLikeQuestionText(text)) return true
            if (delimiter == "." && count >= 2) return true
            if (last == null || number != last + 1) return true
        }

        if (wasBlank && count >= 2 && delimiter == "." && number > (draft.number ?: 0)) return true
        if (count >= 2 && number > (draft.number ?: 0) && ParserPatterns.looksLikeQuestionText(text)) return true
        if (count >= 2 && delimiter == "." && number > (draft.number ?: 0) && text.length > 40) return true
        return false
    }

    private fun resolveAnswer(raw: String, options: List<OptionDraft>): Pair<List<Int>, List<String>> {
        val trimmed = raw.trim().trimEnd('.', ')', ':', ';', ',')
        val textMatches = options.withIndex().filter { it.value.text.trim().equals(trimmed, ignoreCase = true) }
        if (textMatches.size == 1) return listOf(textMatches.single().index) to emptyList()
        return resolveLabels(answerTokens(trimmed), options.map { it.label }, options.map { it.text })
    }

    private fun answerTokens(raw: String): List<String> {
        val normalized = raw.trim().trim('.', ')', ':', ';', ',')
        if (normalized.isEmpty()) return emptyList()
        if (normalized.any { it == ',' || it == ';' || it == '/' }) {
            return normalized.split(Regex("""[\s,;/]+""")).filter { it.isNotBlank() }
        }
        return listOf(normalized)
    }

    private fun resolveLabels(
        tokens: List<String>,
        labels: List<String>,
        texts: List<String>
    ): Pair<List<Int>, List<String>> {
        val indices = linkedSetOf<Int>()
        val unresolved = mutableListOf<String>()
        val canonicalLabels = labels.map(ParserPatterns::canonicalLabel)

        fun single(token: String): Int? {
            val normalized = ParserPatterns.normalizeLabel(token)
            val exact = labels.indices.filter { ParserPatterns.normalizeLabel(labels[it]) == normalized }
            if (exact.isNotEmpty()) return exact.singleOrNull()
            val canonical = ParserPatterns.canonicalLabel(token)
            val lookalike = canonicalLabels.indices.filter { canonicalLabels[it] == canonical }
            if (lookalike.isNotEmpty()) return lookalike.singleOrNull()
            val numeric = token.trim().toIntOrNull()?.minus(1)?.takeIf { it in texts.indices }
            if (numeric != null) return numeric
            val textMatches = texts.withIndex().filter { it.value.trim().equals(token.trim(), ignoreCase = true) }
            if (textMatches.size == 1) return textMatches.single().index
            return null
        }

        for (token in tokens) {
            val direct = single(token)
            if (direct != null) {
                indices.add(direct)
                continue
            }
            // Compact multi-answer key ("АВ"), but never split real answer text such as "Да".
            if (token.length > 1 && ParserPatterns.isLetterLabel(token) &&
                texts.none { it.trim().equals(token.trim(), ignoreCase = true) }
            ) {
                val mapped = token.map { single(it.toString()) }
                if (mapped.all { it != null }) {
                    mapped.filterNotNull().forEach(indices::add)
                    continue
                }
            }
            unresolved.add(token)
        }
        return indices.toList() to unresolved
    }
}
