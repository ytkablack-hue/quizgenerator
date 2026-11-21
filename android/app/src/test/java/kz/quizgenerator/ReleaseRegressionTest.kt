package kz.quizgenerator
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReleaseRegressionTest {
    @get:Rule val folder=TemporaryFolder()
    private val quiz=Quiz("sample","Русский тест",listOf(QuizQuestion("a","Два плюс два?",listOf("4","5"),0,"2 + 2 = 4")))
    @Test fun activeAttemptSurvivesProcessRestartAndKeepsSnapshot() {
        val store=ActiveAttemptStore(folder.root.resolve("active.json"))
        val value=ActiveAttempt(quiz,listOf(1),0,"attempt",10000,20000,7)
        store.save(value);assertEquals(value,store.load());store.clear();assertNull(store.load())
    }
    @Test fun invalidActiveAnswerIsRejectedWithoutDeletingFile() {
        val file=folder.root.resolve("active.json");val store=ActiveAttemptStore(file)
        store.save(ActiveAttempt(quiz,listOf(9),0,"attempt",10000,20000,7))
        assertThrows(IllegalArgumentException::class.java){store.load()};assertTrue(file.exists())
    }
    @Test fun settingsMigrationPreservesLocalContentAndDiscardsLegacySecret() {
        val file=folder.root.resolve("study.json")
        file.writeText("""{"settings":{"server":"https://old.example","accessToken":"unit-test-only","dark":true,"minutes":999},"cards":[],"attempts":[],"exams":[]}""")
        val store=StudyStore(file);val loaded=store.load()
        assertEquals(ProductionConfig.SERVER,loaded.settings.server);assertEquals("",loaded.settings.accessToken)
        assertEquals("dark",loaded.settings.theme);assertEquals(180,loaded.settings.minutes)
        store.save(loaded);assertFalse(file.readText().contains("unit-test-only"));assertFalse(file.readText().contains("accessToken"))
    }
    @Test fun progressUsesOnlyGradedQuestions() {
        assertNull(ProgressSummary.from(emptyList()).percent)
        val attempt=Attempt("id","sample","Тест",1,QuizScore(2,1,1,10),emptyList())
        assertEquals(50,ProgressSummary.from(listOf(attempt)).percent)
    }
    @Test fun malformedAttachmentsAndFakeExtensionsAreRejected() {
        assertThrows(IllegalStateException::class.java){AttachmentRules.detect("not a PDF".toByteArray())}
        assertThrows(IllegalArgumentException::class.java){AttachmentRules.detect(byteArrayOf())}
        assertEquals("application/pdf",AttachmentRules.detect("%PDF-test".toByteArray()))
    }
    @Test fun errorPracticeOnlyUsesActualMistakes() {
        val attempt=StudyLogic.complete("id",quiz,listOf(1),1)
        assertEquals(quiz.questions,mistakeQuiz(quiz,attempt).questions)
        assertEquals("Русский тест · Повторение ошибок",mistakeQuiz(quiz,attempt).title)
    }
}
