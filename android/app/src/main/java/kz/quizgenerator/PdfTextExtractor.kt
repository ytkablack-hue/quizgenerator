package kz.quizgenerator

import android.annotation.TargetApi
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.InputStream

/** Uses the installed Android PDF engine, without adding a native parser or sending files online. */
internal object PdfTextExtractor {
    private const val MAX_BYTES = 64L * 1024 * 1024
    fun paragraphs(source: InputStream, directory: File, size: Long?, progress: (Float?) -> Unit, consume: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < 35) throw IllegalArgumentException(
            "Локальный текст PDF доступен с Android 15. На этом устройстве выберите TXT/DOCX или распознавание с ИИ после согласия на отправку файла.")
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
            readPages(file, progress, consume)
        } finally { file.delete() }
    }

    @TargetApi(35)
    private fun readPages(file: File, progress: (Float?) -> Unit, consume: (String) -> Unit) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                PdfTextPages.consume(renderer.pageCount, { progress(null) }, { page ->
                    progress(.4f+.59f*page/renderer.pageCount.coerceAtLeast(1))
                    renderer.openPage(page).use { it.textContents.map { block -> block.text } }
                }, consume)
            }
        }
        progress(1f)
    }
}

/** Shared bounds/cancellation and fail-closed handling of mixed text/scan documents. */
internal object PdfTextPages {
    fun consume(count: Int, checkCancelled: () -> Unit, readPage: (Int) -> List<String>, emit: (String) -> Unit) {
        require(count in 1..5000) { "PDF должен содержать от 1 до 5000 страниц" }
        var characters=0L
        for(page in 0 until count) {
            checkCancelled()
            val text=readPage(page)
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
