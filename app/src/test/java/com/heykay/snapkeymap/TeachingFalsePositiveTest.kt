package com.heykay.snapkeymap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeachingFalsePositiveTest {
    @Test
    fun positiveOnlyF12TeachingAcceptsGenericStylusLogFromUnseenF11() {
        val baseline = 0L..5000L
        val taps = listOf(6000L..9000L, 10000L..13000L, 14000L..17000L)
        val hold = 18000L..21500L
        val syntheticWriterUid = "424242"
        val tag = "StylusModule"
        val reportedSignature = "StylusModule.onUpdateToolType():# Update tool type = #"

        // Only the normalized signature was reported. UID, numbers, and times are synthetic.
        // F12 (142) and F11 (141) describe the scenario, not metadata carried by this log.
        val f12TeachingLogs = listOf(7000L, 11000L, 15000L, 18200L).mapIndexed { index, time ->
            Observation(time, KeyLearning.logKey(
                syntheticWriterUid, tag, "StylusModule.onUpdateToolType():${900 + index} Update tool type = ${10 + index}",
            ))
        }
        val baselineNoise = Observation(1000L, KeyLearning.logKey(
            syntheticWriterUid, "SyntheticBackground", "idle sample 1",
        ))
        val observations = listOf(baselineNoise) + f12TeachingLogs
        val genericLog = KeyLearning.logKey(syntheticWriterUid, tag, reportedSignature)
        assertFalse(observations.any { it.timeMs in baseline && it.key == genericLog })

        val result = KeyLearning.analyze(observations, baseline, taps, hold) as LearnResult.LogLineFound

        assertEquals(KeySignature(tag, reportedSignature, syntheticWriterUid), result.signature)
        assertEquals(null, result.inputKey)
        assertEquals(null, result.logKeyCode)
        val config = Config(
            enabled = true,
            logSignature = result.signature,
            logKeyCode = result.logKeyCode,
            holdRepeats = result.holdRepeats,
            releaseGapMs = result.releaseGapMs,
        )
        assertTrue(config.isTaught)

        // This negative challenge is withheld from analyze, as in positive-only teaching.
        val unseenF11Message = "StylusModule.onUpdateToolType():9876 Update tool type = 54"
        assertEquals(reportedSignature, normalizeLogMessage(unseenF11Message))
        val acceptedByFullLogFallback = KeyMatching.matches(config.keyBinding(),
            KeySample.Log(syntheticWriterUid, tag, unseenF11Message, null))

        assertTrue("The unseen F11 log incorrectly passes the learned F12 runtime filter", acceptedByFullLogFallback)
        println("REPRO synthetic positive-only F12 teaching learned StylusModule; unseen F11 passed full non-Oplus log acceptance")
    }
}
