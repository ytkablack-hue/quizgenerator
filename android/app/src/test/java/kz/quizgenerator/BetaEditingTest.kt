package kz.quizgenerator

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BetaEditingTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun draftValidatesOptionsAndCanReorderQuestionsWithoutChangingKeys() {
        val a = emptyQuestion().copy(text = "Первый", options = listOf("А", "Б"), correctIndex = 1)
        val b = emptyQuestion().copy(text = "Второй", options = listOf("В", "Г"))
        assertEquals(listOf(b, a), EditorRules.move(listOf(a, b), 0, 1))
        EditorRules.validate("Тест", listOf(a, b))
        assertThrows(IllegalArgumentException::class.java) { EditorRules.validate("Тест", listOf(emptyQuestion())) }
    }

    @Test fun themeMigratesFromPreviousSettingsWithoutDroppingOtherFields() {
        val file = temporary.newFile()
        file.writeText("""{"settings":{"dark":true,"minutes":20,"showAnswers":false}}""")
        val store = StudyStore(file)
        val previous = store.load()
        assertEquals("dark", previous.settings.theme)
        assertEquals(20, previous.settings.minutes)
        assertFalse(previous.settings.showAnswers)
        val switched = previous.copy(settings = previous.settings.copy(dark = false, theme = "light"))
        store.save(switched)
        assertEquals(switched, StudyStore(file).load())
        assertEquals("system", StudyStore(temporary.root.resolve("fresh.json")).load().settings.theme)
    }
}
