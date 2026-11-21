package kz.quizgenerator

internal object QuizTimer {
    const val MAX_MINUTES = 180
    val presets = listOf(0, 5, 10, 15, 20, 30, 45, 60)
    fun validMinutes(minutes: Int) = minutes in 0..MAX_MINUTES
    fun deadline(now: Long, minutes: Int): Long {
        require(now >= 0 && validMinutes(minutes)) { "Время должно быть от 1 до 180 минут или без таймера" }
        if (minutes == 0) return 0L
        return Math.addExact(now, minutes * 60_000L)
    }
    fun secondsLeft(deadline: Long, now: Long): Int {
        if (deadline == 0L || now >= deadline) return 0
        val remaining = deadline - now.coerceAtLeast(0)
        return (remaining / 1000 + if (remaining % 1000 > 0) 1 else 0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
    fun expired(deadline: Long, now: Long) = deadline != 0L && now >= deadline
    fun warning(seconds: Int) = seconds in 1..60
}
