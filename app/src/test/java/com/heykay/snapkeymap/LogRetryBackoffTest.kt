package com.heykay.snapkeymap

import org.junit.Assert.assertEquals
import org.junit.Test

class LogRetryBackoffTest {
    @Test
    fun brieflyFullStreamsDoNotResetBackoff() {
        val retry = LogRetryBackoff()
        listOf(1000L, 2000L, 4000L, 8000L).forEachIndexed { index, expected ->
            val start = index * 40_000L
            retry.onAccess(LogAccess.WAITING, start)
            retry.onAccess(LogAccess.FULL, start + 1)
            retry.onAccess(LogAccess.OFF, start + 30_000)
            assertEquals(expected, retry.nextDelayMs())
        }
    }

    @Test
    fun resetsOnlyAfterThirtySecondsContinuouslyFull() {
        val retry = LogRetryBackoff()
        repeat(4) { retry.nextDelayMs() }
        retry.onAccess(LogAccess.FULL, 0)
        retry.onAccess(LogAccess.FULL, 20_000)
        retry.onAccess(LogAccess.OFF, 30_000)
        assertEquals(1000L, retry.nextDelayMs())
    }

    @Test
    fun waitingAndLimitedTimeDoNotCountAsHealthy() {
        val retry = LogRetryBackoff()
        retry.nextDelayMs()
        retry.onAccess(LogAccess.FULL, 0)
        retry.onAccess(LogAccess.LIMITED, 10_000)
        retry.onAccess(LogAccess.WAITING, 60_000)
        retry.onAccess(LogAccess.FULL, 120_000)
        retry.onAccess(LogAccess.OFF, 120_001)
        assertEquals(2000L, retry.nextDelayMs())
    }

    @Test
    fun capsRepeatedFailuresAtOneMinute() {
        val retry = LogRetryBackoff()
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 16_000L, 32_000L, 60_000L, 60_000L),
            List(8) { retry.nextDelayMs() })
    }
}
