package kz.quizgenerator
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

internal class MaterialUiModel: ViewModel() {
    private val work=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var repo: QuizRepository?=null
    private var requestedInput: AiInputMode?=null
    private var reading: Job?=null
    private var attaching: Job?=null
    private var saving: Job?=null
    private val writeLock=Mutex()
    var draft by mutableStateOf(AiDraft());private set
    var records by mutableStateOf<List<MaterialRecord>>(emptyList());private set
    var text by mutableStateOf("");private set
    var parts by mutableStateOf<List<MaterialSlice>>(emptyList());private set
    var progress by mutableStateOf<ImportProgress?>(null);private set
    var error by mutableStateOf<String?>(null);private set
    var ready by mutableStateOf(false);private set
    var setRevision by mutableIntStateOf(0);private set
    var pendingCards by mutableStateOf<List<Flashcard>>(emptyList())
    private fun store()=AiDraftStore(File(requireNotNull(repo).workspaceRoot,"ai-draft.json"))
    fun chooseInput(repository: QuizRepository,mode: AiInputMode) {
        requestedInput=mode
        bind(repository)
        if(ready){requestedInput=null;update(draft.copy(inputMode=mode))}
    }
    fun bind(repository: QuizRepository) {
        if(repo!=null) {require(repo!!.workspaceRoot==repository.workspaceRoot);return}
        repo=repository
        work.launch {
            try {
                val values=withContext(Dispatchers.IO) {store().load() to repository.materials.list()}
                val requested=requestedInput;requestedInput=null
                draft=values.first.let {if(requested==null)it else it.copy(inputMode=requested)};records=values.second.mapNotNull {it.getOrNull()}
                if(values.second.any {it.isFailure})error="Некоторые документы повреждены. Они сохранены на устройстве; загрузите оригиналы повторно"
                ready=true;select(draft.sourceId,false)
                if(requested!=null)update(draft)
            } catch(e: CancellationException) {throw e}
              catch(_: Exception) {error="Не удалось открыть настройки материалов. Исходные файлы не изменены"}
        }
    }
    fun update(value: AiDraft) {
        if(!ready)return
        if(value.extra.length>AiLimits.INSTRUCTIONS || value.topic.length>AiLimits.TOPIC || value.ownText.length>AiLimits.MATERIAL) {error="Слишком длинное поле. Учебный текст прикрепите файлом";return}
        draft=value;saving?.cancel()
        saving=work.launch {delay(200);try {withContext(Dispatchers.IO) {writeLock.withLock {store().save(value)}}}
            catch(e: CancellationException){throw e}catch(_: Exception){error="Не удалось сохранить настройки. Документы сохранены отдельно"}}
    }
    fun select(id: String?,reset: Boolean=true) {
        reading?.cancel();text="";parts=emptyList()
        if(reset)update(draft.copy(sourceId=id,part=0,previewPage=0))
        if(id==null)return
        reading=work.launch {
            try {
                val worker=currentCoroutineContext()
                val result=withContext(Dispatchers.IO) {val full=requireNotNull(repo).materials.text(id);full to MaterialSlices.plan(full) {worker.ensureActive()}}
                text=result.first;parts=result.second
                if(draft.part !in parts.indices)update(draft.copy(part=0))
            } catch(e: CancellationException){throw e}catch(_: Exception){error="Не удалось открыть текст выбранного документа. Загрузите оригинал повторно"}
        }
    }
    fun attach(uri: Uri,setId: String?=null) {
        if(!ready || attaching?.isActive==true)return
        attaching=work.launch {
            try {
                error=null;progress=ImportProgress(DocumentDetails("Документ",null),null,"Загрузка документа")
                val worker=currentCoroutineContext()
                val saved=withContext(Dispatchers.IO) {requireNotNull(repo).attachMaterial(uri) {worker.ensureActive();progress=it}}
                records=listOf(saved)+records
                if(setId!=null)try {withContext(Dispatchers.IO){requireNotNull(repo).sets.linkMaterial(setId,saved.id)}} catch(e: CancellationException){throw e}catch(_: Exception){error="Документ сохранён в библиотеке, но не прикреплён к набору"}
                setRevision++
                update(draft.copy(inputMode=AiInputMode.DOCUMENT));select(saved.id)
            } catch(e: CancellationException){throw e}
              catch(e: Exception){error=if(e is IllegalArgumentException)e.message else "Не удалось прочитать документ. Проверьте формат и свободное место"}
            finally {progress=null}
        }
    }
    fun completed(context: String,part: Int) {
        val values=(draft.completed[context].orEmpty()+part).distinct()
        val next=draft.completed+(context to values)
        if(next.size>100 || next.values.sumOf {it.size}>10000) {error="Ответ получен, но история фрагментов заполнена";return}
        update(draft.copy(completed=next))
    }
    fun cancel() {attaching?.cancel()}
    override fun onCleared() {work.cancel()}
}
internal fun android.content.Context.studyActivity(): androidx.activity.ComponentActivity {
    var current: android.content.Context=this
    while(current is android.content.ContextWrapper) {
        if(current is androidx.activity.ComponentActivity)return current
        current=current.baseContext
    }
    error("Activity недоступна")
}
