package kz.quizgenerator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.*
import java.util.UUID

@Composable internal fun EditorAnswerSearch(draft: QuizDraft,repository: QuizRepository,ownerSetId: String?,onBack: ()->Unit,onStored: (Quiz)->Unit,onQuiz: (Quiz)->Unit,onLectureAi: (Lecture,String)->Unit) {
    val scope=rememberCoroutineScope()
    var setId by rememberSaveable {mutableStateOf<String?>(null)}
    var bankId by rememberSaveable {mutableStateOf<String?>(null)}
    var confirm by rememberSaveable {mutableStateOf(true)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    BackHandler(onBack=onBack)
    if(setId!=null && bankId!=null) {
        StudyWorkflows(repository,setId!!,listOf(draft.quiz),BatchKind.ANSWERS,bankId,onBack,onStored,onQuiz,onLectureAi)
        return
    }
    StudyColumn("Найти ответы с ИИ",onBack) {
        Text("Сначала сохраните проверенный банк. Затем приложение подготовит список вопросов без подтверждённых ключей.")
        Text("Документальные и подтверждённые вами ответы останутся без изменений. Облачная функция ожидает обновления сервера.")
        if(busy)StudyLoading("Сохраняем банк и открываем задания")
        error?.let {StudyError(it)}
        StitchButton(enabled=!busy,onClick={confirm=true}){Text("Подготовить поиск ответов")}
    }
    if(confirm)AlertDialog(onDismissRequest={if(!busy)onBack()},title={Text("Сохранить банк для поиска ответов?")},text={Text("Правки будут сохранены. Если банк ещё не связан с учебным набором, будет создан набор с его названием. Материал не отправится в облако: разрешение на серию запросов запрашивается отдельно.")},
        dismissButton={TextButton(enabled=!busy,onClick=onBack){Text("Отмена")}},confirmButton={TextButton(enabled=!busy,onClick={
            confirm=false;busy=true;error=null
            scope.launch {try {
                val bank=if(bankId!=null)draft.quiz else withContext(Dispatchers.IO) {
                    val value=draft.quiz.copy(title=draft.quiz.title.trim());EditorRules.validate(value.title,value.questions)
                    if(draft.original==null)repository.addQuiz(value) else repository.saveQuiz(value)
                }
                bankId=bank.id;onStored(bank)
                setId=withContext(Dispatchers.IO){
                    AnswerSearchScope.ensure(repository.sets,bank,ownerSetId).id
                }
            }catch(e: CancellationException){throw e}catch(e: Exception){error=e.message ?: "Не удалось подготовить поиск. Сохранённые данные остаются в библиотеке"}finally{busy=false}}
        }){Text("Сохранить и продолжить")}})
}

internal object AnswerSearchScope {
    fun ensure(store: StudySetStore,bank: Quiz,preferred: String?): StudySet {
        val sets=store.list()
        val owner=sets.firstOrNull {it.id==preferred && !it.archived} ?: sets.firstOrNull {!it.archived && bank.id in it.quizIds}
        if(owner!=null)return store.linkQuiz(owner.id,bank.id)
        val stable=UUID.nameUUIDFromBytes(("answer-search:"+bank.id).toByteArray()).toString()
        val id=if(sets.any {it.id==stable})UUID.randomUUID().toString() else stable
        return store.ensure(StudySet(id,bank.title.take(160),quizIds=listOf(bank.id)))
    }
}
