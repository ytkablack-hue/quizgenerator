package kz.quizgenerator

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipException
import java.util.zip.ZipFile
import javax.xml.parsers.SAXParserFactory

/** Extracts paragraphs from the document body of a .docx file without a network or AI. */
object DocxTextExtractor {
    fun extract(bytes: ByteArray, cacheDirectory: File? = null): String {
        val archive = File.createTempFile("quiz-word-", ".docx", cacheDirectory)
        val xml = try {
            archive.writeBytes(bytes)
            ZipFile(archive).use { zip ->
                val entry = zip.getEntry("word/document.xml")
                    ?: throw IllegalArgumentException("В Word-файле нет документа")
                require(entry.size in 0..3_000_000) { "Текст Word превышает 3 МБ" }
                zip.getInputStream(entry).use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 3_000_000) { "Текст Word превышает 3 МБ" }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray().also { content ->
                        val crc = CRC32().apply { update(content) }
                        require(content.size.toLong() == entry.size && crc.value == entry.crc) {
                            "Повреждённый файл Word"
                        }
                    }
                }
            }
        } catch (error: ZipException) {
            throw IllegalArgumentException("Повреждённый файл Word", error)
        } finally {
            archive.delete()
        }
        val xmlText = String(xml, Charsets.UTF_8)
        require(!xmlText.contains("<!DOCTYPE", ignoreCase = true) &&
            !xmlText.contains("<!ENTITY", ignoreCase = true)) { "Небезопасное содержимое Word" }
        val paragraphs = mutableListOf<String>()
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = true
        // Reject external entities and DTDs in untrusted document uploads.
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        val handler = object : DefaultHandler() {
            private var paragraph: StringBuilder? = null
            private var inText = false

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                when (localName ?: qName?.substringAfter(':')) {
                    "p" -> paragraph = StringBuilder()
                    "t" -> inText = true
                    "tab" -> paragraph?.append(' ')
                    "br" -> paragraph?.append(' ')
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inText) paragraph?.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (localName ?: qName?.substringAfter(':')) {
                    "t" -> inText = false
                    "p" -> {
                        paragraph?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(paragraphs::add)
                        paragraph = null
                    }
                }
            }
        }
        factory.newSAXParser().parse(ByteArrayInputStream(xml), handler)
        return paragraphs.joinToString("\n").also {
            require(it.isNotBlank()) { "Word-файл не содержит распознаваемого текста" }
        }
    }
}
