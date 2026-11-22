package kz.quizgenerator
import org.junit.Assert.*
import org.junit.Test
class HomePresentationTest {
    private val card=Flashcard("card","Вопрос","Ответ")
    private fun attempt(id: String,at: Long,score: QuizScore)=Attempt(id,"quiz","Тест",at,score,emptyList())
    @Test fun noActivityDoesNotInventProgressOrGoal() {
        assertNull(homeProgress(emptyList(),emptyList()))
        assertNull(homeProgress(listOf(card),emptyList()))
    }
    @Test fun reviewsCountActualCardsRatherThanReviewPressesOrMastery() {
        val value=homeProgress(listOf(card.copy(reviews=20),card.copy(id="second")),emptyList())!!
        assertEquals("Повторено карточек",value.label);assertEquals(1,value.completed);assertEquals(2,value.total);assertEquals(.5f,value.fraction,0f)
    }
    @Test fun unknownKeysCannotCreateFakePercentage() {
        assertNull(homeProgress(emptyList(),listOf(attempt("unknown",2,QuizScore(0,0,0,12)))))
    }
    @Test fun lastTestIsNotTheBestHistoricalTest() {
        val value=homeProgress(emptyList(),listOf(attempt("old",1,QuizScore(8,2,0,0)),attempt("last",2,QuizScore(0,1,1,7))))!!
        assertEquals("Последний тест",value.label);assertEquals(0,value.completed);assertEquals(2,value.total);assertEquals(0f,value.fraction,0f)
    }
    @Test fun searchAndTrashKeepRealCountsOrderAndOriginalMetadata() {
        val first=StudySet("a","Очень длинное название: Қазақша физика и механика",lastOpened=1)
        val last=StudySet("b","ФИЗИКА",lastOpened=3);val archived=StudySet("c","Физика",archived=true)
        val original=listOf(first,last,archived)
        assertEquals(listOf(last,first),homeFilteredSets(original," физика ",false))
        assertEquals(listOf(archived),homeFilteredSets(original,"",true));assertTrue(archived.archived)
        assertTrue(homeFilteredSets(original,"химия",false).isEmpty());assertEquals(3,original.size)
    }
}
