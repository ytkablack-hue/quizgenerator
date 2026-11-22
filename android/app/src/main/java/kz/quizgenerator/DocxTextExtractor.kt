package kz.quizgenerator

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.ZipException
import java.util.zip.ZipFile
import javax.xml.parsers.SAXParserFactory

/** Streams the verified Word XML. Images and other archive members are never expanded. */
object DocxTextExtractor {
    internal const val MAX_ARCHIVE_BYTES = 64L * 1024 * 1024
    internal const val MAX_XML_BYTES = 128L * 1024 * 1024
    private const val MAX_PARAGRAPH = 65_536
    private const val TOO_LARGE = "Документ слишком большой. Разделите его на несколько файлов."

    fun extract(bytes: ByteArray, cacheDirectory: File? = null): String =
        extract(ByteArrayInputStream(bytes), cacheDirectory)

    internal fun extract(input: InputStream, cacheDirectory: File?, maxCharacters: Int = 8_000_000): String {
        val output = StringBuilder()
        paragraphs(input, cacheDirectory, null, {}, { paragraph ->
            require(output.length + paragraph.length + 1 <= maxCharacters) { TOO_LARGE }
            if (output.isNotEmpty()) output.append('\n')
            output.append(paragraph)
        })
        return output.toString().also { require(it.isNotBlank()) { "В документе не найден текст" } }
    }

    internal fun paragraphs(
        source: InputStream,
        cacheDirectory: File?,
        sourceSize: Long?,
        progress: (Float?) -> Unit,
        consume: (String) -> Unit
    ) {
        require(sourceSize == null || sourceSize <= MAX_ARCHIVE_BYTES) { TOO_LARGE }
        val archive = File.createTempFile("testhelper-word-", ".docx", cacheDirectory)
        try {
            progress(0f)
            archive.outputStream().buffered().use { destination ->
                val buffer = ByteArray(32 * 1024)
                var copied = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    copied += read
                    require(copied <= MAX_ARCHIVE_BYTES) { TOO_LARGE }
                    destination.write(buffer, 0, read)
                    progress(sourceSize?.takeIf { it > 0 }?.let { .45f * (copied.toFloat() / it).coerceAtMost(1f) })
                }
            }
            ZipFile(archive).use { zip ->
                val numbering = WordNumbering.read(zip) { progress(.45f) }
                val entry = zip.getEntry("word/document.xml")
                    ?: throw IllegalArgumentException("В DOCX нет основного текста документа")
                require(entry.size in 0..MAX_XML_BYTES) { TOO_LARGE }
                val crc = CRC32()
                var read = 0L
                var matched = 0
                val forbidden = "<!DOCTYPE"
                fun inspect(value: Int) {
                    // NULs in UTF-16/32 encodings do not hide the ASCII XML keyword.
                    if (value == 0) return
                    matched = if (value == forbidden[matched].code) matched + 1 else if (value == '<'.code) 1 else 0
                    require(matched < forbidden.length) { "В документе найдено небезопасное объявление XML" }
                }
                val checked = object : FilterInputStream(zip.getInputStream(entry).buffered()) {
                    override fun read(): Int {
                        val byte = super.read()
                        if (byte >= 0) {
                            inspect(byte)
                            crc.update(byte)
                            advance(1)
                        }
                        return byte
                    }

                    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                        val count = super.read(bytes, offset, length)
                        if (count > 0) {
                            advance(count)
                            for (index in offset until offset + count) inspect(bytes[index].toInt() and 255)
                            crc.update(bytes, offset, count)
                        }
                        return count
                    }

                    override fun markSupported() = false

                    private fun advance(bytes: Int) {
                        read += bytes
                        require(read <= MAX_XML_BYTES && read <= entry.size) { TOO_LARGE }
                        progress(.45f + .54f * (read.toFloat() / entry.size.coerceAtLeast(1)).coerceAtMost(1f))
                    }
                }
                checked.use { parseXml(it, numbering, consume) }
                require(read == entry.size && crc.value == entry.crc) { "Повреждённый документ Word" }
            }
            progress(1f)
        } catch (error: ZipException) {
            throw IllegalArgumentException("Повреждённый документ Word", error)
        } catch (error: SAXException) {
            throw IllegalArgumentException("Не удалось разобрать текст документа Word", error)
        } finally {
            archive.delete()
        }
    }

    private fun parseXml(input: InputStream, numbering: WordNumbering, consume: (String) -> Unit) {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val handler = object : DefaultHandler() {
            private val paragraph = StringBuilder()
            private var inParagraph = false
            private var inText = false
            private var numId: String? = null
            private var level = 0

            override fun resolveEntity(publicId: String?, systemId: String?): InputSource =
                throw SAXException("Внешние XML-ресурсы отключены")

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                when (localName?.takeIf(String::isNotEmpty) ?: qName?.substringAfter(':')) {
                    "p" -> { flush(); inParagraph = true; numId = null; level = 0 }
                    "numId" -> if (inParagraph) numId = attributes?.getValue("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val") ?: attributes?.getValue("w:val")
                    "ilvl" -> if (inParagraph) level = (attributes?.getValue("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val") ?: attributes?.getValue("w:val"))?.toIntOrNull() ?: 0
                    "t" -> inText = true
                    "tab" -> if (inParagraph) appendSpace()
                    "br", "cr" -> if (inParagraph) appendSpace('\n')
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inParagraph && inText) {
                    require(paragraph.length + length <= MAX_PARAGRAPH) { "Абзац слишком длинный. Разделите его в Word." }
                    paragraph.append(ch, start, length)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (localName?.takeIf(String::isNotEmpty) ?: qName?.substringAfter(':')) {
                    "t" -> inText = false
                    "p" -> { flush(); inParagraph = false }
                }
            }

            private fun appendSpace(separator: Char = ' ') {
                require(paragraph.length < MAX_PARAGRAPH) { "Абзац слишком длинный. Разделите его в Word." }
                paragraph.append(separator)
            }

            private fun flush() {
                if (paragraph.isNotBlank()) (numbering.prefix(numId, level) + paragraph.toString()).lineSequence().forEach(consume)
                paragraph.setLength(0)
            }
        }
        factory.newSAXParser().parse(input, handler)
    }
}
