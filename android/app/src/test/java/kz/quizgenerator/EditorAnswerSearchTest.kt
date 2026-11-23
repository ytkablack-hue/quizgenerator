package kz.quizgenerator
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID
class EditorAnswerSearchTest {
    @get:Rule val folder=TemporaryFolder()
    private fun bank()=Quiz(UUID.randomUUID().toString(),"Импортированный банк",listOf(QuizQuestion("q","Вопрос?",listOf("А","Б"),null,null)))
    @Test fun createsOneLinkedScopeAndRepeatedOpeningDoesNotDuplicateOrChangeBank() {
        val q=bank();val store=StudySetStore(folder.root);val scope=AnswerSearchScope.ensure(store,q,null)
        assertEquals(listOf(q.id),scope.quizIds);assertEquals(scope,AnswerSearchScope.ensure(store,q,null));assertEquals(1,store.list().size)
        assertEquals(AnswerEvidence.UNKNOWN,q.questions.single().evidence)
    }
    @Test fun reusesRealSetAndPreservesMaterialsNotesAndOtherQuizIds() {
        val q=bank();val store=StudySetStore(folder.root);val old=store.create("Курс");val material=UUID.randomUUID().toString();val other=UUID.randomUUID().toString()
        store.update(old.id){it.copy(notes="Мой конспект",materialIds=listOf(material),quizIds=listOf(other))}
        val result=AnswerSearchScope.ensure(store,q,old.id)
        assertEquals(old.id,result.id);assertEquals(listOf(other,q.id),result.quizIds);assertEquals("Мой конспект",result.notes);assertEquals(listOf(material),result.materialIds)
    }
    @Test fun archivedSetsAreNotUnarchivedOrOverwritten() {
        val q=bank();val store=StudySetStore(folder.root);val old=AnswerSearchScope.ensure(store,q,null);store.update(old.id){it.copy(archived=true)}
        val next=AnswerSearchScope.ensure(store,q,old.id)
        assertNotEquals(old.id,next.id);assertTrue(store.list().first {it.id==old.id}.archived);assertFalse(next.archived)
        assertEquals(next,AnswerSearchScope.ensure(store,q,null))
    }
}
