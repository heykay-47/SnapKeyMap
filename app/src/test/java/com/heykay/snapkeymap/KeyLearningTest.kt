package com.heykay.snapkeymap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyLearningTest {
    private val baseline = 0L..5000L
    private val taps = listOf(6000L..9000L, 10000L..13000L, 14000L..17000L)
    private val hold = 18000L..21500L

    private fun log(time: Long, tag: String, message: String) = Observation(time, KeyLearning.logKey(tag, message))

    /** Background noise that shows up all the time, including while the phone is left alone. */
    private fun noise() = (0L..21000L step 400L).map { log(it, "BatteryService", "level=${it % 97}") }

    private fun snapKeyPress(time: Long) =
        log(time, "KEYLOG_OplusKeyEventUtil", "should not notify undefined keys in restrict listen mode, code=$time")

    @Test
    fun findsTheLogLineThatOnlyAppearsWhenPressing() {
        val observations = noise() +
            listOf(7000L, 11000L, 15000L).map(::snapKeyPress) +
            (18200L..20200L step 180L).map(::snapKeyPress)

        val result = KeyLearning.analyze(observations, baseline, taps, hold)

        result as LearnResult.LogLineFound
        assertEquals("KEYLOG_OplusKeyEventUtil", result.signature.tag)
        assertTrue(result.signature.matches("KEYLOG_OplusKeyEventUtil", "should not notify undefined keys in restrict listen mode, code=1"))
        assertTrue(result.holdRepeats)
        assertEquals(270L, result.releaseGapMs) // 180 ms between repeats × 1.5
    }

    @Test
    fun prefersTheKeyLineOverTheStockActionItTriggers() {
        val observations = noise() + listOf(7000L, 11000L, 15000L).flatMap { t ->
            listOf(snapKeyPress(t)) + (0L..20L).map { log(t + 50 + it * 10, "WindowManager", "animating screenshot frame $it") }
        }

        val result = KeyLearning.analyze(observations, baseline, taps, hold) as LearnResult.LogLineFound

        assertEquals("KEYLOG_OplusKeyEventUtil", result.signature.tag)
        assertFalse(result.holdRepeats)
    }

    @Test
    fun toleratesOneMissedPress() {
        val observations = noise() + listOf(7000L, 15000L).map(::snapKeyPress)

        val result = KeyLearning.analyze(observations, baseline, taps, hold)

        assertTrue(result is LearnResult.LogLineFound)
    }

    @Test
    fun ignoresLinesThatAlsoAppearWhenNotPressing() {
        val observations = noise() + listOf(1000L, 7000L, 11000L, 15000L).map(::snapKeyPress)

        assertEquals(LearnResult.NothingFound, KeyLearning.analyze(observations, baseline, taps, hold))
    }

    @Test
    fun prefersARealKeyEventOverLogLines() {
        val observations = noise() + listOf(7000L, 11000L, 15000L).flatMap { t ->
            listOf(snapKeyPress(t), Observation(t, KeyLearning.keyEventKey(0, 766)))
        }

        val result = KeyLearning.analyze(observations, baseline, taps, hold)

        assertEquals(LearnResult.KeyEventFound(LearnedKey(0, 766)), result)
    }

    @Test
    fun findsNothingWhenNothingHappens() {
        assertEquals(LearnResult.NothingFound, KeyLearning.analyze(noise(), baseline, taps, hold))
    }
}
