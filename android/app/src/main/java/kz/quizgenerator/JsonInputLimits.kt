package kz.quizgenerator

/** Linear preflight before parsing untrusted JSON. Protects platform and JVM parsers alike. */
internal object JsonInputLimits {
    fun check(source: String) {
        require(source.length <= 12_000_000) { "JSON больше 12 МБ. Разделите тест" }
        var quote: Char? = null
        var escaped = false
        var depth = 0
        var tokenLength = 0
        var stringLength = 0
        for (char in source) {
            if (quote != null) {
                stringLength++
                require(stringLength <= 131_072) { "Слишком длинное поле JSON" }
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == quote -> quote = null
                }
            } else when (char) {
                '"', '\'' -> { quote = char; tokenLength = 0; stringLength = 0 }
                '{', '[' -> { depth++; tokenLength = 0; require(depth <= 32) { "Слишком глубокая структура JSON" } }
                '}', ']' -> { depth--; tokenLength = 0; require(depth >= 0) { "Повреждённая структура JSON" } }
                ',', ':', ' ', '\t', '\r', '\n' -> tokenLength = 0
                else -> { tokenLength++; require(tokenLength <= 128) { "Слишком длинное число или значение JSON" } }
            }
        }
        require(quote == null && depth == 0) { "Незавершённая структура JSON" }
    }
}
