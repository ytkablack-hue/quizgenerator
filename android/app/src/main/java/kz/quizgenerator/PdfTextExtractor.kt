package kz.quizgenerator

import androidx.annotation.RequiresApi
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.InputStream

/** Uses the installed Android PDF engine, without adding a native parser or sending files online. */
internal object PdfTextExtractor {
    private const val MAX_BYTES = 64L * 1024 * 1024
    fun paragraphs(source: InputStream, directory: File, size: Long?, progress: (Float?) -> Unit, consume: (String) -> Unit, onEmpty: ((Int)->Unit)? = null, ocr: ((PdfRenderer.Page,(Float)->Unit)->String)? = null): Boolean {
        require(size == null || size <= MAX_BYTES) { "PDF больше 64 МБ. Разделите документ" }
        val file = File.createTempFile("testhelper-pdf-", ".pdf", directory)
        try {
            var copied = 0L
            file.outputStream().buffered().use { out ->
                val buffer = ByteArray(32 * 1024)
                while(true) {
                    progress(size?.takeIf { it>0 }?.let { .4f*copied/it })
                    val n=source.read(buffer);if(n<0)break
                    copied+=n;require(copied<=MAX_BYTES) { "PDF больше 64 МБ. Разделите документ" }
                    out.write(buffer,0,n)
                }
            }
            return readPages(file, progress, consume,ocr,onEmpty)
        } finally { file.delete() }
    }

    private fun readPages(file: File, progress: (Float?) -> Unit, consume: (String) -> Unit,ocr: ((PdfRenderer.Page,(Float)->Unit)->String)?,onEmpty: ((Int)->Unit)?): Boolean {
        var usedOcr=false
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                PdfTextPages.consume(renderer.pageCount, { progress(null) }, { page ->
                    progress(.4f+.59f*page/renderer.pageCount.coerceAtLeast(1))
                    renderer.openPage(page).use { current ->
                        val text=if(Build.VERSION.SDK_INT>=35)textContents(current) else emptyList()
                        if(text.any {it.isNotBlank()})text else {
                            val recognize=ocr ?: throw IllegalArgumentException("Страница требует OCR")
                            usedOcr=true
                            listOf(recognize(current) {fraction -> progress(.4f+.59f*(page+fraction)/renderer.pageCount.coerceAtLeast(1))})
                        }
                    }
                }, consume,onEmpty)
            }
        }
        progress(1f)
        return usedOcr
    }
    @RequiresApi(35)
    private fun textContents(page: PdfRenderer.Page): List<String> = page.textContents.map {it.text}
}

/** Shared bounds/cancellation and fail-closed handling of mixed text/scan documents. */
internal object PdfTextPages {
    fun consume(count: Int, checkCancelled: () -> Unit, readPage: (Int) -> List<String>, emit: (String) -> Unit,onEmpty: ((Int)->Unit)?=null) {
        require(count in 1..5000) { "PDF должен содержать от 1 до 5000 страниц" }
        var characters=0L
        for(page in 0 until count) {
            checkCancelled()
            val text=readPage(page)
            if(text.none {it.isNotBlank()} && onEmpty!=null) {onEmpty(page);continue}
            require(text.any { it.isNotBlank() }) { "Страница "+(page+1)+" не содержит текста (возможен скан). Используйте распознавание с ИИ; частичный импорт не сохранён" }
            for(block in text) {
                checkCancelled()
                characters+=block.length+1L
                require(characters<=8_000_000) { "В PDF больше 8 млн символов. Разделите документ" }
                block.lineSequence().forEach(emit)
            }
        }
    }
}
