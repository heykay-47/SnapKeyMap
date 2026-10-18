package com.heykay.snapkeymap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        val detector = GestureDetector(clock::schedule, clock::cancel, 650, gestures::add).apply {
            wantsDouble = true
            wantsLong = enableHold
        }
        val edges = OplusKeyEdges(detector::down, detector::up)

        fun duplicatedLine(time: Long, deliveredAt: Long = time) {
            clock.advanceTo(deliveredAt)
            repeat(2) { edges.accept("10-18 11:05:$time") }
        }

        fun observedLine(time: Long) {
            clock.advanceTo(time)
            repeat(2) { edges.observe("10-18 11:05:$time") }
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
    fun resetAllowsFirstEventOfANewSession() {
        val edgesSeen = mutableListOf<String>()
        val edges = OplusKeyEdges({ edgesSeen += "down" }, { edgesSeen += "up" })
        edges.accept("first")
        edges.reset()
        edges.accept("first")
        assertEquals(listOf("down", "down"), edgesSeen)
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
        val queuedDown = Runnable { repeat(2) { h.edges.observe("learning-down") } }
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
        repeat(2) { edges.observe("down") }
        edges.accept("down")
        repeat(2) { edges.observe("up") }
        edges.accept("up")
        assertTrue(callbacks.isEmpty())

        edges.accept("next-down")
        edges.accept("next-up")
        assertEquals(listOf("down", "up"), callbacks)
    }

    @Test
    fun knownEdgeSignatureSupportsHoldEvenWithoutRepeats() {
        val result = LearnResult.LogLineFound(
            KeySignature("KEYLOG_OplusKeyEventUtil", "should not notify undefined keys in restrict listen mode"),
            holdRepeats = false,
            releaseGapMs = DEFAULT_RELEASE_GAP_MS,
        )
        assertTrue(result.canDetectHold)
    }
}
