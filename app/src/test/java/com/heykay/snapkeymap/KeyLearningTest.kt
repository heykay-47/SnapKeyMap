package com.heykay.snapkeymap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyLearningTest {
    private val baseline = 0L..5000L
    private val taps = listOf(6000L..9000L, 10000L..13000L, 14000L..17000L)
    private val hold = 18000L..21500L

    private fun log(time: Long, tag: String, message: String, uid: String = "1000") =
        Observation(time, KeyLearning.logKey(uid, tag, message))

    /** Background noise that shows up all the time, including while the phone is left alone. */
    private fun noise() = (0L..21000L step 400L).map { log(it, "BatteryService", "level=${it % 97}") }

    private fun snapKeyPress(time: Long) =
        log(time, "KEYLOG_OplusKeyEventUtil", "should not notify undefined keys in restrict listen mode, code=$time")

    private fun findX9Press(downTime: Long, upTime: Long): List<Observation> {
        val oplusMessage = "should not notify undefined keys in restrict listen mode"
        return listOf(
            log(downTime, "KEYLOG_KeyCombinationManager",
                "down true downTime $downTime action 0 count 0 isDefaultDisplayInteractive true eventTime $downTime mDownTimes: {}"),
            log(downTime + 1, "KEYLOG_OplusKeyEventUtil", oplusMessage),
            log(downTime + 1, "KEYLOG_OplusKeyEventUtil", oplusMessage),
            log(upTime, "KEYLOG_KeyCombinationManager",
                "down false downTime $downTime action 1 count 0 isDefaultDisplayInteractive true eventTime $upTime mDownTimes: {780=$downTime}"),
            log(upTime + 1, "KEYLOG_OplusKeyEventUtil", oplusMessage),
            log(upTime + 1, "KEYLOG_OplusKeyEventUtil", oplusMessage),
        )
    }

    @Test
    fun findsTheLogLineThatOnlyAppearsWhenPressing() {
        val observations = noise() +
            listOf(7000L, 11000L, 15000L).map(::snapKeyPress) +
            (18200L..20200L step 180L).map(::snapKeyPress)

        val result = KeyLearning.analyze(observations, baseline, taps, hold)

        result as LearnResult.LogLineFound
        assertEquals("KEYLOG_OplusKeyEventUtil", result.signature.tag)
        assertTrue(result.signature.matches("1000", "KEYLOG_OplusKeyEventUtil", "should not notify undefined keys in restrict listen mode, code=1"))
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
    fun prefersCanonicalOplusEdgesOverDownOnlyCombinationLog() {
        val observations = noise() + listOf(7000L, 11000L, 15000L).flatMap { t ->
            findX9Press(t, t + 100)
        } + findX9Press(18200L, 22000L)

        val result = KeyLearning.analyze(observations, baseline, taps, hold) as LearnResult.LogLineFound

        assertEquals(
            KeySignature("KEYLOG_OplusKeyEventUtil", "should not notify undefined keys in restrict listen mode", "1000"),
            result.signature,
        )
        assertFalse(result.holdRepeats)
        assertTrue(result.canDetectHold)
    }

    @Test
    fun fallsBackToCombinationLogWhenCanonicalOplusLineAppearsInBaseline() {
        val observations = noise() +
            log(1000L, "KEYLOG_OplusKeyEventUtil", "should not notify undefined keys in restrict listen mode") +
            listOf(7000L, 11000L, 15000L).flatMap { t -> findX9Press(t, t + 100) } +
            findX9Press(18200L, 22000L)

        val result = KeyLearning.analyze(observations, baseline, taps, hold) as LearnResult.LogLineFound

        assertEquals(
            KeySignature("KEYLOG_KeyCombinationManager",
                "down true downTime # action # count # isDefaultDisplayInteractive true eventTime # mDownTimes: {}", "1000"),
            result.signature,
        )
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

    @Test
    fun learnsInputAlongsideLogFallbackOnlyWhenItMatchesTapsAndHold() {
        val input = KeyLearning.inputKey("BTN_TRIGGER_HAPPY32")
        val other = KeyLearning.inputKey("KEY_VOLUMEUP")
        val times = listOf(7000L, 11000L, 15000L, 18200L)
        val observations = times.map(::snapKeyPress) + times.map { Observation(it, input) } +
            listOf(1000L, 7000L, 11000L, 15000L, 18200L).map { Observation(it, other) }

        val result = KeyLearning.analyze(observations, baseline, taps, hold) as LearnResult.LogLineFound
        assertEquals(InputKey("BTN_TRIGGER_HAPPY32"), result.inputKey)
        assertEquals("KEYLOG_OplusKeyEventUtil", result.signature.tag)
        assertTrue(result.canDetectHold)

        val withoutHold = observations.filterNot { it.key == input && it.timeMs in hold }
        val fallback = KeyLearning.analyze(withoutHold, baseline, taps, hold) as LearnResult.LogLineFound
        assertEquals(null, fallback.inputKey)
    }

    @Test
    fun inputOnlyOrUnmatchedInputDoesNotReplaceLogLearning() {
        val input = KeyLearning.inputKey("BTN_TRIGGER_HAPPY32")
        val times = listOf(7000L, 11000L, 15000L, 18200L)
        assertEquals(LearnResult.NothingFound, KeyLearning.analyze(
            times.map { Observation(it, input) }, baseline, taps, hold,
        ))
        val result = KeyLearning.analyze(
            times.map(::snapKeyPress) + listOf(Observation(7000L, input), Observation(18200L, input)),
            baseline, taps, hold,
        ) as LearnResult.LogLineFound
        assertEquals(null, result.inputKey)
    }

    @Test
    fun knownOplusLineRequiresSystemUidWithOrWithoutALearnedSignature() {
        val tag = "KEYLOG_OplusKeyEventUtil"
        val message = "should not notify undefined keys in restrict listen mode"
        val signatures = listOf(null, KeySignature(tag, message), KeySignature(tag, message, "1000"))
        for (signature in signatures) {
            assertFalse(acceptsKeyLog(signature, "10234", tag, message))
            assertTrue(acceptsKeyLog(signature, "1000", tag, message))
            assertTrue(acceptsKeyLog(signature, "system", tag, message))
        }
        assertFalse(isKnownOplusKeyLine("10234", tag, message))
        assertTrue(isKnownOplusKeyLine("1000", tag, message))
        assertTrue(isKnownOplusKeyLine("system", tag, message))
        assertFalse(KeySignature(tag, message, "10234").matches("10234", tag, message))
    }

    @Test
    fun learningIgnoresSpoofedOplusLinesAndNormalizesSystemUid() {
        val tag = "KEYLOG_OplusKeyEventUtil"
        val message = "should not notify undefined keys in restrict listen mode"
        val times = listOf(7000L, 11000L, 15000L)
        val spoofed = times.map { log(it, tag, message, "10234") }
        assertEquals(LearnResult.NothingFound, KeyLearning.analyze(spoofed, baseline, taps, hold))

        val genuine = times.mapIndexed { index, time ->
            log(time, tag, message, if (index == 0) "1000" else "system")
        }
        val result = KeyLearning.analyze(spoofed + genuine, baseline, taps, hold) as LearnResult.LogLineFound
        assertEquals(KeySignature(tag, message, "1000"), result.signature)
    }

    @Test
    fun learnedSignatureRequiresTheSameWriterUid() {
        val observations = listOf(7000L, 11000L, 15000L).map { log(it, "OtherKey", "pressed", "10050") }
        val result = KeyLearning.analyze(observations, baseline, taps, hold) as LearnResult.LogLineFound
        assertEquals("10050", result.signature.uid)
        assertTrue(result.signature.matches("10050", "OtherKey", "pressed"))
        assertFalse(result.signature.matches("10051", "OtherKey", "pressed"))
    }

    @Test
    fun legacyNonOplusSignatureMatchesNothingAndNeedsTeachingAgain() {
        val signature = KeySignature("OtherKey", "pressed")
        assertFalse(signature.matches("1000", "OtherKey", "pressed"))
        assertFalse(signature.matches("10050", "OtherKey", "pressed"))
        assertFalse(acceptsKeyLog(signature, "1000", "OtherKey", "pressed"))
        assertFalse(Config(logSignature = signature).isTaught)
        assertTrue(Config(logSignature = signature.copy(uid = "10050")).isTaught)
        assertTrue(Config(logSignature = KeySignature(
            "KEYLOG_OplusKeyEventUtil", "should not notify undefined keys in restrict listen mode",
        )).isTaught)
    }
}
