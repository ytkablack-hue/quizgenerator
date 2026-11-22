package kz.quizgenerator

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.File
import java.util.UUID

internal data class DocumentDetails(val name: String, val bytes: Long?)
internal data class ImportResult(
    val quiz: Quiz,
    val warnings: List<String>,
    val document: DocumentDetails,
    val charset: String = "UTF-8",
    val encodingConfident: Boolean = true,
    val preview: String = ""
)
internal data class ImportProgress(val document: DocumentDetails, val fraction: Float?)

class QuizRepository(private val context: Context, private val dataRoot: File = context.filesDir) {
    private val directory get() = File(dataRoot, "quiz_banks")

    fun listQuizzes(): List<Quiz> {
        val demo = context.assets.open("demo_quiz.json").bufferedReader().use { QuizJson.parse(it.readText()) }
        val imported = directory.listFiles { file -> file.isFile && file.extension == "json" }
            ?.sortedBy { it.name }
            ?.mapNotNull { file -> runCatching { QuizJson.parse(file.readText(Charsets.UTF_8)) }.getOrNull() }
            .orEmpty()
        return listOf(demo) + imported
    }

    internal fun describe(uri: Uri): DocumentDetails =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) null else DocumentDetails(
                    cursor.getString(0)?.take(256) ?: "Документ",
                    if (cursor.isNull(1)) null else cursor.getLong(1).takeIf { it >= 0 }
                )
            } ?: DocumentDetails("Документ", null)

    fun modifiedAt(quiz: Quiz): Long? =
        if (canDelete(quiz)) File(directory, "${quiz.id}.json").lastModified().takeIf { it > 0 } else null

    private fun <T> withDocument(uri: Uri, action: (InputStream) -> T): T {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Не удалось открыть файл")
        return stream.buffered().use(action)
    }

    private fun bytes(input: InputStream, size: Long?, progress: (Float?) -> Unit): ByteArray {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(result.size() + count <= 12_000_000) { "TXT или JSON больше 12 МБ. Разделите документ." }
            result.write(buffer, 0, count)
            progress(size?.takeIf { it > 0 }?.let { (.9f * result.size() / it).coerceIn(0f, .9f) })
        }
        return result.toByteArray()
    }

    private fun format(input: InputStream, name: String): String {
        val suffix = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
        input.mark(4)
        val prefix = ByteArray(4)
        var length = 0
        while (length < prefix.size) {
            val n = input.read(prefix, length, prefix.size - length)
            if (n < 0) break
            length += n
        }
        input.reset()
        return when {
            suffix == "pdf" || (length == 4 && prefix.contentEquals("%PDF".toByteArray())) -> "pdf"
            suffix == "docx" || (length >= 2 && prefix[0] == 0x50.toByte() && prefix[1] == 0x4b.toByte()) -> "docx"
            else -> suffix
        }
    }

    fun readStudyMaterial(uri: Uri): String = withDocument(uri) { stream ->
        val details = describe(uri)
        val material = when (format(stream, details.name)) {
            "docx" -> DocxTextExtractor.extract(stream, context.cacheDir, 12_001)
            "pdf" -> throw IllegalArgumentException("Для учебного материала выберите TXT или DOCX")
            else -> TextDecoder.decode(bytes(stream, details.bytes, {})).text
        }
        material.trim().also {
            require(it.isNotEmpty()) { "Файл не содержит учебного текста" }
            require(it.length <= 12_000) { "Учебный материал длиннее 12 000 символов" }
        }
    }

    internal fun previewImport(
        uri: Uri,
        charset: String? = null,
        onDecoded: (DecodedText) -> Unit = {},
        progress: (ImportProgress) -> Unit
    ): ImportResult = withDocument(uri) { stream ->
        val details = describe(uri)
        val format = format(stream, details.name)
        val title = details.name.substringBeforeLast('.')
        fun report(fraction: Float?) = progress(ImportProgress(details, fraction))
        report(0f)
        val counted = object : FilterInputStream(stream) {
            var total = 0L
            override fun read(): Int = super.read().also { if (it >= 0) total++ }
            override fun read(b: ByteArray, off: Int, len: Int): Int =
                super.read(b, off, len).also { if (it > 0) total += it }
        }
        var detectedCharset = "UTF-8"
        var confident = true
        var preview = ""
        val parsed = when (format) {
            "pdf" -> QuizTextParser.parseFrom(title, { report(null) }) { consume ->
                PdfTextExtractor.paragraphs(counted, context.cacheDir, details.bytes, ::report, consume)
            }
            "docx" -> QuizTextParser.parseFrom(title) { consume ->
                DocxTextExtractor.paragraphs(counted, context.cacheDir, details.bytes, ::report, consume)
            }
            else -> {
                val decoded = TextDecoder.decode(bytes(counted, details.bytes, ::report), charset)
                onDecoded(decoded)
                detectedCharset = decoded.charset
                confident = decoded.confident
                preview = decoded.text.take(500)
                if (format == "json" || decoded.text.trimStart().startsWith("{")) {
                    ParsedQuiz(QuizJson.parse(decoded.text), emptyList())
                } else {
                    QuizTextParser.parseFrom(title, { report(null) }) { consume -> decoded.text.lineSequence().forEach(consume) }
                }
            }
        }
        report(1f)
        val warnings = if (confident) parsed.warnings else parsed.warnings + listOf(
            "Кодировка определена как $detectedCharset предположительно. Проверьте текст или выберите кодировку вручную."
        )
        ImportResult(
            parsed.quiz, warnings, details.copy(bytes = counted.total),
            charset = detectedCharset, encodingConfident = confident, preview = preview
        )
    }

    /** Persist the manually reviewed answer key; keep the read-only demo quiz intact. */
    fun saveQuiz(quiz: Quiz): Quiz {
        val saved = if (runCatching { UUID.fromString(quiz.id) }.isFailure) {
            quiz.copy(id = UUID.randomUUID().toString(), title = "${quiz.title} (копия)")
        } else quiz
        QuizJson.parse(QuizJson.serialize(saved))
        persist(saved)
        return saved
    }

    fun addQuiz(quiz: Quiz): Quiz {
        val added = quiz.copy(id = UUID.randomUUID().toString())
        QuizJson.parse(QuizJson.serialize(added))
        persist(added)
        return added
    }

    fun canDelete(quiz: Quiz): Boolean =
        runCatching { UUID.fromString(quiz.id).toString() == quiz.id }.getOrDefault(false)

    fun deleteQuiz(quiz: Quiz) {
        require(canDelete(quiz)) { "Демонстрационный тест нельзя удалить" }
        val file = File(directory, "${quiz.id}.json")
        check(!file.exists() || file.delete()) { "Не удалось удалить тест" }
    }

    private fun persist(imported: Quiz) {
        require(runCatching { UUID.fromString(imported.id) }.isSuccess) { "Неверный ID теста" }
        atomicWrite(File(directory, "${imported.id}.json"), QuizJson.serialize(imported))
    }
}
