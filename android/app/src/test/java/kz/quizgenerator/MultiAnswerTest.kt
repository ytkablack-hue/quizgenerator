package kz.quizgenerator
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.json.JSONObject

class MultiAnswerTest {
    @get:Rule val folder=TemporaryFolder()
    private val question=QuizQuestion("q","Чётные числа?",listOf("2","3","4"),0,"Проверка",additionalCorrectIndices=listOf(2))
    private val quiz=Quiz("qz","Два ответа",listOf(question))
    @Test fun exactSetRequiredAndAssumptionsStayUngraded() {
        assertEquals(QuizScore(1,0,0,0),QuizScorer.scoreSelections(quiz,listOf(setOf(2,0))))
        for(answer in listOf(setOf(0),setOf(0,1,2),setOf(1)))assertEquals(QuizScore(0,1,0,0),QuizScorer.scoreSelections(quiz,listOf(answer)))
        assertEquals(QuizScore(0,0,1,0),QuizScorer.scoreSelections(quiz,listOf(emptySet())))
        for(evidence in listOf(AnswerEvidence.INFERRED,AnswerEvidence.LOCAL_AI,AnswerEvidence.CLOUD_AI,AnswerEvidence.UNKNOWN)) {
            val unknown=quiz.copy(questions=listOf(question.copy(evidence=evidence)))
            assertEquals(QuizScore(0,0,0,1),QuizScorer.scoreSelections(unknown,listOf(setOf(0,2))))
            assertEquals(evidence,QuizJson.parse(QuizJson.serialize(unknown)).questions.single().evidence)
        }
    }
    @Test fun fullSetSurvivesRestartAndOriginalFormatStillLoads() {
        val file=folder.root.resolve("attempt.json");val store=ActiveAttemptStore(file)
        val old=ActiveAttempt(quiz,listOf(0),0,"attempt",1000,2000,3)
        store.save(old);assertEquals(old,store.load())
        val next=old.copy(selections=listOf(setOf(0,2)))
        store.save(next);assertEquals(next,store.load())
        val original=JSONObject(file.readText()).apply { remove("selections") }.toString()
        file.writeText(original);assertEquals(old,store.load());assertEquals(original,file.readText())
    }
    @Test fun fractionalAndDuplicateSelectionsRejectedWithoutRewriting() {
        val file=folder.root.resolve("attempt.json");val store=ActiveAttemptStore(file)
        store.save(ActiveAttempt(quiz,listOf(0),0,"attempt",1000,2000,3,listOf(setOf(0,2))))
        val original=file.readText()
        for(bad in listOf("[[0.5,2]]","[[0,0]]","[[3]]","[]")) {
            file.writeText(JSONObject(original).put("selections",org.json.JSONArray(bad)).toString())
            val before=file.readText();assertThrows(IllegalArgumentException::class.java) {store.load()};assertEquals(before,file.readText())
        }
    }
    @Test fun mistakesShowAllChoicesAndAllKeys() {
        val attempt=StudyLogic.completeSelections("attempt",quiz,listOf(setOf(1,2)),1)
        assertEquals(QuizScore(0,1,0,0),attempt.score)
        assertEquals("3, 4",attempt.mistakes.single().given)
        assertEquals("2, 4",attempt.mistakes.single().correct)
    }
    @Test fun answerAndKeySurviveShuffling() {
        val shuffled=StudyLogic.prepare(quiz,StudySettings(shuffleOptions=true),5)
        val q=shuffled.questions.single()
        assertEquals(setOf("2","4"),q.correctIndices.map {q.options[it]}.toSet())
        assertEquals(QuizScore(1,0,0,0),QuizScorer.scoreSelections(shuffled,listOf(q.correctIndices.toSet())))
    }
}
