package kz.quizgenerator
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
internal data class QuizLibraryLoad(val quizzes: List<Quiz>,val problems: List<String>)
internal object QuizLibraryReader {
    fun read(directory: File): QuizLibraryLoad {
        val quizzes=mutableListOf<Quiz>();val problems=mutableListOf<String>()
        for(file in directory.listFiles {f->f.isFile && f.extension=="json"}.orEmpty().sortedBy {it.name}) {
            try {
                require(file.canonicalFile.parentFile==directory.canonicalFile && file.length() in 1..48_000_000)
                val decoder=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                quizzes+=QuizJson.parse(decoder.decode(ByteBuffer.wrap(file.readBytes())).toString())
            } catch(_: Exception) {problems+="Не удалось открыть "+file.name.take(128)+". Исходный файл сохранён; экспортируйте резервную копию перед повторным импортом"}
        }
        return QuizLibraryLoad(quizzes,problems)
    }
}
