package kz.quizgenerator
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.runBlocking

class MaterialSlicesTest {
    @get:Rule val folder=TemporaryFolder()
    @Test fun largeProseCoverageIsExactIncludingUnicodeAndWhitespace() {
        val text=("Ә Ғ Қ Ң Ө Ұ Ү Һ І 😃 Русский текст.\r\n\r\n".repeat(3000))
        val parts=MaterialSlices.plan(text)
        assertTrue(parts.size>1);assertEquals(text,parts.joinToString(""){it.text(text)})
        assertTrue(parts.all {it.end-it.start<=MaterialSlices.LIMIT})
        assertTrue(parts.none {it.start<text.length && Character.isLowSurrogate(text[it.start])})
    }
    @Test fun eightHundredTaggedQuestionsKeepTheirBoundaries() {
        val text=(1..800).joinToString("\r\n") {"<question> Вопрос "+it+"?\r\n<variant> Да\r\n<variant> Нет"}
        val parts=MaterialSlices.plan(text,2000)
        assertEquals(text,parts.joinToString(""){it.text(text)})
        assertEquals(800,parts.sumOf {QuizTextParser.parse(it.text(text),"x").quiz.questions.size})
        assertTrue(parts.none {it.oversized})
    }
    @Test fun numericOptionsAreNotQuestionBoundaries() {
        val text=(1..100).joinToString("\n") {it.toString()+". Вопрос "+it+"?\n1) Первый\n2) Второй\n3) Третий"}
        val parts=MaterialSlices.plan(text,300)
        assertEquals(100,parts.sumOf {QuizTextParser.parse(it.text(text),"x").quiz.questions.size})
        assertEquals(text,parts.joinToString(""){it.text(text)})
    }
    @Test fun oversizedQuestionIsNotSilentlyCut() {
        val text="<question> "+"длинный текст ".repeat(1600)+"\n<variant> Да\n<variant> Нет"
        val part=MaterialSlices.plan(text).single();assertTrue(part.oversized);assertEquals(text,part.text(text))
        assertThrows(IllegalArgumentException::class.java) {MaterialSlices.compose(part.text(text),"")}
    }
    @Test fun draftRestoresAttachmentAndKeepsManualTextSeparate() {
        val store=AiDraftStore(File(folder.root,"ai-draft.json"))
        val value=AiDraft(java.util.UUID.randomUUID().toString(),"Механика","Мои указания",3,"hard","12",5)
        store.save(value);assertEquals(value,AiDraftStore(File(folder.root,"ai-draft.json")).load())
        val original=File(folder.root,"ai-draft.json").readText()
        assertThrows(IllegalArgumentException::class.java) {store.save(value.copy(sourceId="../bob"))}
        assertEquals(original,File(folder.root,"ai-draft.json").readText())
        assertEquals("Мои указания",store.load().extra)
    }
    @Test fun payloadChecksMirrorServerAndNeverSendOversizedMaterial() {
        val valid=JSONObject().put("topic","Тема").put("material",MaterialSlices.compose("ф".repeat(9800),"я".repeat(2000))).put("count",25).put("difficulty","medium")
        AiRequestRules.validate("quizzes",valid)
        assertThrows(IllegalArgumentException::class.java) {AiRequestRules.validate("quizzes",JSONObject(valid.toString()).put("count",1.5))}
        assertThrows(IllegalArgumentException::class.java) {AiRequestRules.validate("quizzes",JSONObject(valid.toString()).put("material","a".repeat(12001)))}
        assertThrows(IllegalArgumentException::class.java) {AiRequestRules.validate("quizzes",JSONObject(valid.toString()).put("material","\u0001".repeat(12000)))}
        assertThrows(IllegalArgumentException::class.java) {runBlocking {AiClient(StudySettings(),authToken={error("AUTH MUST NOT BE CALLED")}).quiz("x","a".repeat(12001),"medium",5)}}
    }
    @Test fun reviewContainsEveryMistakeAndChunkPlanningCanBeCancelled() {
        val attempt=Attempt("a","q","История",0,QuizScore(0,1000,0,0),List(1000){Mistake("Вопрос "+it+"?","Мой ответ","Правильный ответ","Подробное объяснение")})
        val text=AiClient.reviewText(attempt);assertTrue(text.length>12000);assertTrue(text.contains("Вопрос 999?"))
        assertThrows(java.util.concurrent.CancellationException::class.java) {MaterialSlices.plan(text,check={throw java.util.concurrent.CancellationException()})}
    }
}
