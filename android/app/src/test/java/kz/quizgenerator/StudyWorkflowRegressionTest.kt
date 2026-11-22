package kz.quizgenerator

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class StudyWorkflowRegressionTest {
    @get:Rule val folder=TemporaryFolder()
    private val set=UUID.randomUUID().toString()
    private fun quizReply(n: Int,offset: Int=0)=JSONObject(QuizJson.serialize(Quiz(UUID.randomUUID().toString(),"Курс",List(n){QuizQuestion("q$it","Задача ${offset+it}?",listOf("Да","Нет"),0,null)})))
    private fun lecture(input: JSONObject): JSONObject {
        val source=input.optString("material")
        val section=JSONObject().put("heading","Механика").put("paragraphs",JSONArray(listOf("Сила изменяет скорость тела.")))
            .put("subsections",JSONArray().put(JSONObject().put("heading","Применение").put("paragraphs",JSONArray(listOf("Учитываем массу и ускорение.")))))
            .put("definitions",JSONArray().put(JSONObject().put("term","Сила").put("meaning","Мера взаимодействия")))
            .put("examples",JSONArray(listOf("Разгон тележки"))).put("formulas",JSONArray(listOf("F = m·a")))
            .put("quotes",JSONArray(if(source.isBlank())emptyList<String>() else listOf(source)))
        return JSONObject().put("title","Законы Ньютона").put("introduction","Введение в механику.").put("sections",JSONArray().put(section))
            .put("conclusion","Итоги изучения.").put("terms",JSONArray().put(JSONObject().put("term","Масса").put("meaning","Инертность тела")))
            .put("complete",true).put("sectionIndex",input.getInt("sectionIndex"))
    }
    @Test fun importsLargeBanksWithExactReportOriginalNumbersAndRepair() {
        for(count in listOf(200,500)) {
            val source=(1..count).joinToString("\n") {i->"<question>Вопрос $i?\n<variant>Ответ А"+if(i in listOf(2,51,199))"" else "\n<variant>Ответ Б"}
            val q=QuizTextParser.parse(source,"Университет").quiz
            assertEquals(count,q.sourceDetectedCount);assertEquals(count-3,q.questions.size)
            assertEquals(listOf(2,51,199),q.importProblems.mapNotNull {it.number}.distinct())
            assertEquals((1..count).filter {it !in listOf(2,51,199)},q.questions.map {it.sourceNumber})
            assertEquals(q,QuizJson.parse(QuizJson.serialize(q)))
            val issue=q.importProblems.first();val repaired=ImportReview.repair(issue,q.title)
            assertEquals(2,repaired.sourceNumber);assertTrue(repaired.text.contains("Вопрос 2"))
            val (ordered,index)=ImportReview.insert(q.questions,repaired);assertEquals(1,index);assertEquals(listOf(1,2,3),ordered.take(3).map {it.sourceNumber})
            assertEquals(3,q.importProblems.size) // Opening an editor never silently dismisses the issue.
            val material=MaterialStore(folder.newFolder("source$count"));val bytes=source.toByteArray(Charsets.UTF_8)
            val saved=material.import("bank.txt","txt",bytes.inputStream()){MaterialText(source)}
            assertArrayEquals(bytes,material.source(saved.id).readBytes());assertEquals(source,material.text(saved.id))
        }
    }
    @Test fun sourceMultiKeysUnknownAnswersAndOldJsonRemainCompatible() {
        val q=QuizTextParser.parse("1. Чётные?\nA) 2\nB) 3\nC) 4\nПравильные ответы:\n1: A,C","Ключи").quiz
        assertEquals(setOf(0,2),q.questions.single().correctIndices.toSet());assertEquals(AnswerEvidence.SOURCE,q.questions.single().evidence)
        val legacy=Quiz(UUID.randomUUID().toString(),"Старый",listOf(QuizQuestion("q","Да?",listOf("А","Б"),null,null)))
        assertFalse(QuizJson.serialize(legacy).contains("sourceDetectedCount"));assertEquals(legacy,QuizJson.parse(QuizJson.serialize(legacy)))
    }
    @Test fun lectureTopicTextDocumentContractsRenderStructureAndGroundedQuotes() {
        for(mode in AiInputMode.entries) {
            val p=BatchPlanning.lecture("alice",set,"Механика",mode,"Законы Ньютона",if(mode==AiInputMode.TOPIC)"" else "Сила изменяет скорость тела.","university","brief")
            val input=JSONObject(p.parts.single().payload);val text=StudyContract.lecture(lecture(input),input)
            assertTrue(text.contains("## Введение"));assertTrue(text.contains("### Применение"));assertTrue(text.contains("F = m·a"));assertTrue(text.contains("## Термины"))
            assertEquals(mode!=AiInputMode.TOPIC,text.contains("> Из выбранного источника:"))
            for(bad in listOf(lecture(input).put("complete",false),lecture(input).put("sectionIndex",2)))assertThrows(IllegalArgumentException::class.java){StudyContract.lecture(bad,input)}
            val fake=lecture(input);fake.getJSONArray("sections").getJSONObject(0).put("quotes",JSONArray(listOf("Неподтверждённая цитата")))
            assertThrows(IllegalArgumentException::class.java){StudyContract.lecture(fake,input)}
        }
    }
    @Test fun detailedLectureIsPartialUntilEveryPartIsDurablyStored()=runBlocking {
        val p=BatchPlanning.lecture("alice",set,"Механика",AiInputMode.TOPIC,"Законы Ньютона","","university","detailed").accepted(1)
        val store=BatchJobStore(folder.root,"alice");store.save(p);var now=100000L
        repeat(3){i->val job=BatchEngine(store){now}.step(p.id){_,body,_->lecture(body)};assertEquals(i+1,job.actual);assertEquals(i==2,job.lecture().complete);now+=22000}
        assertTrue(BatchJobStore(folder.root,"alice").load(p.id).lecture().complete)
    }
    @Test fun cancelledInFlightRequestPreservesAnUncertainCheckpoint()=runBlocking {
        val p=BatchPlanning.quiz("alice",set,"Курс",AiInputMode.TOPIC,"Физика","",1,"medium").accepted(1)
        val store=BatchJobStore(folder.root,"alice");store.save(p);var calls=0
        try {BatchEngine(store){100000}.step(p.id){_,_,_->calls++;throw kotlinx.coroutines.CancellationException("cancel")};fail()}catch(_: kotlinx.coroutines.CancellationException){}
        assertEquals(BatchStatus.UNCERTAIN,store.load(p.id).status);assertEquals(BatchPartStatus.UNKNOWN,store.load(p.id).parts.single().status)
        BatchEngine(store){122000}.step(p.id){_,_,_->calls++;quizReply(1)};assertEquals(1,calls)
    }
    @Test fun failedDiskWriteAfterResponseCannotTurnIntoAutomaticResend()=runBlocking {
        val p=BatchPlanning.quiz("alice",set,"Курс",AiInputMode.TOPIC,"Физика","",1,"medium").accepted(1)
        val store=BatchJobStore(folder.root,"alice");store.save(p)
        val destination=File(folder.root,"batch_jobs/${p.id}.json");val checkpoint=folder.newFile("checkpoint.json")
        var calls=0
        try {BatchEngine(store){100000}.step(p.id){_,_,_->calls++;destination.copyTo(checkpoint,true);check(destination.delete());check(destination.mkdir());quizReply(1)};fail()}catch(_: Exception){}
        check(destination.delete());checkpoint.copyTo(destination)
        val restored=store.restore(store.load(p.id));assertEquals(BatchStatus.UNCERTAIN,restored.status);store.save(restored)
        BatchEngine(store){122000}.step(p.id){_,_,_->calls++;quizReply(1)};assertEquals(1,calls)
    }
    @Test fun publicationPreservesManualEditsAndNeverResurrectsDeletedQuestions() {
        val p=BatchPlanning.quiz("alice",set,"Курс",AiInputMode.TOPIC,"Физика","",2,"medium").accepted(1)
        val original=QuizJson.parse(quizReply(2).toString());val job=p.copy(status=BatchStatus.COMPLETE,parts=p.parts.map {it.copy(status=BatchPartStatus.DONE,response=QuizJson.serialize(original))})
        val saved=job.quiz();val edited=saved.copy(title="Моё название",questions=listOf(saved.questions[0].copy(text="Моя правка")))
        val merged=BatchPublication.quiz(job.copy(publishedIds=saved.questions.map {it.id}.toSet()),edited)
        assertEquals(edited,merged)
    }
    @Test fun answerPlansIncludeContextAdaptSizesAndNeverSendConfirmedKeys() {
        val bank=Quiz(UUID.randomUUID().toString(),"Курс",(1..200).map {QuizQuestion("q$it","Вопрос $it?",listOf("А","Б"),null,null,sourceExcerpt="Общий контекст")})
        val p=BatchPlanning.answers("alice",set,bank);assertEquals(200,p.target)
        p.parts.forEach {assertEquals("Общий контекст",JSONObject(it.payload).getString("context"));AiRequestRules.validate(it.route,JSONObject(it.payload))}
        val large=bank.copy(questions=bank.questions.take(10).map {it.copy(sourceExcerpt="я".repeat(5000)+it.id)})
        assertTrue(BatchPlanning.answers("alice",set,large).parts.size>=5)
    }
    @Test fun wrongAnswerIdStopsWithoutChangingOriginalAndEmptyUncertainStaysUngraded()=runBlocking {
        val bank=Quiz(UUID.randomUUID().toString(),"Курс",listOf(QuizQuestion("q","Вопрос?",listOf("А","Б"),null,null)))
        val p=BatchPlanning.answers("alice",set,bank).accepted(1);val store=BatchJobStore(folder.root,"alice");store.save(p)
        fun answer(id: String)=JSONObject().put("answers",JSONArray().put(JSONObject().put("id",id).put("indices",JSONArray()).put("uncertain",true).put("explanation","Данных недостаточно")))
        val j=BatchEngine(store){100000}.step(p.id){_,_,_->answer("foreign")};assertEquals(BatchStatus.REVIEW,j.status);assertEquals(0,j.actual)
        val good=p.copy(parts=p.parts.map {it.copy(status=BatchPartStatus.DONE,response=answer("q").toString())})
        val changed=BatchAnswerMerge.apply(good,bank);assertEquals(AnswerEvidence.UNKNOWN,changed.questions.single().evidence);assertEquals(1,QuizScorer.score(changed,listOf(0)).ungraded)
    }
    @Test fun selectedSetExportContainsItsLecturesAndJobsButNotOtherSetsOrAuth() {
        val root=folder.root;val other=UUID.randomUUID().toString()
        for(id in listOf(set,other)) {
            LectureStore(root).save(Lecture(UUID.randomUUID().toString(),id,if(id==set)"Лекция" else "OTHER_PRIVATE","Локальный материал",null,null,true))
            BatchJobStore(root,"alice").save(BatchPlanning.quiz("alice",id,if(id==set)"Курс" else "OTHER_PRIVATE",AiInputMode.TOPIC,"Физика","",1,"medium").accepted(1))
        }
        File(root,"auth-session.enc").writeText("PRIVATE_SESSION");File(root,"batch-rate.json").writeText("PRIVATE_RATE")
        val bytes=ByteArrayOutputStream();StudySetExport.write(root,StudySet(set,"Набор"),StudyData(),bytes)
        val entries=mutableMapOf<String,String>();ZipInputStream(bytes.toByteArray().inputStream()).use {zip->while(true){val e=zip.nextEntry ?: break;entries[e.name]=zip.readBytes().toString(Charsets.UTF_8)}}
        assertEquals(4,entries.size);assertEquals(1,entries.keys.count {it.startsWith("lectures/")});assertEquals(1,entries.keys.count {it.startsWith("batch_jobs/")})
        assertTrue(entries.values.none {it.contains("OTHER_PRIVATE") || it.contains("PRIVATE_SESSION") || it.contains("PRIVATE_RATE")})
        val backup=ByteArrayOutputStream();LibraryBackup.write(root,backup);assertFalse(backup.toString("UTF-8").contains("PRIVATE_SESSION"))
    }
}
