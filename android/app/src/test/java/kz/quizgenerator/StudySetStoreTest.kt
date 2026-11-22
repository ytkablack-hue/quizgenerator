package kz.quizgenerator
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StudySetStoreTest {
    @get:Rule val folder=TemporaryFolder()
    private val quiz=Quiz(java.util.UUID.randomUUID().toString(),"Старый тест",listOf(QuizQuestion("q","Вопрос?",listOf("Да","Нет"),0,null)))
    @Test fun legacyIndexIsReadOnlyAndOldDataRemainByteIdentical() {
        File(folder.root,"study.json").writeText("original-study")
        val file=File(folder.root,"quiz.json");file.writeText(QuizJson.serialize(quiz));val original=file.readBytes()
        val store=StudySetStore(folder.root);assertTrue(store.list().isEmpty())
        val virtual=WorkspaceIndex.build(store.list(),listOf(quiz),emptyList(),emptyList()).single()
        assertFalse(File(folder.root,"sets.json").exists());assertEquals(listOf(quiz.id),virtual.quizIds)
        store.ensure(virtual);store.update(virtual.id) {it.copy(title="Новое название",notes="Мои заметки")}
        assertArrayEquals(original,file.readBytes());assertEquals("original-study",File(folder.root,"study.json").readText())
        assertEquals("Мои заметки",StudySetStore(folder.root).list().single().notes)
    }
    @Test fun archiveAndRestorePreserveFilesAndDoNotResurfaceAsLegacy() {
        val store=StudySetStore(folder.root)
        val item=WorkspaceIndex.build(emptyList(),listOf(quiz),emptyList(),emptyList()).single();store.ensure(item)
        store.update(item.id) {it.copy(archived=true)}
        val index=WorkspaceIndex.build(store.list(),listOf(quiz),emptyList(),emptyList())
        assertEquals(1,index.size);assertTrue(index.single().archived)
        store.update(item.id) {it.copy(archived=false)};assertFalse(store.list().single().archived)
    }
    @Test fun linksAreIdempotentAndIsolatedPerAccount() {
        val alice=StudySetStore(folder.newFolder("alice"));val bob=StudySetStore(folder.newFolder("bob"))
        val set=alice.create("Набор");val doc=java.util.UUID.randomUUID().toString()
        repeat(2){alice.linkMaterial(set.id,doc);alice.linkQuiz(set.id,quiz.id);alice.linkCards(set.id,listOf("card"))}
        val restored=alice.list().single();assertEquals(listOf(doc),restored.materialIds);assertEquals(listOf(quiz.id),restored.quizIds);assertEquals(listOf("card"),restored.cardIds)
        assertTrue(bob.list().isEmpty())
    }
    @Test fun malformedAndOversizedUpdatesDoNotOverwriteExistingMetadata() {
        val store=StudySetStore(folder.root);val set=store.create("Набор");val file=File(folder.root,"sets.json");val original=file.readText()
        assertThrows(IllegalArgumentException::class.java){store.update(set.id){it.copy(materialIds=listOf("../bob"))}}
        assertThrows(IllegalArgumentException::class.java){store.update(set.id){it.copy(notes="x".repeat(20001))}}
        assertEquals(original,file.readText());file.writeText("broken")
        assertThrows(org.json.JSONException::class.java){store.create("Другой")};assertEquals("broken",file.readText())
    }
    @Test fun practiceUsesOriginalConfirmedQuestionsAndKeepsOriginalBankId() {
        val unknown=quiz.questions.single().copy(id="unknown",text="Неизвестный?",evidence=AnswerEvidence.CLOUD_AI)
        val bank=quiz.copy(questions=quiz.questions+unknown)
        val history=listOf(Attempt("a",quiz.id,"Тест",0,QuizScore(0,1,0,1),listOf(Mistake("Вопрос?","Нет","Да",null),Mistake("Неизвестный?","Нет","Да",null))))
        val practice=WorkspaceIndex.practice(bank,history)!!
        assertEquals(quiz.id,practice.id);assertEquals(listOf(quiz.questions.single()),practice.questions);assertEquals(2,bank.questions.size)
    }
    @Test fun looseManualCardsStayDiscoverableAndCountsAreReal() {
        val card=Flashcard("c","Что?","Ответ")
        val set=WorkspaceIndex.build(emptyList(),emptyList(),emptyList(),listOf(card)).single()
        val data=StudyData(cards=listOf(card));assertEquals(listOf(card),WorkspaceIndex.cards(set,data));assertTrue(WorkspaceIndex.attempts(set,data).isEmpty())
    }
}
