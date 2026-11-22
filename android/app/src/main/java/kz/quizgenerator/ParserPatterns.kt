package kz.quizgenerator

import java.util.Locale

/**
 * Central place for line-level recognition. Keeping the patterns here makes the
 * parser state machine readable and lets each format evolve independently.
 */
internal object ParserPatterns {
    const val KAZAKH_LETTERS = "ӘәҒғҚқҢңӨөҰұҮүҺһІі"
    private const val CYRILLIC = "А-Яа-я"
    private const val LATIN = "A-Za-z"
    private const val LETTERS = "$LATIN$CYRILLIC$KAZAKH_LETTERS"
    private const val MARKERS = "*+✓✔•·"

    val taggedQuestion = Regex(
        """^<\s*question\s*>\s*(.*?)\s*(?:</\s*question\s*>)?$""",
        RegexOption.IGNORE_CASE
    )
    val taggedVariant = Regex(
        """^<\s*variant\s*>\s*(.*?)\s*(?:</\s*variant\s*>)?$""",
        RegexOption.IGNORE_CASE
    )

    val namedQuestion = Regex(
        """^(?:Вопрос|Сұрақ|Сұрау|Question|Тапсырма)\s*(?:№\s*)?(\d{1,6})\s*[:.)\-–—]?\s*(.*)$""",
        RegexOption.IGNORE_CASE
    )
    val reversedNamedQuestion = Regex(
        """^(\d{1,6})\s*[-–—]\s*(?:сұрақ|вопрос|question|тапсырма)\b\s*[:.)\-–—]?\s*(.*)$""",
        RegexOption.IGNORE_CASE
    )

    /** A line that starts with a number and a delimiter. Context decides question vs option. */
    val numbered = Regex("""^(?:№\s*)?(\d{1,6})\s*([.)\]:\-–—])\s*(.*)$""")

    /** A labelled answer option: A) / Б. / 1) / *В) / ✓ C. */
    val option = Regex("""^([$MARKERS]\s*)?([$LETTERS]|\d{1,2})\s*([.)\]:\-–—])\s*(.*)$""")

    val answer = Regex(
        """^(?:Ответ|Правильный\s+ответ|Дұрыс\s+жауап|Жауабы|Ключ|Correct(?:\s+answer)?|Answer)\s*[:\-–—]\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )
    val explanation = Regex(
        """^(?:Объяснение|Пояснение|Түсіндірме|Explanation|Rationale)\s*[:\-–—]\s*(.*)$""",
        RegexOption.IGNORE_CASE
    )

    /** Header of a separate answer-key block. */
    val keyHeader = Regex(
        """^(?:Ответы|Ключ(?:и)?|Правильные\s+ответы|Жауаптар(?:\s+кілті)?|Answers|Answer\s+key)\s*[:\-–—]?\s*(.*)$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Latin letters that are visually identical to Cyrillic ones. Used only as a
     * fallback after an exact label match, so "B" in an English test and "В" in a
     * Russian test resolve to the same option instead of being silently different.
     */
    private val lookalike = mapOf(
        'A' to 'А', 'B' to 'В', 'C' to 'С', 'E' to 'Е', 'H' to 'Н', 'K' to 'К',
        'M' to 'М', 'O' to 'О', 'P' to 'Р', 'T' to 'Т', 'X' to 'Х', 'Y' to 'У'
    )

    fun normalizeLabel(value: String): String =
        value.trim().trim('.', ')', ':', ';', ',', '—', '–', '-').uppercase(Locale.ROOT)

    fun canonicalLabel(value: String): String = buildString {
        for (char in value.uppercase(Locale.ROOT)) append(lookalike[char] ?: char)
    }

    fun looksLikeQuestionText(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.endsWith("?") || trimmed.endsWith("؟")
    }

    fun isLetterLabel(value: String): Boolean =
        value.isNotEmpty() && value.all { it.isLetter() }
}
