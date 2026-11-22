package kz.quizgenerator
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.json.JSONArray
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import android.widget.Toast

internal object StudySetExport {
    fun write(root: File,set: StudySet,data: StudyData,out: OutputStream,check: ()->Unit={}) {
        val ids=set.quizIds.toSet()
        val filtered=data.copy(cards=WorkspaceIndex.cards(set,data),attempts=WorkspaceIndex.attempts(set,data),exams=data.exams.filter {exam->exam.quizIds.any {it in ids}})
        val temp=File.createTempFile(".set-export-",".json",root)
        try {
            StudyStore(temp).save(filtered)
            val sources=set.quizIds.map {id ->require(runCatching {java.util.UUID.fromString(id).toString()==id}.getOrDefault(false));File(root,"quiz_banks/"+id+".json")}+
                set.materialIds.flatMap {id ->val material=MaterialStore(root);material.load(id);listOf("meta.json","source.bin","document.txt").map {File(material.source(id).parentFile,it)}}
            val studyFiles=LectureStore(root).list(set.id).map {File(root,"lectures/"+it.id+".json")}+
                File(root,"batch_jobs").listFiles {f->f.isFile && f.extension=="json"}.orEmpty().filter {file->require(file.canonicalFile.parentFile==File(root,"batch_jobs").canonicalFile) {"Небезопасный путь задания"};require(file.length() in 1..32_000_000);JSONObject(file.readText()).optString("setId")==set.id}
            var total=0L
            ZipOutputStream(out).use {zip ->
                fun entry(name: String,bytes: ByteArray) {check();zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry()}
                val manifest=JSONObject().put("version",1).put("id",set.id).put("title",set.title).put("notes",set.notes)
                    .put("materialIds",JSONArray(set.materialIds)).put("quizIds",JSONArray(set.quizIds)).put("cardIds",JSONArray(set.cardIds))
                entry("set.json",manifest.toString().toByteArray(Charsets.UTF_8));entry("study.json",temp.readBytes())
                for(file in sources+studyFiles) {
                    check();require(file.isFile) {"Один из файлов набора отсутствует"}
                    val name=file.relativeTo(root).invariantSeparatorsPath
                    require(file.canonicalPath==File(root.canonicalFile,name).normalize().path) {"Небезопасный путь экспорта"}
                    require(file.length()<=128L*1024*1024);total+=file.length();require(total<=512L*1024*1024) {"Набор больше512МиБ. Экспортируйте материалы отдельно"}
                    zip.putNextEntry(ZipEntry(name));file.inputStream().use {input ->val bytes=ByteArray(32*1024);while(true){check();val n=input.read(bytes);if(n<0)break;zip.write(bytes,0,n)}};zip.closeEntry()
                }
            }
        } finally {temp.delete()}
    }
}

@Composable internal fun rememberStudySetExport(repository: QuizRepository,data: StudyData): (StudySet)->Unit {
    val context=LocalContext.current;val auth=LocalAuth.current;val uid=auth.account?.uid;val scope=rememberCoroutineScope()
    var pendingId by rememberSaveable {mutableStateOf<String?>(null)};var busy by remember {mutableStateOf(false)}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {uri ->
        val id=pendingId
        if(uri!=null && id!=null && !busy) {busy=true;scope.launch {
            try {withContext(Dispatchers.IO) {
                check(auth.account?.uid==uid) {"Аккаунт изменился"}
                val set=repository.sets.list().first {it.id==id};val worker=currentCoroutineContext()
                val temp=File.createTempFile("set-export-",".zip",context.cacheDir)
                try {
                    temp.outputStream().use {StudySetExport.write(repository.workspaceRoot,set,data,it) {worker.ensureActive()}}
                    worker.ensureActive();context.contentResolver.openOutputStream(uri)?.use {output ->temp.inputStream().use {input ->
                        val bytes=ByteArray(32*1024);while(true){worker.ensureActive();val n=input.read(bytes);if(n<0)break;output.write(bytes,0,n)}
                    }} ?: error("Не удалось открыть файл")
                } finally {temp.delete()}
            };Toast.makeText(context,"Набор сохранён в ZIP",Toast.LENGTH_LONG).show()}
            catch(e: CancellationException){throw e}catch(_: Exception){Toast.makeText(context,"Не удалось экспортировать набор. Исходные данные не изменены",Toast.LENGTH_LONG).show()}
            finally {busy=false;pendingId=null}
        }}
    }
    return {set ->if(!busy)scope.launch {try {withContext(Dispatchers.IO){repository.sets.ensure(set)};pendingId=set.id;launcher.launch("TestHelper-study-set.zip")}
        catch(e: CancellationException){throw e}catch(_: Exception){Toast.makeText(context,"Не удалось открыть набор для экспорта",Toast.LENGTH_LONG).show()}}}
}
