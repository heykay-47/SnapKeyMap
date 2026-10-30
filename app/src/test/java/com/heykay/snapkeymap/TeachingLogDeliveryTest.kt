package com.heykay.snapkeymap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeachingLogDeliveryTest {
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

    private class Harness(enableLong: Boolean = false) {
        val clock = Clock()
        val delivery = TeachingDelivery()
        val tracking = TeachingLogEdges()
        val context = LoggedKeyContext()
        var config = Config(logSignature = KeySignature(TAG, MESSAGE, "1000"), logKeyCode = "KEYCODE_F12").let {
            if (enableLong) it.copy(actions = it.actions + (Gesture.LONG to SnapAction.Device(DeviceAction.FLASHLIGHT))) else it
        }
        var session: TeachingSession? = null
        var deliveredSamples = 0
        var seenEdges = 0
        val history = mutableListOf<Pair<Gesture, Long>>()
        val actions = mutableListOf<SnapAction>()
        val detector = GestureDetector(clock::schedule, clock::cancel, { 650L }, {
            history += it to clock.now
            actions += config.action(it)
        }).apply { wantsLong = config.action(Gesture.LONG) != SnapAction.NoAction }
        val edges = OplusKeyEdges(
            { seenEdges++; detector.down() },
            { seenEdges++; detector.up() },
        )
        private var nextHandle = 0L

        fun begin() {
            detector.reset()
            tracking.begin(config.logKeyCode, edges)
            edges.reset()
            session = TeachingSession(TeachingAccess(false, true, false))
            delivery.advance(TeachingHandle(++nextHandle))
        }

        fun handsOff() {
            delivery.discardPending()
            edges.reset()
            tracking.reset()
        }

        fun queue(time: Long, sample: KeySample.Log): () -> Unit {
            clock.advanceTo(time)
            val receipt = delivery.stamp
            return {
                tracking.deliver(delivery, receipt, timestamp(time), sample, config.keyBinding(), edges) {
                    deliveredSamples++
                    val current = session
                    if (current != null) current.observe(sample, time)
                    else if (KeyMatching.matches(config.keyBinding(), sample) &&
                        isKnownOplusKeyLine(sample.uid, sample.tag, sample.message)) edges.accept(timestamp(time))
                }
            }
        }

        fun line(time: Long, tid: String, tag: String, message: String) {
            queue(time, context.sample(1, delivery.stamp.epoch, "1000", tid, tag, message))()
        }

        fun raw(time: Long, code: String, down: Boolean) = line(time, "raw", RAW_TAG, rawMessage(code, down))
        fun edge(time: Long) = line(time, "edge", TAG, MESSAGE)

        fun candidateHeld() {
            begin()
            handsOff()
            for (time in POSITIVES) {
                raw(time, SNAP, true)
                edge(time + 1)
                edge(time + 2)
                if (time != POSITIVES.last()) {
                    raw(time + 100, SNAP, false)
                    edge(time + 101)
                    edge(time + 102)
                }
            }
            session!!.capture(BASELINE, TAPS, HOLD)
            delivery.advance(delivery.stamp.handle)
            val candidate = (session!!.state as TeachingState.Candidate).result as LearnResult.LogLineFound
            assertEquals(SNAP, candidate.logKeyCode)
            assertNull(candidate.inputKey)
        }

        fun beginCheck(time: Long) {
            delivery.advance(delivery.stamp.handle)
            session!!.beginCheck(time)
        }

        fun readyHeld() {
            candidateHeld()
            beginCheck(23000)
            raw(24000, VOLUME, true)
            line(24001, "raw", TAG, MESSAGE)
            raw(24100, VOLUME, false)
            line(24101, "raw", TAG, MESSAGE)
            clock.advanceTo(CHECK_END)
            session!!.finishCheck(CHECK_END)
            assertTrue(session!!.state is TeachingState.Ready)
            assertTrue(history.isEmpty())
        }

        fun exit() {
            detector.reset()
            session = null
            delivery.advance(null)
            tracking.restore(config.logKeyCode, edges)
            tracking.reset()
        }

        fun cancel() {
            session!!.cancel()
            exit()
        }
    }

    @Test
    fun teachingUpQueuedBehindConfirmedSaveOrCancelLeavesTheNextPressAndHoldTimingsIntact() {
        for (save in listOf(false, true)) for (enableLong in listOf(false, true)) {
            val h = Harness(enableLong)
            h.readyHeld()
            val old = h.config
            val closed = h.session!!
            lateinit var queuedUp: () -> Unit
            if (save) {
                assertTrue(closed.save { patch ->
                    queuedUp = h.queue(29000, snap())
                    h.config = patch.applyTo(h.config)
                    true
                })
                h.exit()
                assertEquals(SNAP, h.config.logKeyCode)
            } else {
                queuedUp = h.queue(29000, snap())
                h.cancel()
                assertEquals(old, h.config)
            }
            val delivered = h.deliveredSamples
            h.clock.advanceTo(30000)
            queuedUp()
            queuedUp()
            assertEquals(delivered, h.deliveredSamples)
            assertEquals(TeachingState.Closed, closed.state)
            assertEquals(0, h.seenEdges)
            assertTrue(h.history.isEmpty())
            assertTrue(h.actions.isEmpty())

            h.edge(31000)
            assertEquals(if (enableLong) emptyList() else listOf(Gesture.SINGLE to 31000L), h.history)
            h.edge(31100)
            val firstAt = if (enableLong) 31100L else 31000L
            h.clock.advanceTo(32000)
            assertEquals(listOf(Gesture.SINGLE to firstAt), h.history)
            assertEquals(listOf(SnapAction.Device(DeviceAction.FLASHLIGHT)), h.actions)
            if (enableLong) {
                h.edge(33000)
                h.clock.advanceTo(33649)
                assertEquals(1, h.history.size)
                h.clock.advanceTo(33650)
                assertEquals(listOf(Gesture.SINGLE to firstAt, Gesture.LONG to 33650L), h.history)
                h.edge(34000)
                h.clock.advanceTo(35000)
                assertEquals(2, h.history.size)
            }
        }
    }

    @Test
    fun staleRuntimeObservationUsesTheWholeBindingAndCannotSeedSnapFromIdentifiedVolume() {
        val rejected = listOf(
            snap().copy(threadKey = VOLUME),
            snap().copy(uid = "2000"),
            snap().copy(tag = "Other_OplusKeyEventUtil"),
            snap().copy(message = "another restrict listen mode message"),
        )
        for (sample in rejected) {
            val h = Harness()
            h.begin()
            h.handsOff()
            val queued = h.queue(100, sample)
            h.cancel()
            queued()
            assertEquals(0, h.seenEdges)
            assertTrue(h.history.isEmpty())
            h.edge(1000)
            assertEquals(listOf(Gesture.SINGLE to 1000L), h.history)
            h.edge(1100)
            h.clock.advanceTo(2000)
            assertEquals(1, h.history.size)
        }
    }

    @Test
    fun staleReleaseDuringAnotherAttemptUpdatesOnlyItsPhysicalPhase() {
        val h = Harness()
        h.readyHeld()
        val release = h.queue(29000, snap())
        h.cancel()
        h.begin()
        val retry = h.session!!
        val delivered = h.deliveredSamples
        release()
        assertEquals(delivered, h.deliveredSamples)
        assertEquals(TeachingState.Capturing, retry.state)
        retry.capture(BASELINE, TAPS, HOLD)
        assertEquals(TeachingState.Failed(TeachingFailure.NOT_FOUND), retry.state)
        assertFalse(retry.save { error("Retry must not inherit proof") })
        h.cancel()
        h.edge(31000)
        assertEquals(listOf(Gesture.SINGLE to 31000L), h.history)
        h.edge(31100)
        h.clock.advanceTo(32000)
        assertEquals(1, h.history.size)
    }

    @Test
    fun staleCaptureCannotTeachTheRetryAndStaleCheckSamplesCannotWitnessOrCollide() {
        val h = Harness()
        h.begin()
        val queued = POSITIVES.map { h.queue(it, snap()) }
        h.cancel()
        h.begin()
        val delivered = h.deliveredSamples
        queued.forEach { it() }
        h.session!!.capture(BASELINE, TAPS, HOLD)
        assertEquals(TeachingState.Failed(TeachingFailure.NOT_FOUND), h.session!!.state)
        assertEquals(delivered, h.deliveredSamples)

        val checking = Harness()
        checking.candidateHeld()
        val volumeDown = checking.queue(24000, KeySample.Log("1000", RAW_TAG, rawMessage(VOLUME, true), VOLUME, "raw"))
        val volumeUp = checking.queue(24000, KeySample.Log("1000", RAW_TAG, rawMessage(VOLUME, false), VOLUME, "raw"))
        val collision = checking.queue(24000, snap())
        checking.beginCheck(24000)
        val samples = checking.deliveredSamples
        volumeDown()
        volumeUp()
        collision()
        assertEquals(samples, checking.deliveredSamples)
        assertTrue(checking.session!!.state is TeachingState.Checking)
        checking.session!!.finishCheck(24000 + TeachingSession.CHECK_MS + TeachingSession.SETTLE_MS)
        assertEquals(TeachingState.Failed(TeachingFailure.NO_DIFFERENT_KEY), checking.session!!.state)
        assertTrue(checking.history.isEmpty())
        assertTrue(checking.actions.isEmpty())
        checking.cancel()
        checking.edge(31000)
        checking.edge(31100)
        checking.clock.advanceTo(32000)
        assertEquals(listOf(Gesture.SINGLE to 31000L), checking.history)
    }

    @Test
    fun preHandsOffDownOrUpCannotInvertTheAuthoritativeReleasedBaseline() {
        for (up in listOf(false, true)) {
            val h = Harness(enableLong = true)
            h.begin()
            if (up) h.edge(0)
            val oldEdge = h.queue(100, snap())
            h.handsOff()
            val delivered = h.deliveredSamples
            oldEdge()
            assertEquals(delivered, h.deliveredSamples)
            h.cancel()
            h.edge(1000)
            h.edge(1100)
            h.clock.advanceTo(2000)
            assertEquals(listOf(Gesture.SINGLE to 1100L), h.history)
        }
    }

    companion object {
        private const val TAG = "KEYLOG_OplusKeyEventUtil"
        private const val MESSAGE = "should not notify undefined keys in restrict listen mode"
        private const val RAW_TAG = "KEYLOG_PhoneWindowManagerExtImpl"
        private const val SNAP = "KEYCODE_ACTION_BUTTON_CLICK"
        private const val VOLUME = "KEYCODE_VOLUME_DOWN"
        private val BASELINE = 0L..5000L
        private val TAPS = listOf(6000L..9000L, 10000L..13000L, 14000L..17000L)
        private val HOLD = 18000L..21500L
        private val POSITIVES = listOf(7000L, 11000L, 15000L, 18200L)
        private const val CHECK_END = 23000 + TeachingSession.CHECK_MS + TeachingSession.SETTLE_MS

        private fun snap() = KeySample.Log("1000", TAG, MESSAGE, null)
        private fun rawMessage(code: String, down: Boolean) =
            "overrideInterceptKeyBeforeQueueing:KeyEvent { action=${if (down) "ACTION_DOWN" else "ACTION_UP"}, keyCode=$code, scanCode=0 }"
        private fun timestamp(time: Long) = "10-18 11:00:%02d.%03d".format(time / 1000, time % 1000)
    }
}
