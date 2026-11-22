package kz.quizgenerator

internal data class KeyEntry(val number: Int, val labels: List<String>)

/**
 * Parses a single answer-key line. Supported shapes:
 *   1-А 2-Б 3-В
 *   1 — Б, 2 — А
 *   1. Б 2. А
 *   1 Б
 *   1 — А, В
 */
internal object AnswerKeyParser {
    private const val LETTERS = "A-Za-zА-Яа-яӘәҒғҚқҢңӨөҰұҮүҺһІі"

    private val numberStart = Regex(
        """(?:^|[\s,;])(\d{1,6})\s*(?:[-–—.:)]\s*|\s+(?=[$LETTERS]))"""
    )
    private val separators = Regex("""[\s,;/]+""")

    fun parse(line: String): List<KeyEntry> {
        val starts = numberStart.findAll(line).toList()
        if (starts.isEmpty()) return emptyList()
        return starts.mapIndexedNotNull { index, match ->
            val end = starts.getOrNull(index + 1)?.range?.first ?: line.length
            val answerPart = line.substring(match.range.last + 1, end).trim().trim(',', ';', ' ')
            val labels = splitLabels(answerPart)
            if (labels.isEmpty()) null else KeyEntry(match.groupValues[1].toInt(), labels)
        }
    }

    /** A key line is compact when every answer part is a short label, not a question sentence. */
    fun isCompactEntry(line: String): Boolean {
        if (line.contains('?')) return false
        val entries = parse(line)
        return entries.isNotEmpty() && entries.all { entry ->
            entry.labels.isNotEmpty() && entry.labels.all { it.length <= 2 }
        }
    }

    fun splitLabels(raw: String): List<String> =
        raw.split(separators).filter { it.isNotBlank() }.mapNotNull { token ->
            token.trim('.', ')', ':', ';', ',', '—', '–', '-').ifEmpty { null }
        }
}
