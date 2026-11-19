package kz.quizgenerator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QuizJsonTest {
    private fun quiz(key: String) = """
        {"id":"demo","title":"Русский тест","questions":[
            {"id":"q","text":"Где Волга?","options":["Россия","Китай"],
             "correctIndex":$key,"explanation":"Волга течёт в России."}
        ]}
    """.trimIndent()

    @Test fun russianTextSurvivesRoundTrip() {
        val parsed = QuizJson.parse(quiz("0"))
        assertEquals("Русский тест", parsed.title)
        assertEquals("Россия", parsed.questions.single().options[0])
        assertEquals(parsed, QuizJson.parse(QuizJson.serialize(parsed)))
    }

    @Test fun rejectsAmbiguousAndInvalidIndices() {
        for (key in listOf("0.5", "\"0\"", "-1", "2")) {
            assertThrows(IllegalArgumentException::class.java) { QuizJson.parse(quiz(key)) }
        }
    }

    @Test fun rejectsEmptyCorruptAndTrailingInput() {
        for (input in listOf("", "{", quiz("0") + " {}")) {
            assertThrows(Exception::class.java) { QuizJson.parse(input) }
        }
    }
}
