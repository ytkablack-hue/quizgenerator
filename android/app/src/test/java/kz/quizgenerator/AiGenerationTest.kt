package kz.quizgenerator
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
class AiGenerationTest {
 @get:Rule val dir=TemporaryFolder()
 private fun build(mode: AiInputMode,topic: String="Законы Ньютона",text: String="Мой текст",doc: String="НЕ ОТПРАВЛЯТЬ ДОКУМЕНТ",count: Int=15)=AiGeneration.build(mode,topic,text,doc,"Инструкция","medium",count)
 @Test fun topicAndTopicCardsNeverAttachMaterial() {repeat(2){val r=build(AiInputMode.TOPIC);assertEquals("Законы Ньютона",r.getString("topic"));assertEquals("",r.getString("material"));AiRequestRules.validate(if(it==0)"quizzes" else "flashcards",r)}}
 @Test fun explicitTopicSurvivesSetAutoSelectionWithAttachedDocuments() {assertEquals("old-doc",AiGeneration.initialDocument(AiInputMode.TOPIC,"old-doc",listOf("new-doc")));assertNull(AiGeneration.initialDocument(AiInputMode.TEXT,null,listOf("new-doc")));assertEquals("new-doc",AiGeneration.initialDocument(AiInputMode.DOCUMENT,null,listOf("new-doc")))}
 @Test fun ownTextAndDocumentAreSeparate() {assertEquals("Мой текст",build(AiInputMode.TEXT).getString("material"));assertTrue(build(AiInputMode.DOCUMENT).getString("material").contains("Инструкция"));assertFalse(build(AiInputMode.DOCUMENT).getString("material").contains("Мой текст"))}
 @Test fun switchingAndRestoringKeepsExplicitModeAndDocumentId() {val store=AiDraftStore(File(dir.root,"draft.json"));val original=AiDraft(sourceId=java.util.UUID.randomUUID().toString(),ownText="Текст",extra="Подсказка");for(mode in AiInputMode.entries){val value=original.copy(inputMode=mode);store.save(value);assertEquals(value,store.load())}}
 @Test fun blankAndCountBoundariesAreValidatedBeforeNetwork() {for(mode in AiInputMode.entries)assertThrows(IllegalArgumentException::class.java){build(mode,"","","",1)};for(n in listOf(0,26))assertThrows(IllegalArgumentException::class.java){build(AiInputMode.TOPIC,count=n)};for(n in listOf(1,25))assertEquals(n,build(AiInputMode.TOPIC,count=n).getInt("count"))}
 @Test fun oldDraftMigrationKeepsOwnTextAndDocumentMode() {val file=File(dir.root,"draft.json");file.writeText("""{"extra":"Старый текст"}""");assertEquals(AiInputMode.TEXT,AiDraftStore(file).load().inputMode);assertEquals("Старый текст",AiDraftStore(file).load().ownText);file.writeText("""{"sourceId":"00000000-0000-0000-0000-000000000001"}""");assertEquals(AiInputMode.DOCUMENT,AiDraftStore(file).load().inputMode)}
 @Test fun oversizedOwnTextIsNotSilentlyTruncated() {assertThrows(IllegalArgumentException::class.java){build(AiInputMode.TEXT,text="Я".repeat(12001))};assertEquals(12000,build(AiInputMode.TEXT,text="Я".repeat(12000)).getString("material").length)}
}
