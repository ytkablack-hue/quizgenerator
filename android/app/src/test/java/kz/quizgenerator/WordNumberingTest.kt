package kz.quizgenerator
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WordNumberingTest {
    private val ns="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private fun archive(document:String, numbering:String):ByteArray {
        val out=ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for((name,text) in listOf("document" to document,"numbering" to numbering)) {
                z.putNextEntry(ZipEntry("word/$name.xml"));z.write(text.toByteArray());z.closeEntry()
            }
        }
        return out.toByteArray()
    }
    private fun paragraph(id:Int, level:Int, text:String)="""<w:p><w:pPr><w:numPr><w:ilvl w:val="$level"/><w:numId w:val="$id"/></w:numPr></w:pPr><w:r><w:t>$text</w:t></w:r></w:p>"""
    private fun numbering()="""<w:numbering xmlns:w="$ns"><w:abstractNum w:abstractNumId="0"><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/></w:lvl><w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="upperLetter"/><w:lvlText w:val="%2)"/></w:lvl></w:abstractNum><w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num></w:numbering>"""
    @Test fun automaticNumberingAndNestedOptionsRestart() {
        val body=buildString { for(i in 1..2) { append(paragraph(1,0,"Вопрос $i?"));append(paragraph(1,1,"Первый"));append(paragraph(1,1,"Второй")) } }
        val text=DocxTextExtractor.extract(archive("""<w:document xmlns:w="$ns"><w:body>$body</w:body></w:document>""",numbering()))
        assertTrue(text.contains("2. Вопрос 2?"));assertEquals(2,text.lineSequence().count { it=="A) Первый" })
        val quiz=QuizTextParser.parse(text,"Word").quiz
        assertEquals(2,quiz.questions.size);assertTrue(quiz.questions.all { it.options==listOf("Первый","Второй") })
    }
    @Test fun tablesMergedCellsAndSplitRunsKeepReadingOrder() {
        val doc="""<w:document xmlns:w="$ns"><w:body><w:tbl><w:tr><w:tc><w:tcPr><w:gridSpan w:val="2"/></w:tcPr><w:p><w:r><w:t>1. Воп</w:t></w:r><w:r><w:t>рос?</w:t></w:r></w:p></w:tc></w:tr><w:tr><w:tc><w:p><w:r><w:t>А) Да</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Б) Нет</w:t></w:r></w:p></w:tc></w:tr></w:tbl></w:body></w:document>"""
        val q=QuizTextParser.parse(DocxTextExtractor.extract(archive(doc,numbering())),"Таблица").quiz.questions.single()
        assertEquals("Вопрос?",q.text);assertEquals(listOf("Да","Нет"),q.options)
    }
    @Test fun maliciousNumberingXmlIsRejected() {
        val bad="""<!DOCTYPE w:numbering [<!ENTITY x SYSTEM "file:///private">]><w:numbering xmlns:w="$ns"/>"""
        assertThrows(IllegalArgumentException::class.java) { DocxTextExtractor.extract(archive("<x/>",bad)) }
    }
    @Test fun fullLevelOverrideAndStartOverrideAreRespected() {
        val changed=numbering().replace("<w:abstractNumId w:val=\"0\"/>", """<w:abstractNumId w:val="0"/><w:lvlOverride w:ilvl="0"><w:startOverride w:val="3"/></w:lvlOverride><w:lvlOverride w:ilvl="1"><w:lvl w:ilvl="1"><w:start w:val="5"/><w:numFmt w:val="lowerLetter"/><w:lvlText w:val="%2)"/></w:lvl></w:lvlOverride>""")
        val doc="""<w:document xmlns:w="$ns"><w:body>"""+paragraph(1,0,"Вопрос?")+paragraph(1,1,"Да")+paragraph(1,1,"Нет")+"</w:body></w:document>"
        val text=DocxTextExtractor.extract(archive(doc,changed))
        assertEquals("3. Вопрос?\ne) Да\nf) Нет",text)
    }
    @Test fun manualWordBreaksKeepQuestionBoundaries() {
        val doc="""<w:document xmlns:w="$ns"><w:body><w:p><w:r><w:t>1. Вопрос?</w:t><w:br/><w:t>А) Да</w:t><w:cr/><w:t>Б) Нет</w:t></w:r></w:p></w:body></w:document>"""
        val text=DocxTextExtractor.extract(archive(doc,numbering()))
        assertEquals(listOf("Да","Нет"),QuizTextParser.parse(text,"Word").quiz.questions.single().options)
    }
}
