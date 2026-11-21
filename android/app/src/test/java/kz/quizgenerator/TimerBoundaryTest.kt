package kz.quizgenerator
import org.junit.Assert.*
import org.junit.Test

class TimerBoundaryTest {
    @Test fun presetsAndCustomBounds() {
        assertEquals(listOf(0,5,10,15,20,30,45,60), QuizTimer.presets)
        assertTrue(QuizTimer.validMinutes(180)); assertFalse(QuizTimer.validMinutes(181)); assertFalse(QuizTimer.validMinutes(-1))
        for (invalid in listOf(-1, 181, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { QuizTimer.deadline(0, invalid) }
        }
    }
    @Test fun backgroundTimeAndExactDeadline() {
        val deadline=QuizTimer.deadline(500,5)
        assertEquals(300,QuizTimer.secondsLeft(deadline,500))
        assertEquals(60,QuizTimer.secondsLeft(deadline,deadline-60_000))
        assertEquals(1,QuizTimer.secondsLeft(deadline,deadline-1))
        assertEquals(0,QuizTimer.secondsLeft(deadline,deadline))
        assertTrue(QuizTimer.expired(deadline,deadline)); assertFalse(QuizTimer.expired(0,Long.MAX_VALUE))
    }
    @Test fun warningAndOverflow() {
        assertTrue(QuizTimer.warning(60)); assertTrue(QuizTimer.warning(1)); assertFalse(QuizTimer.warning(61)); assertFalse(QuizTimer.warning(0))
        assertThrows(ArithmeticException::class.java) { QuizTimer.deadline(Long.MAX_VALUE,1) }
    }
}
