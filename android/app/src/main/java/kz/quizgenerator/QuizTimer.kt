package kz.quizgenerator

internal object QuizTimer {
    fun deadline(now: Long, minutes: Int): Long =
        if (minutes == 0) 0L else now + minutes * 60_000L

    fun secondsLeft(deadline: Long, now: Long): Int =
        (if (deadline == 0L) 0L else (deadline - now).coerceAtLeast(0).plus(999L).div(1000L)).toInt()

    fun expired(deadline: Long, now: Long): Boolean = deadline != 0L && now >= deadline
}
