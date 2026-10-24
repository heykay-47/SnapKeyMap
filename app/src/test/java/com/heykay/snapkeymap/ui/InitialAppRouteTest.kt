package com.heykay.snapkeymap.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialAppRouteTest {
    @Test fun firstPresentationStartsAtIntro() {
        assertEquals("intro", initialAppRoute(introSeen = false))
    }

    @Test fun reopeningAfterPresentationStartsAtHome() {
        assertEquals("home", initialAppRoute(introSeen = true))
    }
}
