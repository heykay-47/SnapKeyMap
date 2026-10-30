package com.heykay.snapkeymap

import org.junit.Assert.assertEquals
import org.junit.Test

class TeachingDeliveryTest {
    @Test
    fun queuedRuntimeCallbacksCannotCrossEntryAndExitEvenWhenBothRecipientsAreRuntime() {
        val gate = TeachingDelivery()
        var actions = 0
        val before = gate.stamp
        gate.advance(TeachingHandle(1))
        gate.deliver(before) { actions++ }
        gate.advance(null)
        gate.deliver(before) { actions++ }
        assertEquals(0, actions)
        gate.deliver(gate.stamp) { actions++ }
        assertEquals(1, actions)
    }

    @Test
    fun cancelledTeachingCallbackNeverFallsThroughToRuntimeAndRetryCannotInheritIt() {
        val gate = TeachingDelivery()
        gate.advance(TeachingHandle(1))
        val oldLog = gate.stamp
        val oldInput = gate.stamp
        var teachingSamples = 0
        var runtimeActions = 0
        fun deliver(receipt: TeachingDelivery.Stamp) = gate.deliver(receipt) {
            if (gate.stamp.handle == null) runtimeActions++ else teachingSamples++
        }
        gate.advance(null)
        deliver(oldLog)
        deliver(oldInput)
        gate.advance(TeachingHandle(2))
        deliver(oldLog)
        deliver(oldInput)
        assertEquals(0, runtimeActions)
        assertEquals(0, teachingSamples)
        deliver(gate.stamp)
        assertEquals(1, teachingSamples)
    }

    @Test
    fun phaseBoundaryRejectsLateCaptureButSettlingAndReadyKeepCheckingTheSameReceipt() {
        val gate = TeachingDelivery()
        val owner = TeachingHandle(7)
        gate.advance(owner)
        val capture = gate.stamp
        gate.advance(owner)
        val candidate = gate.stamp
        gate.advance(owner)
        val checking = gate.stamp
        var samples = 0
        gate.deliver(capture) { samples++ }
        gate.deliver(candidate) { samples++ }
        gate.deliver(checking) { samples++ }
        assertEquals(1, samples)
        gate.deliver(checking) { samples++ }
        assertEquals(2, samples)
    }
}
