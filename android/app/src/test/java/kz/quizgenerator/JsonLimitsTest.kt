package kz.quizgenerator
import org.junit.Assert.*
import org.junit.Test

class JsonLimitsTest {
    @Test fun unboundedNumberIsRejectedBeforeNumericConversion() {
        assertThrows(IllegalArgumentException::class.java) { QuizJson.parse("{\"number\":" + "9".repeat(100_000) + "}") }
    }
    @Test fun nestingAndUnterminatedStringsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { JsonInputLimits.check("[".repeat(33) + "]".repeat(33)) }
        assertThrows(IllegalArgumentException::class.java) { JsonInputLimits.check("{\"text\":\"незавершено}") }
    }
    @Test fun bracesAndEscapedQuotesInRussianTextDoNotCountAsNesting() {
        JsonInputLimits.check("""{"text":"Русский текст: { [ \"цитата\" ] }","correctIndex":0}""")
        JsonInputLimits.check("{" + "\"a\":0,".repeat(5000) + "\"b\":1}")
    }
}
