package kz.quizgenerator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocxTextExtractorTest {
    private fun docx(xml: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(xml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    @Test fun extractsWordParagraphsAndOptions() {
        val xml = """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>1. Сұрақ?</w:t></w:r></w:p><w:p><w:r><w:t>A) Көкшетау</w:t></w:r></w:p><w:p><w:r><w:t>B) Астана</w:t></w:r></w:p></w:body></w:document>"""
        assertEquals("1. Сұрақ?\nA) Көкшетау\nB) Астана", DocxTextExtractor.extract(docx(xml)))
    }

    @Test fun rejectsExternalEntities() {
        val xml = """<!DOCTYPE root [<!ENTITY sample SYSTEM "file:///etc/passwd">]><root/>"""
        assertThrows(IllegalArgumentException::class.java) { DocxTextExtractor.extract(docx(xml)) }
    }
}
