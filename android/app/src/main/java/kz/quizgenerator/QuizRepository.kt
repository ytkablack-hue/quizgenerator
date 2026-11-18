package kz.quizgenerator

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID

data class ImportResult(val quiz: Quiz, val warnings: List<String>)

class QuizRepository(private val context: Context) {
    private val directory get() = File(context.filesDir, "quiz_banks")

    fun listQuizzes(): List<Quiz> {
        val demo = context.assets.open("demo_quiz.json").bufferedReader().use { QuizJson.parse(it.readText()) }
        val imported = directory.listFiles { file -> file.isFile && file.extension == "json" }
            ?.sortedBy { it.name }
            ?.mapNotNull { file -> runCatching { QuizJson.parse(file.readText(Charsets.UTF_8)) }.getOrNull() }
            .orEmpty()
        return listOf(demo) + imported
    }

    /** Imports JSON, plain text and text-based DOCX locally, without contacting a server. */
    private fun readFile(uri: Uri): Pair<String, ByteArray> {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Не удалось открыть файл")
        val bytes = stream.use { input ->
            val result = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= 12_000_000) { "Файл больше 12 МБ" }
                result.write(buffer, 0, count)
            }
            result.toByteArray()
        }
        val filename = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "Тест"
        return filename to bytes
    }

    fun readStudyMaterial(uri: Uri): String {
        val (filename, bytes) = readFile(uri)
        val material = if (filename.endsWith(".docx", ignoreCase = true)) {
            DocxTextExtractor.extract(bytes, context.cacheDir)
        } else decodeText(bytes)
        return material.trim().also {
            require(it.isNotEmpty()) { "Файл не содержит учебного текста" }
            require(it.length <= 12_000) { "Учебный материал длиннее 12 000 символов" }
        }
    }

    fun importQuiz(uri: Uri): ImportResult {
        val (filename, bytes) = readFile(uri)
        val lowerName = filename.lowercase()
        val result = when {
            lowerName.endsWith(".pdf") || bytes.copyOfRange(0, minOf(4, bytes.size)).contentEquals("%PDF".toByteArray()) ->
                throw IllegalArgumentException("Импорт PDF пока не готов. Выбери Word (.docx), TXT или JSON")
            lowerName.endsWith(".docx") || bytes.take(2).toByteArray().contentEquals(byteArrayOf(0x50, 0x4b)) -> {
                val content = DocxTextExtractor.extract(bytes, context.cacheDir)
                val extracted = QuizTextParser.parse(content, filename.substringBeforeLast('.'))
                ImportResult(extracted.quiz, extracted.warnings)
            }
            else -> {
                val decoded = decodeText(bytes)
                if (lowerName.endsWith(".json") || decoded.trimStart().startsWith("{")) {
                    ImportResult(QuizJson.parse(decoded), emptyList())
                } else {
                    val extracted = QuizTextParser.parse(decoded, filename.substringBeforeLast('.'))
                    ImportResult(extracted.quiz, extracted.warnings)
                }
            }
        }
        val imported = result.quiz.copy(id = UUID.randomUUID().toString())
        persist(imported)
        return result.copy(quiz = imported)
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

    private fun decodeText(bytes: ByteArray): String = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString().removePrefix("\uFEFF")

    private fun persist(imported: Quiz) {
        require(runCatching { UUID.fromString(imported.id) }.isSuccess) { "Неверный ID теста" }
        atomicWrite(File(directory, "${imported.id}.json"), QuizJson.serialize(imported))
    }
}
