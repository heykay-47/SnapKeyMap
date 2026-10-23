package com.heykay.snapkeymap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyLearningReplayTest {
    private val oplusTag = "KEYLOG_OplusKeyEventUtil"
    private val oplusMessage = "should not notify undefined keys in restrict listen mode"
    private val combinationTag = "KEYLOG_KeyCombinationManager"
    private val baseline = 0L..5000L
    private val taps = listOf(6000L..9000L, 10000L..13000L, 14000L..17000L)
    private val hold = 18000L..21500L

    private fun combinationMessage(down: Boolean, downTime: Long, eventTime: Long) =
        "down $down downTime $downTime action ${if (down) 0 else 1} count 0 " +
            "isDefaultDisplayInteractive true eventTime $eventTime mDownTimes: " +
            if (down) "{}" else "{780=$downTime}"

    private fun press(down: Long, up: Long) = listOf(
        Observation(down, KeyLearning.logKey("1000", combinationTag, combinationMessage(true, down, down))),
        Observation(up, KeyLearning.logKey("1000", combinationTag, combinationMessage(false, down, up))),
    ) + listOf(down + 1, down + 1, up + 1, up + 1).map {
        Observation(it, KeyLearning.logKey("1000", oplusTag, oplusMessage))
    }

    private fun observations() = listOf(7000L, 11000L, 15000L).flatMap { press(it, it + 100) } +
        press(18200L, 22000L)

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

    @Test
    fun otherThreadsKeyboardEdgesDoNotChangeSnapKeyGesturePhase() {
        val snapCode = "KEYCODE_ACTION_BUTTON_CLICK"
        val samples = listOf(7000L, 11000L, 15000L).map {
            Observation(it, KeyLearning.loggedKey(snapCode))
        } + Observation(1000L, KeyLearning.loggedKey("KEYCODE_A"))
        val result = KeyLearning.analyze(observations() + samples, baseline, taps, hold) as LearnResult.LogLineFound
        assertEquals(snapCode, result.logKeyCode)
        val clock = Clock()
        val gestures = mutableListOf<Gesture>()
        val detector = GestureDetector(clock::schedule, clock::cancel, { 650L }, gestures::add).apply {
            wantsDouble = true
            wantsLong = true
        }
        val edges = OplusKeyEdges(detector::down, detector::up)
        val keysByThread = mutableMapOf<String, String>()
        fun key(tid: String, code: String) {
            val parsed = loggedKeyCode("1000", "KEYLOG_PhoneWindowManagerExtImpl",
                "overrideInterceptKeyBeforeQueueing:KeyEvent { action=ACTION_DOWN, keyCode=$code, scanCode=0 }")
            if (parsed != null) keysByThread[tid] = parsed
        }
        fun edge(time: Long, tid: String) {
            clock.advanceTo(time)
            if (!acceptsKeyLog(result.signature, "1000", oplusTag, oplusMessage) ||
                !acceptsLoggedKey(result.logKeyCode, keysByThread[tid])) return
            repeat(3) { edges.accept("10-23 20:15:%02d.%03d".format(time / 1000, time % 1000)) }
        }

        key("2666", snapCode)
        key("2607", "KEYCODE_A")
        edge(0, "2666")
        edge(40, "2607")
        edge(100, "2666")
        edge(140, "2607")
        edge(200, "2666")
        edge(240, "2607")
        edge(300, "2666")
        edge(340, "2607")
        clock.advanceTo(1000)
        key("2666", "KEYCODE_A")
        edge(1100, "2666")
        edge(1200, "2666")
        key("2666", snapCode)
        edge(2000, "2666")
        edge(2100, "2607")
        edge(2200, "2607")
        edge(2800, "2666")
        clock.advanceTo(4000)

        assertEquals(listOf(Gesture.DOUBLE, Gesture.LONG), gestures)
    }

    @Test
    fun learnedSourceRoutesRecordedDoubleTapWithoutSingleOrHold() {
        val result = KeyLearning.analyze(observations(), baseline, taps, hold) as LearnResult.LogLineFound
        val clock = Clock()
        val gestures = mutableListOf<Gesture>()
        val detector = GestureDetector(clock::schedule, clock::cancel, { 650L }, gestures::add).apply {
            wantsDouble = true
            wantsLong = result.canDetectHold
        }
        val edges = OplusKeyEdges(detector::down, detector::up)
        var knownAccepted = 0
        var genericAccepted = 0

        fun line(time: Long, timestamp: String, tag: String, message: String) {
            clock.advanceTo(time)
            // These recorded system lines predate capturing logcat's UID column.
            if (!acceptsKeyLog(result.signature, "1000", tag, message)) return
            if (isKnownOplusKeyLine("1000", tag, message)) {
                knownAccepted++
                edges.accept(timestamp)
            } else {
                genericAccepted++
            }
        }

        line(0, "10-18 12:07:40.755", combinationTag, combinationMessage(true, 42401336, 42401336))
        repeat(2) { line(1, "10-18 12:07:40.756", oplusTag, oplusMessage) }
        line(90, "10-18 12:07:40.845", combinationTag, combinationMessage(false, 42401336, 42401430))
        repeat(2) { line(91, "10-18 12:07:40.846", oplusTag, oplusMessage) }
        line(152, "10-18 12:07:40.907", combinationTag, combinationMessage(true, 42401492, 42401492))
        repeat(2) { line(152, "10-18 12:07:40.907", oplusTag, oplusMessage) }
        line(262, "10-18 12:07:41.017", combinationTag, combinationMessage(false, 42401492, 42401601))
        repeat(2) { line(262, "10-18 12:07:41.017", oplusTag, oplusMessage) }
        clock.advanceTo(5000)

        assertEquals(8, knownAccepted)
        assertEquals(0, genericAccepted)
        assertEquals(listOf(Gesture.DOUBLE), gestures)
    }

    @Test
    fun knownLogsMustStillCoverEnoughTapWindows() {
        val generic = KeyLearning.logKey("1000", "OtherKey", "pressed")
        val observations = listOf(7000L, 11000L, 15000L).map { Observation(it, generic) } +
            press(7000L, 7100L)

        val result = KeyLearning.analyze(observations, baseline, taps, hold) as LearnResult.LogLineFound

        assertEquals(KeySignature("OtherKey", "pressed", "1000"), result.signature)
        assertFalse(result.holdRepeats)
        assertEquals(DEFAULT_RELEASE_GAP_MS, result.releaseGapMs)
    }

    @Test
    fun learnedInputReplaysExplicitHoldAndDoubleWithoutTogglingPhase() {
        val inputKey = KeyLearning.inputKey("BTN_TRIGGER_HAPPY32")
        val samples = listOf(7000L, 11000L, 15000L, 18200L).map { Observation(it, inputKey) }
        val result = KeyLearning.analyze(observations() + samples, baseline, taps, hold) as LearnResult.LogLineFound
        assertEquals(InputKey("BTN_TRIGGER_HAPPY32"), result.inputKey)
        assertEquals(oplusTag, result.signature.tag)

        val clock = Clock()
        val gestures = mutableListOf<Gesture>()
        val detector = GestureDetector(clock::schedule, clock::cancel, { 650L }, gestures::add).apply {
            wantsDouble = true
            wantsLong = true
        }
        fun line(time: Long, code: String, value: String) {
            clock.advanceTo(time)
            val event = parseInputKeyEvent("[ 42.000000] /dev/input/event0: EV_KEY $code $value") ?: return
            if (event.key != result.inputKey) return
            if (event.down) detector.down() else detector.up()
        }
        line(0, "BTN_TRIGGER_HAPPY32", "DOWN")
        line(10, "BTN_TRIGGER_HAPPY32", "DOWN")
        line(500, "BTN_TRIGGER_HAPPY32", "REPEAT")
        line(800, "KEY_VOLUMEUP", "DOWN")
        line(1000, "BTN_TRIGGER_HAPPY32", "UP")
        line(2000, "BTN_TRIGGER_HAPPY32", "DOWN")
        line(2100, "BTN_TRIGGER_HAPPY32", "UP")
        line(2200, "BTN_TRIGGER_HAPPY32", "DOWN")
        line(2300, "BTN_TRIGGER_HAPPY32", "UP")
        clock.advanceTo(5000)
        assertEquals(listOf(Gesture.LONG, Gesture.DOUBLE), gestures)
    }

    @Test
    fun learnedFilterRemainsExactAndDoesNotOverrideGenericSignatures() {
        val generic = KeySignature("OtherKey", "pressed #", "1000")

        assertTrue(acceptsKeyLog(null, "1000", oplusTag, oplusMessage))
        assertFalse(acceptsKeyLog(null, "1000", "OtherKey", "pressed 1"))
        assertTrue(acceptsKeyLog(generic, "1000", "OtherKey", "pressed 1"))
        assertFalse(acceptsKeyLog(generic, "1000", oplusTag, oplusMessage))
        assertFalse(acceptsKeyLog(generic, "1000", "OtherKey", "released 1"))
    }
}
