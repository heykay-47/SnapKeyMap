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
    fun resetAllowsFirstEventOfANewSession() {
        val edgesSeen = mutableListOf<String>()
        val edges = OplusKeyEdges({ edgesSeen += "down" }, { edgesSeen += "up" })
        edges.accept("first")
        edges.reset()
        edges.accept("first")
        assertEquals(listOf("down", "down"), edgesSeen)
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
