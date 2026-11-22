package kz.quizgenerator
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class AnswerInferenceTest {
    private val q=QuizQuestion("q","Столица Казахстана?",listOf("Астана","Алматы"),1,"Секретный ключ",evidence=AnswerEvidence.SOURCE)
    private class Fake(val result: AnswerInferenceResult): AnswerInferenceProvider {
        override val revision="test-only-v1"
        override val evidence=result.evidence
        var calls=0
        var received: AnswerInferenceInput?=null
        override suspend fun infer(input: AnswerInferenceInput): AnswerInferenceResult {calls++;received=input;return result}
    }
    private fun answer(evidence: AnswerEvidence=AnswerEvidence.LOCAL_AI,uncertain: Boolean=false,indices: Set<Int> = setOf(0)) =
        AnswerInferenceResult(indices,"Нужна проверка",uncertain,evidence,"test-only-v1")
    @Test fun inputExcludesImportedKeysAndDoesNotChangeQuestion()=runBlocking {
        val fake=Fake(answer());val original=q.copy()
        val result=AnswerInferenceService(fake).suggest(q)
        assertEquals(AnswerInferenceInput(q.text,q.options),fake.received)
        assertFalse(fake.received!!.prompt().contains("Секретный ключ"));assertEquals(original,q)
        val suggestion=q.copy(correctIndex=result.indices.single(),evidence=result.evidence)
        assertEquals(QuizScore(0,0,0,1),QuizScorer.score(Quiz("x","x",listOf(suggestion)),listOf(0)))
    }
    @Test fun cloudRequiresExplicitConsentBeforeProviderCall()=runBlocking {
        val fake=Fake(answer(AnswerEvidence.CLOUD_AI))
        assertThrows(IllegalArgumentException::class.java) {runBlocking {AnswerInferenceService(fake).suggest(q)}}
        assertEquals(0,fake.calls);AnswerInferenceService(fake).suggest(q,true);assertEquals(1,fake.calls)
    }
    @Test fun uncertainAndInvalidResultsCannotEstablishSampleRule() {
        for(result in listOf(answer(uncertain=true),answer(indices=setOf(0,1)),answer(indices=setOf(2)),answer(indices=emptySet()))) {
            assertThrows(IllegalArgumentException::class.java) {runBlocking {InferenceSampleAdapter(Fake(result)).verify(listOf(q))}}
        }
    }
    @Test fun providerFailureAndCancellationPropagateWithoutFallback() {
        val provider=object: AnswerInferenceProvider {
            override val revision="test-only-v1";override val evidence=AnswerEvidence.LOCAL_AI
            override suspend fun infer(input: AnswerInferenceInput): AnswerInferenceResult=throw CancellationException()
        }
        assertThrows(CancellationException::class.java) {runBlocking {AnswerInferenceService(provider).suggest(q)}}
    }
}
