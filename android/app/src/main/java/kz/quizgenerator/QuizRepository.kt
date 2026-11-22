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
internal data class ImportProgress(val document: DocumentDetails, val fraction: Float?,val phase: String="Обработка документа")

class QuizRepository(private val context: Context, private val dataRoot: File = context.filesDir) {
    internal var libraryWarnings: List<String> = emptyList();private set
    internal val materials = MaterialStore(dataRoot)
    internal val sets = StudySetStore(dataRoot)
    internal val workspaceRoot: File get()=dataRoot
    private val directory get() = File(dataRoot, "quiz_banks")

    fun listQuizzes(): List<Quiz> {
        val demo = context.assets.open("demo_quiz.json").bufferedReader().use { QuizJson.parse(it.readText()) }
        val imported=QuizLibraryReader.read(directory)
        libraryWarnings=imported.problems
        return listOf(demo)+imported.quizzes
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

    internal fun attachMaterial(uri: Uri,charset: String?=null,progress: (ImportProgress)->Unit): MaterialRecord = withDocument(uri) {input ->
        val details=describe(uri);val type=format(input,details.name).let {if(it.isBlank())"txt" else it}
        require(details.bytes==null || details.bytes<=MaterialStore.MAX_SOURCE) {"Документ больше 64 МБ"}
        var phase="Загрузка документа";var fraction: Float?=0f
        fun report(value: Float?) {fraction=value;progress(ImportProgress(details,value,phase))}
        fun checkProgress()=progress(ImportProgress(details,fraction,phase))
        report(0f)
        materials.import(details.name,type,input,::checkProgress,copied={total->report(details.bytes?.takeIf {it>0 && total<=it}?.let {total.toFloat()/it})}) {file ->
            phase="Извлечение текста";report(null)
            val text=StringBuilder();var ocr=false;val emptyPages=mutableListOf<Int>()
            fun append(line: String) {checkProgress();require(text.length.toLong()+line.length+1<=MaterialStore.MAX_TEXT) {"В документе больше 8 млн символов"};text.append(line).append('\n')}
            file.inputStream().buffered().use {source ->
                when(type) {
                    "docx" -> DocxTextExtractor.paragraphs(source,context.cacheDir,file.length(),::report,::append)
                    "pdf" -> LocalOcr(context).use {engine -> ocr=PdfTextExtractor.paragraphs(source,context.cacheDir,file.length(),::report,::append,onEmpty={emptyPages+=it+1}) {page,p -> phase="Распознавание страниц";engine.page(page,p,allowEmpty=true)}}
                    "image" -> LocalOcr(context).use {engine -> ocr=true;phase="Распознавание изображения";append(engine.image(source,context.cacheDir,file.length(),::report))}
                    else -> {
                        val decoded=TextDecoder.decode(source.readBytesBounded(MaterialStore.MAX_TEXT*4),charset)
                        append(decoded.text)
                        if(!decoded.confident)return@import MaterialText(text.toString(),"Кодировка "+decoded.charset+" определена предположительно. Проверьте текст перед генерацией")
                    }
                }
            }
            phase="Сохранение";report(null)
            MaterialText(text.toString(),(if(ocr)"Распознано локальным OCR. Проверьте формулировки и ключи. " else "")+if(emptyPages.isEmpty())"" else "Без распознанного текста: "+emptyPages.size+" страниц ("+emptyPages.take(20).joinToString()+"). Возможны пропуски: проверьте оригинал. Полный PDF сохранён.")
        }.also {phase="Готово";report(1f)}
    }

    internal fun questionsFromMaterial(id: String,check: ()->Unit={}): ParsedQuiz {
        val record=materials.load(id);val text=materials.text(id)
        var parsed=if(record.format=="json")ParsedQuiz(QuizJson.parse(text),emptyList()) else QuizTextParser.parseFrom(record.name.substringBeforeLast('.'),check) {consume->text.lineSequence().forEach(consume)}
        if(record.notice.contains("Без распознанного текста:"))parsed=parsed.copy(quiz=parsed.quiz.copy(importProblems=parsed.quiz.importProblems+ImportProblem(null,"Проверьте нераспознанные страницы оригинала",record.notice)),warnings=parsed.warnings+record.notice)
        if(record.notice.contains("ИИ-лекции"))return parsed.copy(quiz=parsed.quiz.copy(generatedByAi=true,questions=parsed.quiz.questions.map {it.copy(evidence=if(it.correctIndices.isEmpty())AnswerEvidence.UNKNOWN else AnswerEvidence.CLOUD_AI)}))
        return if(record.notice.contains("OCR"))parsed.copy(quiz=OcrReview.review(parsed.quiz,true)) else parsed
    }

    internal fun previewImport(uri: Uri,charset: String?=null,onDecoded: (DecodedText)->Unit={},progress: (ImportProgress)->Unit): ImportResult {
        val record=attachMaterial(uri,charset,progress)
        require(!record.notice.contains("Без распознанного текста:")) {"Некоторые страницы не распознаны. Полный оригинал сохранён; проверьте его перед импортом"}
        val parsed=questionsFromMaterial(record.id) {progress(ImportProgress(DocumentDetails(record.name,record.bytes),null,"Распознавание вопросов"))}
        val decoded=if(record.format in listOf("txt","json"))TextDecoder.decode(materials.source(record.id).readBytes(),charset) else null
        if(decoded!=null)onDecoded(decoded)
        return ImportResult(parsed.quiz,parsed.warnings+listOf(record.notice).filter {it.isNotBlank()},DocumentDetails(record.name,record.bytes),decoded?.charset ?: "UTF-8",decoded?.confident ?: true,decoded?.text?.take(500).orEmpty())
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
