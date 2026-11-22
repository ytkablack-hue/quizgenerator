package kz.quizgenerator
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import java.util.concurrent.CancellationException

class StudySetExportTest {
    @get:Rule val folder=TemporaryFolder()
    @Test fun zipContainsOnlySelectedSetMaterialQuestionsAndHistory() {
        val root=folder.root;val materials=MaterialStore(root)
        val a=materials.import("a.txt","txt","SOURCE_A".byteInputStream()){MaterialText("Текст A")}
        materials.import("b.txt","txt","OTHER_SENSITIVE".byteInputStream()){MaterialText("OTHER_SENSITIVE")}
        val q=Quiz(java.util.UUID.randomUUID().toString(),"Мой тест",listOf(QuizQuestion("q","Что?",listOf("Да","Нет"),0,null)))
        File(root,"quiz_banks").mkdirs();File(root,"quiz_banks/"+q.id+".json").writeText(QuizJson.serialize(q))
        val c=Flashcard("my-card","Мой вопрос","Ответ",sourceQuizId=q.id)
        val other=Flashcard("other-card","OTHER_SENSITIVE","OTHER_SENSITIVE")
        val attempt=StudyLogic.complete("mine",q,listOf(0),1)
        val foreign=attempt.copy(id="other",quizId="other",title="OTHER_SENSITIVE")
        val data=StudyData(cards=listOf(c,other),attempts=listOf(attempt,foreign))
        StudyStore(File(root,"study.json")).save(data);val original=File(root,"study.json").readText()
        File(root,"auth-session.enc").writeText("PRIVATE_SESSION")
        val set=StudySet(java.util.UUID.randomUUID().toString(),"Мой набор",listOf(a.id),listOf(q.id),notes="Мои заметки")
        val bytes=ByteArrayOutputStream();StudySetExport.write(root,set,data,bytes)
        val entries=mutableMapOf<String,String>()
        ZipInputStream(bytes.toByteArray().inputStream()).use {zip->while(true){val entry=zip.nextEntry ?: break;entries[entry.name]=String(zip.readBytes(),Charsets.UTF_8)}}
        assertEquals(6,entries.size);assertTrue(entries.containsKey("quiz_banks/"+q.id+".json"))
        assertTrue(entries.values.none {it.contains("OTHER_SENSITIVE") || it.contains("PRIVATE_SESSION")})
        assertTrue(entries.getValue("study.json").contains("my-card"));assertTrue(entries.getValue("set.json").contains("Мои заметки"))
        assertEquals(original,File(root,"study.json").readText());assertTrue(root.listFiles().orEmpty().none {it.name.startsWith(".set-export-")})
    }
    @Test fun cancellationKeepsOriginalFilesAndCleansOnlyOwnTemporaryFile() {
        val root=folder.root;val marker=File(root,"user-file.txt").apply {writeText("unchanged")}
        val set=StudySet(java.util.UUID.randomUUID().toString(),"Набор")
        assertThrows(CancellationException::class.java){StudySetExport.write(root,set,StudyData(),ByteArrayOutputStream()){throw CancellationException()}}
        assertEquals("unchanged",marker.readText());assertEquals(listOf("user-file.txt"),root.listFiles().orEmpty().map {it.name})
    }
    @Test fun utf16PreviewBoundariesDoNotBreakEmojiAndHaveCompleteCoverage() {
        val text="a".repeat(3999)+"😃"+"я".repeat(6000)
        val pages=(0 until (text.length+3999)/4000).map {i->text.substring(PreviewTextBoundary.at(text,i*4000),PreviewTextBoundary.at(text,(i+1)*4000))}
        assertEquals(text,pages.joinToString(""));assertTrue(pages.none {it.isNotEmpty() && Character.isLowSurrogate(it.first())})
    }
    @Test fun emptyPdfPagesRequireExplicitReportingAndStrictModeStillRejects() {
        val out=mutableListOf<String>();val empty=mutableListOf<Int>()
        PdfTextPages.consume(3,{}, {if(it==1)emptyList() else listOf("Страница "+it)}, {out+=it}, {empty+=it})
        assertEquals(listOf(1),empty);assertEquals(listOf("Страница 0","Страница 2"),out)
        assertThrows(IllegalArgumentException::class.java){PdfTextPages.consume(3,{}, {if(it==1)emptyList() else listOf("Текст")}, {})}
    }
}
