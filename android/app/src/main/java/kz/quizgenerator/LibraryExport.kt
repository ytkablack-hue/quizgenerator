package kz.quizgenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun rememberLibraryExport(root: File): () -> Unit {
    val context=LocalContext.current
    val auth=LocalAuth.current
    val uid=auth.account?.uid
    val scope=rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if(uri!=null && !busy) {
            busy=true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        check(auth.account?.uid==uid) { "Аккаунт изменился" }
                        val worker=kotlin.coroutines.coroutineContext
                        val temp=File.createTempFile("testhelper-backup-",".zip",context.cacheDir)
                        try {
                            temp.outputStream().use { LibraryBackup.write(root,it) { worker.ensureActive() } }
                            worker.ensureActive()
                            context.contentResolver.openOutputStream(uri)?.use { out -> temp.inputStream().use { it.copyTo(out) } }
                                ?: error("Не удалось открыть файл резервной копии")
                        } finally { temp.delete() }
                    }
                    Toast.makeText(context,"Копия сохранена. JSON-тесты из ZIP можно снова импортировать",Toast.LENGTH_LONG).show()
                } catch(e: kotlinx.coroutines.CancellationException) { throw e }
                catch(_: Exception) { Toast.makeText(context,"Не удалось сохранить копию. Исходная библиотека не изменена",Toast.LENGTH_LONG).show() }
                finally { busy=false }
            }
        }
    }
    return { if(!busy)launcher.launch("TestHelper-library-backup.zip") }
}
