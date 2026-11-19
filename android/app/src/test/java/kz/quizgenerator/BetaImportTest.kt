package kz.quizgenerator

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BetaImportTest {
    @get:Rule val folder = TemporaryFolder()

    private fun archive(padding: Int): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            val writer = OutputStreamWriter(zip, Charsets.UTF_8)
            writer.write("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""")
            val block = "<w:bookmarkStart/>".repeat(1024)
            repeat(padding + 1) { writer.write(block) }
            for (line in listOf("1. Как называется столица?", "А) Астана", "Б) Алматы", "Ответ: А")) {
                writer.write("<w:p><w:r><w:t>$line</w:t></w:r></w:p>")
            }
            writer.write("</w:body></w:document>")
            writer.flush()
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    @Test fun extractsQuestionsFromFourAndTwentyMegabytesOfWordXml() {
        for (blocks in listOf(228, 1138)) {
            val archive = archive(blocks)
            val points = mutableListOf<Float?>()
            val quiz = QuizTextParser.parseFrom("Документ") { consume ->
                DocxTextExtractor.paragraphs(ByteArrayInputStream(archive), folder.root, archive.size.toLong(), {
                    if (points.lastOrNull() != it && (points.size < 2000 || it == 1f)) points.add(it)
                }, consume)
            }.quiz
            assertEquals("Астана", quiz.questions.single().options.first())
            assertEquals(0, quiz.questions.single().correctIndex)
            assertTrue(points.contains(0f))
            assertTrue(points.any { it != null && it > .5f && it < 1f })
            assertEquals(1f, points.last() ?: 0f, .001f)
            assertEquals(0, folder.root.listFiles().orEmpty().size)
        }
    }

    @Test fun cancelsAndReleasesTemporaryArchive() {
        val archive = archive(228)
        assertThrows(CancellationException::class.java) {
            DocxTextExtractor.paragraphs(ByteArrayInputStream(archive), folder.root, archive.size.toLong(), {
                if ((it ?: 0f) > .5f) throw CancellationException()
            }, {})
        }
        assertEquals(0, folder.root.listFiles().orEmpty().size)
    }

    @Test fun rejectsDocumentsOverArchiveLimitBeforeReading() {
        assertThrows(IllegalArgumentException::class.java) {
            DocxTextExtractor.paragraphs(ByteArrayInputStream(byteArrayOf()), folder.root,
                DocxTextExtractor.MAX_ARCHIVE_BYTES + 1, {}, {})
        }
    }

}
