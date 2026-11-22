package kz.quizgenerator
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CancellationException
import kz.quizgenerator.ui.screens.CardSessionRules

class StudyUxRegressionTest {
    @get:Rule val folder=TemporaryFolder()
    private val q=QuizQuestion("q","Чётные числа?",listOf("2","3","4"),0,"Объяснение",additionalCorrectIndices=listOf(2))
    private val quiz=Quiz(java.util.UUID.randomUUID().toString(),"Тест",listOf(q))
    @Test fun resultStatusesMatchStrictMultiAnswerScoring() {
        val variants=listOf(setOf(0,2),setOf(0),emptySet())
        val outcomes=listOf(QuestionOutcome.CORRECT,QuestionOutcome.INCORRECT,QuestionOutcome.SKIPPED)
        variants.zip(outcomes).forEach {(answer,outcome)->assertEquals(outcome,StudyResultRules.outcome(q,answer))}
        for(origin in listOf(AnswerEvidence.INFERRED,AnswerEvidence.CLOUD_AI,AnswerEvidence.LOCAL_AI,AnswerEvidence.UNKNOWN))assertEquals(QuestionOutcome.UNVERIFIED,StudyResultRules.outcome(q.copy(evidence=origin),setOf(0,2)))
    }
    @Test fun mistakePracticeDoesNotIncludeAssumptionsOrRewriteQuestionBank() {
        val unknown=q.copy(id="u",text="Неизвестный",evidence=AnswerEvidence.INFERRED)
        val original=quiz.copy(questions=listOf(q,unknown));val answers=listOf(setOf(0),setOf(1))
        val practice=StudyResultRules.mistakes(original,answers)!!
        assertEquals(quiz.id,practice.id);assertEquals(listOf(q),practice.questions);assertEquals(2,original.questions.size)
        assertEquals(QuizScore(1,0,0,0),QuizScorer.scoreSelections(practice,listOf(setOf(0,2))))
    }
    @Test fun cardSessionCountsUniqueSavedReviewsAndCannotFinishOnFailedSave() {
        val ids=listOf("a","b");var reviewed=emptyList<String>()
        reviewed=CardSessionRules.afterSave(reviewed,"a",false);assertEquals(0,CardSessionRules.count(ids,reviewed))
        repeat(2){reviewed=CardSessionRules.afterSave(reviewed,"a",true)}
        assertEquals(1,CardSessionRules.count(ids,reviewed));assertFalse(CardSessionRules.completed(ids,reviewed))
        reviewed=CardSessionRules.afterSave(reviewed,"b",true);assertTrue(CardSessionRules.completed(ids,reviewed))
        assertFalse(CardSessionRules.completed(emptyList(),reviewed));assertFalse(CardSessionRules.completed(ids+"c",reviewed))
    }
    @Test fun successfulPartHistoryIsPersistentAndSeparatedBySettings() {
        val first=MaterialSeries.key("doc","QUIZ","topic","text","medium","5")
        val other=MaterialSeries.key("doc","CARDS","topic","text","medium","5")
        assertNotEquals(first,other);assertNotEquals(first,MaterialSeries.key("other-doc","QUIZ","topic","text","medium","5"))
        val store=AiDraftStore(File(folder.root,"ai-draft.json"));val draft=AiDraft(completed=mapOf(first to listOf(0,2),other to listOf(1)))
        store.save(draft);assertEquals(draft,store.load());val original=File(folder.root,"ai-draft.json").readText()
        assertThrows(IllegalArgumentException::class.java){store.save(draft.copy(completed=mapOf(first to listOf(0,0))))}
        assertEquals(original,File(folder.root,"ai-draft.json").readText())
    }
    @Test fun invalidBanksAreReportedAndFilesAreNotModified() {
        val dir=folder.newFolder("banks");val good=File(dir,"good.json").apply {writeText(QuizJson.serialize(quiz))}
        val bad=File(dir,"broken.json").apply {writeText("{broken")}
        val encoding=File(dir,"encoding.json").apply {writeBytes(byteArrayOf(0xc3.toByte(),0x28))}
        val before=listOf(good,bad,encoding).associateWith {it.readBytes()}
        val loaded=QuizLibraryReader.read(dir);assertEquals(listOf(quiz),loaded.quizzes);assertEquals(2,loaded.problems.size)
        before.forEach {(file,bytes)->assertArrayEquals(bytes,file.readBytes())}
    }
    @Test fun staleOrCancelledSnapshotCannotReviveFinishedAttemptOrOverwriteNewAnswers() {
        val file=File(folder.root,"active.json");val old=ActiveAttemptStore(file);val reopened=ActiveAttemptStore(file)
        val value=ActiveAttempt(quiz,listOf(0),0,"attempt",100,200,1,listOf(setOf(0,2)))
        val ticket=old.ticket();old.save(value,ticket);reopened.clear()
        assertFalse(old.save(value,ticket));assertNull(reopened.load())
        val latest=value.copy(answers=listOf(1),selections=listOf(setOf(1)))
        reopened.save(latest)
        assertThrows(CancellationException::class.java){old.save(value,old.ticket()){throw CancellationException()}}
        assertEquals(latest,reopened.load())
    }
    @Test fun sourceCopyProgressCountsRealBytesAndKeepsAllText() {
        val bytes="Русский и Қазақша мәтін".repeat(500).toByteArray();val counts=mutableListOf<Long>()
        val record=MaterialStore(folder.root).import("text.txt","txt",bytes.inputStream(),copied={counts+=it}){MaterialText(bytes.toString(Charsets.UTF_8))}
        assertEquals(bytes.size.toLong(),record.bytes);assertEquals(bytes.size.toLong(),counts.last());assertTrue(counts.zipWithNext().all {it.first<=it.second})
    }
}
