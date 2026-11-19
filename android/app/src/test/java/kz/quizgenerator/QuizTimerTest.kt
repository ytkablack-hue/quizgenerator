package kz.quizgenerator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizTimerTest {
    @Test fun timerUsesElapsedTimeInsteadOfCountingCallbacks() {
        val deadline = QuizTimer.deadline(1000, 10)
        assertEquals(600, QuizTimer.secondsLeft(deadline, 1000))
        assertEquals(1, QuizTimer.secondsLeft(deadline, deadline - 1))
        assertEquals(0, QuizTimer.secondsLeft(deadline, deadline + 4000))
        assertTrue(QuizTimer.expired(deadline, deadline))
    }

    @Test fun unlimitedTestNeverExpires() {
        val deadline = QuizTimer.deadline(1000, 0)
        assertFalse(QuizTimer.expired(deadline, Long.MAX_VALUE))
    }
}
