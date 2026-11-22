package kz.quizgenerator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.SystemClock
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import org.json.JSONArray
import kotlinx.coroutines.CancellationException

/** Tesseract never sends documents over the network. Native state belongs to this import only. */
internal class LocalOcr(private val context: Context): Closeable {
    private var api: TessBaseAPI?=null
    private var callback: ((Float)->Unit)?=null
    private var stopped: Exception?=null
    private var started=0L

    private fun engine(progress: (Float)->Unit): TessBaseAPI {
        api?.let {return it}
        progress(0f)
        val directory=installModels(context) {progress(0f)}
        val created=try { TessBaseAPI { value ->
            try {
                if(SystemClock.elapsedRealtime()-started>90_000)throw IllegalArgumentException("OCR страницы превысил 90 секунд. Уменьшите изображение")
                callback?.invoke(value.percent/100f)
            } catch(e: CancellationException) {stopped=e;api?.stop()}
              catch(e: IllegalArgumentException) {stopped=e;api?.stop()}
        } } catch(_: LinkageError) {throw IllegalStateException("OCR-движок несовместим с этим устройством. Используйте TXT/DOCX или облачное распознавание с явным согласием")}
        try {
            require(created.init(directory.absolutePath+"/","rus+kaz+eng",TessBaseAPI.OEM_LSTM_ONLY)) {"Не удалось загрузить OCR-языки"}
            created.pageSegMode=TessBaseAPI.PageSegMode.PSM_AUTO
            api=created
            return created
        } catch(e: Exception) {created.recycle();throw e}
    }

    fun recognize(bitmap: Bitmap, progress: (Float)->Unit): String {
        val engine=engine(progress)
        callback=progress;stopped=null;started=SystemClock.elapsedRealtime()
        try {
            progress(0f);engine.setImage(bitmap)
            // HOCR performs recognition with the documented stop/progress mechanism.
            engine.getHOCRText(0)
            stopped?.let {throw it};progress(.99f)
            val text=engine.getUTF8Text().orEmpty()
            require(text.isNotBlank()) {"OCR не нашёл текста. Проверьте качество и ориентацию изображения"}
            require(text.length<=8_000_000) {"Слишком много распознанного текста"}
            progress(1f)
            return text
        } finally {callback=null;engine.clear()}
    }

    fun page(page: PdfRenderer.Page, progress: (Float)->Unit): String {
        val (width,height)=OcrLimits.dimensions(page.width,page.height)
        // PDF dimensions are points; upscale small pages for legible glyphs within the same memory bound.
        val scale=minOf(OcrLimits.LONG_EDGE.toDouble()/maxOf(width,height),3.0)
        val w=maxOf(1,(width*scale).toInt());val h=maxOf(1,(height*scale).toInt())
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        try { bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT);return recognize(bitmap,progress) }
        finally {bitmap.recycle()}
    }

    fun image(source: InputStream,directory: File,size: Long?,progress: (Float?)->Unit): String {
        require(size==null || size<=OcrLimits.FILE_BYTES) {"Изображение больше 64 МБ"}
        val file=File.createTempFile("testhelper-ocr-",".img",directory)
        try {
            file.outputStream().buffered().use {out ->
                val bytes=ByteArray(32*1024);var total=0L
                while(true) {progress(null);val n=source.read(bytes);if(n<0)break;total+=n;require(total<=OcrLimits.FILE_BYTES) {"Изображение больше 64 МБ"};out.write(bytes,0,n)}
            }
            val options=BitmapFactory.Options().apply {inJustDecodeBounds=true}
            BitmapFactory.decodeFile(file.absolutePath,options)
            require(options.outMimeType in setOf("image/jpeg","image/png")) {"Поддерживаются только JPG и PNG"}
            val (w,h)=OcrLimits.dimensions(options.outWidth,options.outHeight)
            var sample=1
            while(options.outWidth/sample>w || options.outHeight/sample>h)sample*=2
            options.inJustDecodeBounds=false;options.inSampleSize=sample;options.inPreferredConfig=Bitmap.Config.ARGB_8888
            progress(0f)
            val bitmap=BitmapFactory.decodeFile(file.absolutePath,options) ?: error("Не удалось декодировать изображение")
            var oriented: Bitmap?=null
            try {
                oriented=orient(bitmap,file)
                return recognize(oriented) {progress(it)}
            } finally {if(oriented!==bitmap)oriented?.recycle();bitmap.recycle()}
        } finally {file.delete()}
    }
    private fun orient(bitmap: Bitmap,file: File): Bitmap {
        val orientation=runCatching {android.media.ExifInterface(file.absolutePath).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)}.getOrDefault(1)
        val matrix=android.graphics.Matrix()
        when(orientation) {
            2 -> matrix.setScale(-1f,1f)
            3 -> matrix.setRotate(180f)
            4 -> {matrix.setRotate(180f);matrix.postScale(-1f,1f)}
            5 -> {matrix.setRotate(90f);matrix.postScale(-1f,1f)}
            6 -> matrix.setRotate(90f)
            7 -> {matrix.setRotate(-90f);matrix.postScale(-1f,1f)}
            8 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
    }
    override fun close() {api?.recycle();api=null}

    companion object {
        private val modelLock=Any()
        private fun digest(file: File): String = file.inputStream().use {stream ->
            val digest=MessageDigest.getInstance("SHA-256");val bytes=ByteArray(32*1024)
            while(true) {val n=stream.read(bytes);if(n<0)break;digest.update(bytes,0,n)}
            digest.digest().joinToString("") {"%02x".format(it.toInt() and 255)}
        }
        private fun installModels(context: Context,checkCancelled: ()->Unit): File=synchronized(modelLock) {
            val root=File(context.noBackupFilesDir,"ocr-v1")
            val directory=File(root,"tessdata");check(directory.isDirectory || directory.mkdirs()) {"Нет места для OCR-языков"}
            val manifest=JSONArray(context.assets.open("ocr/models.json").bufferedReader().use {it.readText()})
            for(i in 0 until manifest.length()) {
                checkCancelled();val item=manifest.getJSONObject(i);val language=item.getString("language")
                require(language in setOf("rus","kaz","eng"))
                val target=File(directory,language+".traineddata")
                val hash=item.getString("sha256")
                if(target.exists()) {require(target.length()==item.getLong("bytes") && digest(target)==hash) {"Повреждены локальные OCR-языки. Переустановите приложение, предварительно сохраните библиотеку"};continue}
                val temp=File.createTempFile(".ocr-",".tmp",directory)
                try {
                    context.assets.open("ocr/tessdata/"+language+".traineddata").use {input -> temp.outputStream().use {out ->
                        val bytes=ByteArray(32*1024);while(true) {checkCancelled();val n=input.read(bytes);if(n<0)break;out.write(bytes,0,n)}
                    }}
                    require(temp.length()==item.getLong("bytes") && digest(temp)==hash) {"Не совпадает SHA-256 OCR-языка"}
                    check(!target.exists() && temp.renameTo(target)) {"Не удалось сохранить OCR-язык"}
                } finally {temp.delete()}
            }
            root
        }
    }
}
