package kz.quizgenerator
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable internal fun MaterialViewer(record: MaterialRecord,text: String,page: Int,onPage: (Int)->Unit,onClose: ()->Unit,sourceFile: java.io.File?=null) {
    val count=((text.length+3999)/4000).coerceAtLeast(1);val index=page.coerceIn(0,count-1)
    var original by rememberSaveable(record.id) {mutableStateOf(false)}
    var size by rememberSaveable(record.id) {mutableFloatStateOf(17f)}
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth()) {Text(record.name,modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton(onClick=onClose){Text("Закрыть")}}
                if(sourceFile!=null && record.format in setOf("pdf","image"))TextButton(onClick={original=!original}) {Text(if(original)"Извлечённый текст" else "Открыть оригинал")}
                if(original && sourceFile!=null) {Box(Modifier.weight(1f)){DocumentPreview(sourceFile,record.format=="pdf"){original=false}};return@Column}
                Text("Текстовый фрагмент "+(index+1)+" из "+count+" · полный текст сохранён")
                Row {TextButton(enabled=index>0,onClick={onPage(index-1)}){Text("Назад")};TextButton(enabled=index+1<count,onClick={onPage(index+1)}){Text("Далее")}}
                Slider(size,{size=it},valueRange=14f..26f)
                SelectionContainer(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Text(text.substring(PreviewTextBoundary.at(text,index*4000),PreviewTextBoundary.at(text,(index+1)*4000)),fontSize=size.sp)
                }
            }
        }
    }
}
