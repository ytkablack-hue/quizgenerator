package kz.quizgenerator

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/** Extracts paragraphs from the document body of a .docx file without a network or AI. */
object DocxTextExtractor {
    fun extract(bytes: ByteArray): String {
        var documentXml: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 3_000_000) { "Текст Word превышает 3 МБ" }
                        output.write(buffer, 0, count)
                    }
                    documentXml = output.toByteArray()
                    break
                }
                zip.closeEntry()
            }
        }
        val xml = documentXml ?: throw IllegalArgumentException("В Word-файле нет документа")
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
