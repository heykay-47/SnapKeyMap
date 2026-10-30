package com.heykay.snapkeymap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A logcat time [ms] milliseconds after 11:05:00. */
private fun stamp(ms: Long): String {
    val total = 5 * 60_000 + ms
    return "10-18 11:%02d:%02d.%03d".format(total / 60_000, total / 1000 % 60, total % 1000)
}

class OplusKeyEdgesTest {
    private class Clock {
        var now = 0L
        private val tasks = mutableMapOf<Runnable, Long>()

        fun schedule(task: Runnable, delay: Long) { tasks[task] = now + delay }
        fun cancel(task: Runnable) { tasks.remove(task) }

        fun advanceTo(time: Long) {
            while (true) {
                val next = tasks.entries.minByOrNull { it.value } ?: break
                if (next.value > time) break
                now = next.value
                tasks.remove(next.key)
                next.key.run()
            }
            now = time
        }
    }

    private class Harness(enableHold: Boolean = true) {
        val clock = Clock()
        val gestures = mutableListOf<Gesture>()
        val detector = GestureDetector(clock::schedule, clock::cancel, { 650L }, gestures::add).apply {
            wantsDouble = true
            wantsLong = enableHold
        }
        val edges = OplusKeyEdges(detector::down, detector::up)

        fun duplicatedLine(time: Long, deliveredAt: Long = time) {
            clock.advanceTo(deliveredAt)
            repeat(2) { edges.accept(stamp(time)) }
        }

        fun observedLine(time: Long) {
            clock.advanceTo(time)
            repeat(2) { edges.observe(stamp(time)) }
        }
    }

    @Test
    fun findX9RecordedSingleDoubleAndHoldProduceThreeDifferentGestures() {
        val h = Harness()
        // Exact source offsets from the user's trace, including the duplicate lines.
        listOf(10525L, 10651L, 15779L, 15884L, 15955L, 16056L, 19455L, 22134L)
            .forEach { h.duplicatedLine(it) }
        h.clock.advanceTo(24000)

        assertEquals(listOf(Gesture.SINGLE, Gesture.DOUBLE, Gesture.LONG), h.gestures)
    }

    @Test
    fun doubleTapDoesNotFireTheSingleActionEvenWithHoldUnassigned() {
        val h = Harness(enableHold = false)
        listOf(0L, 105L, 176L, 277L).forEach { h.duplicatedLine(it) }
        h.clock.advanceTo(1000)

        assertEquals(listOf(Gesture.DOUBLE), h.gestures)
    }

    @Test
    fun oneTapWaitsForTheDoubleTapWindow() {
        val h = Harness()
        h.duplicatedLine(0)
        h.duplicatedLine(126)
        h.clock.advanceTo(475)
        assertTrue(h.gestures.isEmpty())
        h.clock.advanceTo(476)
        assertEquals(listOf(Gesture.SINGLE), h.gestures)
    }

    @Test
    fun holdNeedsNoRepeatedLogLinesAndReleaseDoesNotFireSingle() {
        val h = Harness()
        h.duplicatedLine(0)
        h.clock.advanceTo(650)
        assertEquals(listOf(Gesture.LONG), h.gestures)
        h.duplicatedLine(2679)
        h.clock.advanceTo(4000)
        assertEquals(listOf(Gesture.LONG), h.gestures)
    }

    @Test
    fun bufferedEventsAreDeduplicatedBySourceTimestampNotDeliveryTime() {
        val h = Harness()
        listOf(0L, 105L, 176L, 277L).forEach { h.duplicatedLine(it, deliveredAt = 1000) }
        h.clock.advanceTo(2000)
        assertEquals(listOf(Gesture.DOUBLE), h.gestures)
    }

    @Test
    fun duplicateCopiesSplitAcrossAMillisecondStillCountAsOneEdge() {
        // A Find X9 capture logged one edge at 12:07:40.908932 and 12:07:40.909046.
        val h = Harness(enableHold = false)
        fun edge(time: Long, vararg timestamps: String) {
            h.clock.advanceTo(time)
            timestamps.forEach(h.edges::accept)
        }
        edge(0, "10-18 12:07:40.908", "10-18 12:07:40.909")
        edge(100, "10-18 12:07:41.008", "10-18 12:07:41.008")
        h.clock.advanceTo(1000)
        edge(3000, "10-18 12:07:43.908", "10-18 12:07:43.908")
        edge(3100, "10-18 12:07:44.008", "10-18 12:07:44.008")
        h.clock.advanceTo(4000)

        assertEquals(listOf(Gesture.SINGLE, Gesture.SINGLE), h.gestures)
    }

    @Test
    fun copiesAcrossASecondOrMidnightAreOneEdgeButSeparateEdgesAreNot() {
        val edgesSeen = mutableListOf<String>()
        val edges = OplusKeyEdges({ edgesSeen += "down" }, { edgesSeen += "up" })
        listOf(
            "10-18 12:07:40.999", "10-18 12:07:41.000",
            "10-18 12:07:41.020",
            "10-31 23:59:59.999", "11-01 00:00:00.000",
            "11-01 00:00:00.062", "11-01 00:00:00.062",
            "not a timestamp",
        ).forEach(edges::accept)

        assertEquals(listOf("down", "up", "down", "up"), edgesSeen)
    }

    @Test
    fun resetAllowsFirstEventOfANewSession() {
        val edgesSeen = mutableListOf<String>()
        val edges = OplusKeyEdges({ edgesSeen += "down" }, { edgesSeen += "up" })
        edges.accept(stamp(0))
        edges.reset()
        edges.accept(stamp(0))
        assertEquals(listOf("down", "down"), edgesSeen)
    }

    @Test
    fun rawInputHandoffWhilePressedTreatsNextLogEdgeAsRelease() {
        val edgesSeen = mutableListOf<String>()
        val edges = OplusKeyEdges({ edgesSeen += "down" }, { edgesSeen += "up" })
        edges.reset(pressed = true)
        edges.accept(stamp(1000))
        edges.accept(stamp(2000))
        edges.accept(stamp(2100))
        assertEquals(listOf("up", "down", "up"), edgesSeen)
    }

    @Test
    fun finalLearningHoldReleasedAfterFinishDoesNotTurnFirstSingleIntoDouble() {
        val h = Harness(enableHold = false)
        h.detector.reset()
        listOf(0L, 100L, 1000L, 1100L, 2000L, 2100L, 3000L).forEach(h::observedLine)
        h.clock.advanceTo(5000)
        assertTrue(h.gestures.isEmpty())

        h.detector.reset()
        h.duplicatedLine(5100)
        h.clock.advanceTo(6000)
        assertTrue(h.gestures.isEmpty())

        h.duplicatedLine(6100)
        h.duplicatedLine(6200)
        h.clock.advanceTo(6550)
        assertEquals(listOf(Gesture.SINGLE), h.gestures)

        listOf(7000L, 7100L, 7200L, 7300L).forEach { h.duplicatedLine(it) }
        h.clock.advanceTo(9000)
        assertEquals(listOf(Gesture.SINGLE, Gesture.DOUBLE), h.gestures)
    }

    @Test
    fun finalLearningHoldReleasedBeforeFinishLeavesFirstSingleIntact() {
        val h = Harness()
        h.detector.reset()
        h.observedLine(0)
        h.observedLine(2000)
        h.detector.reset()
        h.clock.advanceTo(3000)
        assertTrue(h.gestures.isEmpty())

        h.duplicatedLine(3100)
        h.duplicatedLine(3200)
        h.clock.advanceTo(4000)
        assertEquals(listOf(Gesture.SINGLE), h.gestures)
    }

    @Test
    fun duplicateCrossingLearningBoundaryDoesNotDispatchAnObservedDown() {
        val h = Harness(enableHold = false)
        h.observedLine(0)
        h.detector.reset()
        h.duplicatedLine(0, deliveredAt = 1000)
        h.duplicatedLine(1100)
        h.clock.advanceTo(2000)
        assertTrue(h.gestures.isEmpty())

        h.duplicatedLine(2100)
        h.duplicatedLine(2200)
        h.clock.advanceTo(3000)
        assertEquals(listOf(Gesture.SINGLE), h.gestures)
    }

    @Test
    fun learningStartedWithKeyDownPreservesPhaseAndCancelsPendingHold() {
        val h = Harness()
        h.duplicatedLine(0)
        h.detector.reset()
        h.observedLine(1000)
        h.observedLine(2000)
        h.clock.advanceTo(4000)
        assertTrue(h.gestures.isEmpty())

        h.detector.reset()
        h.duplicatedLine(4100)
        h.duplicatedLine(5000)
        h.duplicatedLine(5100)
        h.clock.advanceTo(6000)
        assertEquals(listOf(Gesture.SINGLE), h.gestures)
    }

    @Test
    fun learningCancelsPendingSingleAndSilentTrackingSurvivesCancel() {
        val h = Harness()
        h.duplicatedLine(0)
        h.duplicatedLine(100)
        h.detector.reset()
        h.observedLine(200)
        h.clock.advanceTo(2000)
        assertTrue(h.gestures.isEmpty())

        h.detector.reset()
        h.duplicatedLine(2100)
        h.duplicatedLine(3000)
        h.duplicatedLine(3100)
        h.clock.advanceTo(4000)
        assertEquals(listOf(Gesture.SINGLE), h.gestures)
    }

    @Test
    fun silentDownQueuedDuringLearningCanRunAfterFinishBeforeRelease() {
        val h = Harness()
        val queuedDown = Runnable { repeat(2) { h.edges.observe(stamp(2000)) } }
        h.detector.reset()
        h.clock.advanceTo(2000)
        h.detector.reset()
        queuedDown.run()
        h.clock.advanceTo(3000)
        assertTrue(h.gestures.isEmpty())

        h.duplicatedLine(3100)
        h.duplicatedLine(4000)
        h.duplicatedLine(4100)
        h.clock.advanceTo(5000)
        assertEquals(listOf(Gesture.SINGLE), h.gestures)
    }

    @Test
    fun observingEdgesNeverCallsEitherCallbackIncludingAcrossDispatchDuplicates() {
        val callbacks = mutableListOf<String>()
        val edges = OplusKeyEdges({ callbacks += "down" }, { callbacks += "up" })
        repeat(2) { edges.observe(stamp(0)) }
        edges.accept(stamp(0))
        repeat(2) { edges.observe(stamp(100)) }
        edges.accept(stamp(100))
        assertTrue(callbacks.isEmpty())

        edges.accept(stamp(1000))
        edges.accept(stamp(1100))
        assertEquals(listOf("down", "up"), callbacks)
    }

    @Test
    fun knownEdgeSignatureSupportsHoldEvenWithoutRepeats() {
        val result = LearnResult.LogLineFound(
            KeySignature("KEYLOG_OplusKeyEventUtil", "should not notify undefined keys in restrict listen mode", "1000"),
            holdRepeats = false,
            releaseGapMs = DEFAULT_RELEASE_GAP_MS,
        )
        assertTrue(result.canDetectHold)
    }

    @Test
    fun teachingRestoresHeldSnapReleaseWithoutAnActionAndKeepsTimestampDedupe() {
        val tracking = TeachingLogEdges()
        val snap = KeySample.Log("1000", "KEYLOG_OplusKeyEventUtil",
            "should not notify undefined keys in restrict listen mode", "KEYCODE_ACTION_BUTTON_CLICK")
        tracking.observe(stamp(0), snap)
        tracking.observe(stamp(1), snap)
        val h = Harness(enableHold = false)
        tracking.restore("KEYCODE_ACTION_BUTTON_CLICK", h.edges)
        h.duplicatedLine(1)
        h.duplicatedLine(100)
        h.clock.advanceTo(1000)
        assertTrue(h.gestures.isEmpty())
        h.duplicatedLine(2000)
        h.duplicatedLine(2100)
        h.clock.advanceTo(3000)
        assertEquals(listOf(Gesture.SINGLE), h.gestures)
    }

    @Test
    fun heldVolumeKeyDuringTeachingDoesNotChangeFilteredSnapPhaseOnExit() {
        val tracking = TeachingLogEdges()
        val snap = KeySample.Log("system", "KEYLOG_OplusKeyEventUtil",
            "should not notify undefined keys in restrict listen mode", "KEYCODE_ACTION_BUTTON_CLICK")
        tracking.observe(stamp(0), snap)
        tracking.observe(stamp(100), snap)
        tracking.observe(stamp(200), snap.copy(threadKey = "KEYCODE_VOLUME_DOWN"))
        val h = Harness(enableHold = false)
        tracking.restore("KEYCODE_ACTION_BUTTON_CLICK", h.edges)
        h.duplicatedLine(1000)
        h.duplicatedLine(1100)
        h.clock.advanceTo(2000)
        assertEquals(listOf(Gesture.SINGLE), h.gestures)
    }

    @Test
    fun missingContextRetainsRuntimeAcceptanceAndResetDiscardsOldTeachingEdges() {
        val tracking = TeachingLogEdges()
        val sample = KeySample.Log("1000", "KEYLOG_OplusKeyEventUtil",
            "should not notify undefined keys in restrict listen mode", null)
        tracking.observe(stamp(0), sample)
        val h = Harness(enableHold = false)
        tracking.restore("KEYCODE_ACTION_BUTTON_CLICK", h.edges)
        h.duplicatedLine(100)
        h.clock.advanceTo(1000)
        assertTrue(h.gestures.isEmpty())
        tracking.reset()
        tracking.restore("KEYCODE_ACTION_BUTTON_CLICK", h.edges)
        h.duplicatedLine(2000)
        h.duplicatedLine(2100)
        h.clock.advanceTo(3000)
        assertEquals(listOf(Gesture.SINGLE), h.gestures)
    }

    @Test
    fun cancellingDuringHandsOffWithTheOldKeyHeldDoesNotInvertItsNextPress() {
        val h = Harness(enableHold = false)
        h.duplicatedLine(0)
        h.detector.reset()
        val tracking = TeachingLogEdges()
        tracking.begin("KEYCODE_ACTION_BUTTON_CLICK", h.edges)
        h.edges.reset()
        val sample = KeySample.Log("1000", "KEYLOG_OplusKeyEventUtil",
            "should not notify undefined keys in restrict listen mode", "KEYCODE_ACTION_BUTTON_CLICK")
        tracking.observe(stamp(100), sample)
        tracking.restore("KEYCODE_ACTION_BUTTON_CLICK", h.edges)
        h.duplicatedLine(1000)
        h.duplicatedLine(1100)
        h.clock.advanceTo(2000)
        assertEquals(listOf(Gesture.SINGLE), h.gestures)
    }
}
