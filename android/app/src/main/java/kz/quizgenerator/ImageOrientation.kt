package kz.quizgenerator
import android.graphics.Bitmap
import java.io.File
internal object ImageOrientation {
    fun apply(bitmap: Bitmap,file: File): Bitmap {
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
}
