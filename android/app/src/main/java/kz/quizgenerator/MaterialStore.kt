package kz.quizgenerator

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject

internal data class MaterialText(val text: String,val notice: String="")
internal data class MaterialRecord(val id: String,val name: String,val format: String,val bytes: Long,
    val characters: Int,val createdAt: Long,val textHash: String,val notice: String="")

/** New account-scoped storage. Existing quiz/study files are never migrated or overwritten. */
internal class MaterialStore(private val accountRoot: File) {
    private val root get()=File(accountRoot,"materials")
    private fun validId(id: String)=runCatching {UUID.fromString(id).toString()==id}.getOrDefault(false)
    private fun directory(id: String): File {
        require(validId(id)) {"Некорректный ID документа"}
        val result=File(root,id)
        require(result.canonicalFile.parentFile==root.canonicalFile) {"Небезопасный путь документа"}
        return result
    }
    fun source(id: String): File = safeChild(directory(id),"source.bin")
    fun load(id: String): MaterialRecord {
        val file=safeChild(directory(id),"meta.json")
        require(file.length() in 1..16384) {"Повреждены сведения о документе"}
        val value=JSONObject(file.readText(Charsets.UTF_8))
        require(value.getString("id")==id)
        return MaterialRecord(id,value.getString("name"),value.getString("format"),value.getLong("bytes"),
            value.getInt("characters"),value.getLong("createdAt"),value.getString("textHash"),value.optString("notice")).also {
            require(it.name.length in 1..256 && it.bytes in 1..MAX_SOURCE && it.characters in 1..MAX_TEXT)
            require(it.format in FORMATS && Regex("[a-f0-9]{64}").matches(it.textHash))
        }
    }
    /** Errors are returned explicitly; a broken attachment is never silently discarded. */
    fun list(): List<Result<MaterialRecord>> = root.listFiles().orEmpty().filter {it.isDirectory && validId(it.name)}
        .sortedByDescending {it.lastModified()}.map {runCatching {load(it.name)}}
    fun text(id: String): String {
        val record=load(id);val file=safeChild(directory(id),"document.txt")
        require(file.length() in 1..(MAX_TEXT.toLong()*4)) {"Повреждён текст документа"}
        val bytes=file.readBytes()
        require(hash(bytes)==record.textHash) {"Текст документа повреждён. Загрузите оригинал повторно"}
        return bytes.toString(Charsets.UTF_8).also {require(it.length==record.characters)}
    }
    fun import(name: String,format: String,input: InputStream,checkCancelled: ()->Unit={},
        extract: (File)->MaterialText): MaterialRecord {
        require(format in FORMATS) {"Выберите TXT, DOCX, PDF, JSON, JPG или PNG"}
        check(root.isDirectory || root.mkdirs()) {"Не удалось создать хранилище материалов"}
        require(root.listFiles().orEmpty().count {it.isDirectory && validId(it.name)}<5000) {"Слишком много документов"}
        val id=UUID.randomUUID().toString();val staging=File(root,".pending-"+id)
        check(staging.mkdir())
        var published=false
        try {
            val source=File(staging,"source.bin");var bytes=0L
            source.outputStream().buffered().use {out ->
                val block=ByteArray(32*1024)
                while(true) {checkCancelled();val n=input.read(block);if(n<0)break;bytes+=n;require(bytes<=MAX_SOURCE) {"Документ больше 64 МБ"};out.write(block,0,n)}
            }
            require(bytes>0) {"Выбран пустой файл"}
            checkCancelled()
            val result=extract(source)
            require(result.text.isNotBlank()) {"В документе нет текста"}
            require(result.text.length<=MAX_TEXT) {"В документе больше 8 млн символов. Разделите его"}
            val encoded=result.text.toByteArray(Charsets.UTF_8)
            File(staging,"document.txt").writeBytes(encoded)
            val record=MaterialRecord(id,name.trim().take(256).ifBlank {"Документ"},format,bytes,result.text.length,
                System.currentTimeMillis(),hash(encoded),result.notice.take(2000))
            File(staging,"meta.json").writeText(JSONObject().put("version",1).put("id",id).put("name",record.name)
                .put("format",format).put("bytes",bytes).put("characters",record.characters).put("createdAt",record.createdAt)
                .put("textHash",record.textHash).put("notice",record.notice).toString(),Charsets.UTF_8)
            checkCancelled()
            check(!directory(id).exists() && staging.renameTo(directory(id))) {"Не удалось сохранить документ"}
            published=true
            return record
        } finally {
            // Only our three staging files can be removed, never a user source or published document.
            if(!published) {listOf("source.bin","document.txt","meta.json").forEach {File(staging,it).delete()};staging.delete()}
        }
    }
    private fun safeChild(parent: File,name: String): File = File(parent,name).also {
        require(it.canonicalFile.parentFile==parent.canonicalFile) {"Небезопасный файл материала"}
    }
    companion object {
        const val MAX_TEXT=8_000_000
        const val MAX_SOURCE=64L*1024*1024
        val FORMATS=setOf("txt","docx","pdf","json","image")
        private fun hash(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it.toInt() and 255)}
    }
}
