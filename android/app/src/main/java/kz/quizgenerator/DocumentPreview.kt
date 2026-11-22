package kz.quizgenerator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

private data class OriginalPage(val bitmap: Bitmap,val page: Int,val count: Int)
@Composable internal fun DocumentPreview(file: File,pdf: Boolean,onClose: ()->Unit) {
    var index by rememberSaveable(file.absolutePath) {mutableIntStateOf(0)}
    var zoom by rememberSaveable(file.absolutePath) {mutableFloatStateOf(1f)}
    val state by produceState<Result<OriginalPage>?>(null,file,index,pdf) {
        value=null
        try {value=Result.success(withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            require(file.length() in 1..MaterialStore.MAX_SOURCE) {"Неподдерживаемый размер документа"}
            if(pdf)ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use {fd->PdfRenderer(fd).use {renderer->
                require(renderer.pageCount in 1..5000);renderer.openPage(index.coerceIn(0,renderer.pageCount-1)).use {page->
                    val (w,h)=OcrLimits.dimensions(page.width,page.height)
                    val scale=minOf(1500.0/maxOf(w,h),3.0)
                    val bitmap=Bitmap.createBitmap(maxOf(1,(w*scale).toInt()),maxOf(1,(h*scale).toInt()),Bitmap.Config.ARGB_8888)
                    try {bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)}catch(e: Exception){bitmap.recycle();throw e}
                    OriginalPage(bitmap,index,renderer.pageCount)
                }
            }} else {
                val options=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeFile(file.absolutePath,options)
                OcrLimits.dimensions(options.outWidth,options.outHeight)
                var sample=1;while(maxOf(options.outWidth,options.outHeight)/sample>1500)sample*=2
                options.inJustDecodeBounds=false;options.inSampleSize=sample;options.inPreferredConfig=Bitmap.Config.ARGB_8888
                val bitmap=BitmapFactory.decodeFile(file.absolutePath,options) ?: error("Не удалось открыть изображение")
                val oriented=try {ImageOrientation.apply(bitmap,file)} catch(e: Exception){bitmap.recycle();throw e}
                if(oriented!==bitmap)bitmap.recycle()
                OriginalPage(oriented,0,1)
            }
        })}catch(e: CancellationException){throw e}catch(e: Exception){value=Result.failure(e)}
    }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        val page=state?.getOrNull()
        Row {TextButton(onClick=onClose){Text("К тексту")}
            if(pdf){TextButton(enabled=page!=null && index>0,onClick={index--;zoom=1f}){Text("Назад")};TextButton(enabled=page!=null && index+1<page.count,onClick={index++;zoom=1f}){Text("Далее")}}}
        if(page!=null) {
            Text(if(pdf)"Страница "+(page.page+1)+" из "+page.count else "Исходное изображение")
            Slider(zoom,{zoom=it},valueRange=1f..2.5f)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val width=maxWidth*zoom
                Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
                    Image(page.bitmap.asImageBitmap(),"Исходный документ",Modifier.width(width).aspectRatio(page.bitmap.width.toFloat()/page.bitmap.height))
                }
            }
        } else if(state==null)StudyLoading("Открываем оригинал")
        state?.exceptionOrNull()?.let {StudyError("Не удалось отобразить оригинал. Извлечённый текст доступен отдельно; исходный файл сохранён.")}
    }
}
