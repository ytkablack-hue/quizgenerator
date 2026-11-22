package kz.quizgenerator
internal object PreviewTextBoundary {
    fun at(text: String,index: Int): Int {
        val p=index.coerceIn(0,text.length)
        return if(p in 1 until text.length && Character.isLowSurrogate(text[p]))p-1 else p
    }
}
