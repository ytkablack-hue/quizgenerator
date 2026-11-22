package kz.quizgenerator
import kotlin.math.roundToInt
/** Image memory is bounded independently of compressed input size. One page is decoded at a time. */
internal object OcrLimits {
    const val FILE_BYTES=64L*1024*1024
    const val LONG_EDGE=2500
    const val MAX_PIXELS=6_250_000L
    fun dimensions(width: Int,height: Int): Pair<Int,Int> {
        require(width>0 && height>0 && width<=100_000 && height<=100_000) {"Некорректные размеры изображения"}
        require(width.toLong()*height<=100_000_000L) {"Изображение больше 100 мегапикселей. Уменьшите его"}
        val scale=minOf(1.0,LONG_EDGE.toDouble()/maxOf(width,height))
        return maxOf(1,(width*scale).roundToInt()) to maxOf(1,(height*scale).roundToInt())
    }
}

internal object OcrReview {
    fun review(quiz: Quiz,usedOcr: Boolean): Quiz = if(!usedOcr)quiz else quiz.copy(questions=quiz.questions.map {
        if(it.evidence==AnswerEvidence.SOURCE)it.copy(evidence=AnswerEvidence.INFERRED) else it
    })
}
