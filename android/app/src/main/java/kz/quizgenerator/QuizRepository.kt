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
    internal val materials = MaterialStore(dataRoot)
    internal val sets = StudySetStore(dataRoot)
    internal val workspaceRoot: File get()=dataRoot
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
            suffix in setOf("jpg","jpeg","png") || (length>=3 && prefix[0]==0xff.toByte() && prefix[1]==0xd8.toByte() && prefix[2]==0xff.toByte()) || (length==4 && prefix.contentEquals(byteArrayOf(0x89.toByte(),0x50,0x4e,0x47))) -> "image"
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

    internal fun attachMaterial(uri: Uri,progress: (ImportProgress)->Unit): MaterialRecord = withDocument(uri) {input ->
        val details=describe(uri);val type=format(input,details.name).let {if(it.isBlank())"txt" else it}
        require(details.bytes==null || details.bytes<=MaterialStore.MAX_SOURCE) {"Документ больше 64 МБ"}
        fun report(value: Float?)=progress(ImportProgress(details,value))
        report(0f)
        materials.import(details.name,type,input,{report(null)}) {file ->
            val text=StringBuilder();var ocr=false;val emptyPages=mutableListOf<Int>()
            fun append(line: String) {report(null);require(text.length.toLong()+line.length+1<=MaterialStore.MAX_TEXT) {"В документе больше 8 млн символов"};text.append(line).append('\n')}
            file.inputStream().buffered().use {source ->
                when(type) {
                    "docx" -> DocxTextExtractor.paragraphs(source,context.cacheDir,file.length(),::report,::append)
                    "pdf" -> LocalOcr(context).use {engine -> ocr=PdfTextExtractor.paragraphs(source,context.cacheDir,file.length(),::report,::append,onEmpty={emptyPages+=it+1}) {page,p -> engine.page(page,p,allowEmpty=true)}}
                    "image" -> LocalOcr(context).use {engine -> ocr=true;append(engine.image(source,context.cacheDir,file.length(),::report))}
                    else -> {
                        val decoded=TextDecoder.decode(source.readBytesBounded(MaterialStore.MAX_TEXT*4))
                        append(decoded.text)
                        if(!decoded.confident)return@import MaterialText(text.toString(),"Кодировка "+decoded.charset+" определена предположительно. Проверьте текст перед генерацией")
                    }
                }
            }
            report(1f)
            MaterialText(text.toString(),(if(ocr)"Распознано локальным OCR. Проверьте формулировки и ключи. " else "")+if(emptyPages.isEmpty())"" else "Без распознанного текста: "+emptyPages.size+" страниц ("+emptyPages.take(20).joinToString()+"). Возможны пропуски: проверьте оригинал. Полный PDF сохранён.")
        }
    }

    internal fun questionsFromMaterial(id: String): ParsedQuiz {
        val record=materials.load(id);val text=materials.text(id)
        val parsed=if(record.format=="json")ParsedQuiz(QuizJson.parse(text),emptyList()) else QuizTextParser.parse(text,record.name.substringBeforeLast('.'))
        return if(record.notice.contains("OCR"))parsed.copy(quiz=OcrReview.review(parsed.quiz,true)) else parsed
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
        var usedOcr=false
        val parsed = when (format) {
            "pdf" -> QuizTextParser.parseFrom(title, { report(null) }) { consume ->
                LocalOcr(context).use { engine ->
                    usedOcr=PdfTextExtractor.paragraphs(counted,context.cacheDir,details.bytes,::report,consume) {page,progress -> engine.page(page,progress)}
                }
            }
            "image" -> {
                usedOcr=true
                val text=LocalOcr(context).use {it.image(counted,context.cacheDir,details.bytes,::report)}
                QuizTextParser.parseFrom(title,{report(null)}) {consume -> text.lineSequence().forEach(consume)}
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
        val warnings = (if(usedOcr)parsed.warnings+"Использован локальный OCR. Проверьте текст, варианты и ключи: OCR-ключи не оцениваются до ручного подтверждения." else parsed.warnings) + if (confident) emptyList() else listOf(
            "Кодировка определена как $detectedCharset предположительно. Проверьте текст или выберите кодировку вручную."
        )
        ImportResult(
            OcrReview.review(parsed.quiz,usedOcr), warnings, details.copy(bytes = counted.total),
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
