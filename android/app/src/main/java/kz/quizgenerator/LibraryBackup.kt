package kz.quizgenerator

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject

internal object LibraryBackup {
    fun write(root: File, output: OutputStream, checkCancelled: () -> Unit = {}) {
        val files=listOf(File(root,"study.json"),File(root,"active-attempt.json"),File(root,"sets.json"),File(root,"ai-draft.json"))+
            File(root,"quiz_banks").listFiles { f -> f.isFile && f.extension=="json" }.orEmpty().sortedBy { it.name } +
            File(root,"materials").listFiles().orEmpty().filter {it.isDirectory && runCatching {java.util.UUID.fromString(it.name).toString()==it.name}.getOrDefault(false)}
                .flatMap {dir -> listOf("meta.json","document.txt","source.bin").map {File(dir,it)} }
        val extra=listOf("lectures","batch_jobs").flatMap {name->File(root,name).listFiles {f->f.isFile && f.extension=="json" && runCatching {java.util.UUID.fromString(f.nameWithoutExtension).toString()==f.nameWithoutExtension}.getOrDefault(false)}.orEmpty().toList()}
        var total=0L
        ZipOutputStream(output).use { zip ->
            for(file in (files+extra).filter { it.exists() }) {
                checkCancelled()
                val relative=file.relativeTo(root).invariantSeparatorsPath
                require(file.canonicalPath==File(root.canonicalFile,relative).normalize().path) { "Небезопасный путь библиотеки" }
                require(file.length()<=128L*1024*1024) { "Файл слишком большой для резервной копии" }
                total+=file.length();require(total<=512L*1024*1024) { "Библиотека слишком большая. Сохраните тесты по частям" }
                zip.putNextEntry(ZipEntry(relative))
                if(relative=="study.json") {
                    require(file.length()<=16L*1024*1024) { "История больше 16 МБ. Сохраните тесты отдельно" }
                    val value=JSONObject(file.readText(Charsets.UTF_8))
                    value.optJSONObject("settings")?.let { listOf("serverPassword","serverUrl","serverToken","server","accessToken","apiKey","password","idToken","refreshToken").forEach(it::remove) }
                    zip.write(value.toString().toByteArray(Charsets.UTF_8))
                } else file.inputStream().buffered().use { input ->
                    val bytes=ByteArray(32*1024)
                    while(true) { checkCancelled();val n=input.read(bytes);if(n<0)break;zip.write(bytes,0,n) }
                }
                zip.closeEntry()
            }
        }
    }
}
