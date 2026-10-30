package com.heykay.snapkeymap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeachingSessionTest {
    private val fullAccess = TeachingAccess(accessibility = true, fullLogs = true, inputLive = true)
    private val baseline = 0L..5000L
    private val taps = listOf(6000L..9000L, 10000L..13000L, 14000L..17000L)
    private val hold = 18000L..21500L
    private val positiveTimes = listOf(7000L, 11000L, 15000L, 18200L)
    private val checkStart = 23000L
    private val checkEnd = checkStart + TeachingSession.CHECK_MS + TeachingSession.SETTLE_MS
    private val stylusTag = "StylusModule"
    private val reportedSignature = "StylusModule.onUpdateToolType():# Update tool type = #"
    private val oplusTag = "KEYLOG_OplusKeyEventUtil"
    private val oplusMessage = "should not notify undefined keys in restrict listen mode"
    private val snapCode = "KEYCODE_ACTION_BUTTON_CLICK"
    private val inputCode = "BTN_TRIGGER_HAPPY32"

    @Test
    fun reportedF12PositiveF11NegativeStylusSignatureIsRejectedWithoutSaving() {
        val saved = SavedConfig()
        val old = saved.value
        val session = logged()
        val result = (session.state as TeachingState.Candidate).result as LearnResult.LogLineFound
        assertEquals(KeySignature(stylusTag, reportedSignature, "424242"), result.signature)
        assertFalse(saved.save(session))
        session.beginCheck(checkStart)
        differentAccessibility(session, code = 141)
        session.observe(stylus("9876", "54"), 24500)
        failed(session, TeachingFailure.COLLISION)
        assertFalse(saved.save(session))
        assertEquals(old, saved.value)
        assertEquals(0, saved.writes)
    }

    @Test
    fun livePreciseInputCannotHideABroadLogFallbackDuringValidation() {
        val session = logged(withInput = true)
        assertEquals(InputKey(inputCode), ((session.state as TeachingState.Candidate).result as LearnResult.LogLineFound).inputKey)
        session.beginCheck(checkStart)
        session.observe(input("KEY_F11", true), 24000)
        session.observe(input("KEY_F11", false), 24100)
        session.observe(stylus("9876", "54"), 24500)
        failed(session, TeachingFailure.COLLISION)
    }

    @Test
    fun colorOsVolumeRawDownUpAndFilteredSameThreadOplusEdgesPass() {
        for (volume in listOf("KEYCODE_VOLUME_UP", "KEYCODE_VOLUME_DOWN", "24", "25", "164")) {
            val session = logged(oplus = true)
            session.beginCheck(checkStart)
            val context = LoggedKeyContext()
            val down = context.sample(1, 1, "system", "42", "KEYLOG_PhoneWindowManagerExtImpl", rawKey(volume, true))
            session.observe(down, 24000)
            session.observe(context.sample(1, 1, "1000", "42", oplusTag, oplusMessage), 24001)
            session.observe(context.sample(1, 1, "1000", "42", "KEYLOG_PhoneWindowManagerExtImpl", rawKey(volume, false)), 24100)
            session.observe(context.sample(1, 1, "1000", "42", oplusTag, oplusMessage), 24101)
            session.finishCheck(checkEnd)
            assertTrue("Expected a complete filtered $volume check", session.state is TeachingState.Ready)
            assertNotNull(session.takeSave())
        }
    }

    @Test
    fun equalOrMissingColorOsContextCollidesAndAnotherThreadCannotProvideAFilter() {
        for (contextCode in listOf(snapCode, null)) {
            val session = logged(oplus = true)
            session.beginCheck(checkStart)
            differentAccessibility(session)
            session.observe(KeySample.Log("1000", oplusTag, oplusMessage, contextCode), 24500)
            failed(session, TeachingFailure.COLLISION)
        }
        val session = logged(oplus = true)
        session.beginCheck(checkStart)
        differentAccessibility(session)
        val context = LoggedKeyContext()
        context.sample(1, 1, "1000", "other", "KEYLOG_PhoneWindowManagerExtImpl", rawKey("KEYCODE_VOLUME_DOWN", true))
        session.observe(context.sample(1, 1, "1000", "snap", oplusTag, oplusMessage), 24500)
        failed(session, TeachingFailure.COLLISION)
    }

    @Test
    fun directKnownCodeStillCollidesWithADifferentScanAndUpAloneCollides() {
        for (down in listOf(true, false)) {
            val session = direct()
            session.beginCheck(checkStart)
            differentAccessibility(session)
            session.observe(KeySample.Accessibility(142, 999, down), 24500)
            failed(session, TeachingFailure.COLLISION)
        }
    }

    @Test
    fun unknownCodeRequiresTheSameScanToCollideAndDifferentScanCanWitness() {
        val valid = direct(code = 0, scan = 88)
        valid.beginCheck(checkStart)
        differentAccessibility(valid, code = 0, scan = 99)
        valid.finishCheck(checkEnd)
        assertTrue(valid.state is TeachingState.Ready)
        val collision = direct(code = 0, scan = 88)
        collision.beginCheck(checkStart)
        collision.observe(KeySample.Accessibility(0, 88, false), 24000)
        failed(collision, TeachingFailure.COLLISION)
    }

    @Test
    fun exactInputReleaseCollidesAndDifferentExactInputCanWitnessALoggedCheck() {
        val collision = logged(withInput = true)
        collision.beginCheck(checkStart)
        collision.observe(input(inputCode, false), 24000)
        failed(collision, TeachingFailure.COLLISION)
        val valid = logged(withInput = true)
        valid.beginCheck(checkStart)
        valid.observe(input("KEY_VOLUMEDOWN", true), 24000)
        valid.observe(input("KEY_VOLUMEDOWN", false), 24100)
        valid.finishCheck(checkEnd)
        assertTrue(valid.state is TeachingState.Ready)
    }

    @Test
    fun volumeIsExcludedFromCaptureButReachesValidationAsACompleteWitness() {
        val session = TeachingSession(fullAccess)
        positiveTimes.forEach { session.observe(KeySample.Accessibility(25, 114, true), it) }
        session.capture(baseline, taps, hold)
        failed(session, TeachingFailure.NOT_FOUND)
        val candidate = direct()
        candidate.beginCheck(checkStart)
        differentAccessibility(candidate, code = 25)
        candidate.finishCheck(checkEnd)
        assertTrue(candidate.state is TeachingState.Ready)
    }

    @Test
    fun silenceAmbientLogsReleaseOnlyAndUnreleasedPressCannotPass() {
        val cases: List<(TeachingSession) -> Unit> = listOf(
            {},
            { it.observe(KeySample.Log("2000", "Ambient", "some log", null), 24000) },
            { it.observe(KeySample.Accessibility(25, 114, false), 24000) },
            { it.observe(KeySample.Accessibility(25, 114, true), 24000) },
            {
                it.observe(input("BTN_TOUCH", true), 24000)
                it.observe(input("BTN_TOUCH", false), 24100)
            },
            {
                differentAccessibility(it)
                it.observe(KeySample.Accessibility(24, 115, true), 24500)
            },
            {
                it.observe(KeySample.Accessibility(25, 114, true, repeat = 2), 24000)
                it.observe(KeySample.Accessibility(25, 114, false), 24100)
            },
        )
        cases.forEach { observe ->
            val session = logged()
            session.beginCheck(checkStart)
            observe(session)
            session.finishCheck(checkEnd)
            failed(session, TeachingFailure.NO_DIFFERENT_KEY)
        }
    }

    @Test
    fun directRequiresAccessibilityWitnessNotInputOrRawLogs() {
        val session = direct()
        session.beginCheck(checkStart)
        session.observe(input("KEY_VOLUMEDOWN", true), 24000)
        session.observe(input("KEY_VOLUMEDOWN", false), 24100)
        session.observe(KeySample.Log("1000", "KEYLOG_PhoneWindowManagerExtImpl", rawKey("25", true), "25"), 24200)
        session.observe(KeySample.Log("1000", "KEYLOG_PhoneWindowManagerExtImpl", rawKey("25", false), "25"), 24300)
        session.finishCheck(checkEnd)
        failed(session, TeachingFailure.NO_DIFFERENT_KEY)
    }

    @Test
    fun rawVolumeEvidenceRequiresSystemWriterReleaseAndSameThread() {
        for (differentThread in listOf(false, true)) {
            val session = logged()
            session.beginCheck(checkStart)
            session.observe(KeySample.Log(if (differentThread) "1000" else "2000", "KEYLOG_PhoneWindowManagerExtImpl",
                rawKey("25", true), "25", "one"), 24000)
            session.observe(KeySample.Log(if (differentThread) "1000" else "2000", "KEYLOG_PhoneWindowManagerExtImpl",
                rawKey("25", false), "25", "two"), 24100)
            session.finishCheck(checkEnd)
            failed(session, TeachingFailure.NO_DIFFERENT_KEY)
        }
    }

    @Test
    fun fiveSecondsNeedsSettlingAndLateReleaseCanCompleteButLateDownCannotWitness() {
        val valid = direct()
        valid.beginCheck(checkStart)
        valid.observe(KeySample.Accessibility(25, 114, true), checkStart + 4900)
        valid.finishCheck(checkStart + TeachingSession.CHECK_MS)
        assertTrue(valid.state is TeachingState.Checking)
        valid.observe(KeySample.Accessibility(25, 114, false), checkStart + 5200)
        valid.finishCheck(checkEnd)
        assertTrue(valid.state is TeachingState.Ready)

        val late = direct()
        late.beginCheck(checkStart)
        late.observe(KeySample.Accessibility(25, 114, true), checkStart + 5100)
        late.observe(KeySample.Accessibility(25, 114, false), checkStart + 5200)
        late.finishCheck(checkEnd)
        failed(late, TeachingFailure.NO_DIFFERENT_KEY)

        val lateRelease = direct()
        lateRelease.beginCheck(checkStart)
        lateRelease.observe(KeySample.Accessibility(25, 114, true), checkStart + 4900)
        lateRelease.observe(KeySample.Accessibility(25, 114, false), checkEnd + 100)
        lateRelease.finishCheck(checkEnd + 200)
        failed(lateRelease, TeachingFailure.NO_DIFFERENT_KEY)
    }

    @Test
    fun collisionDuringSettlingOrReadyInvalidatesProofBeforeSave() {
        for (ready in listOf(false, true)) {
            val session = logged()
            session.beginCheck(checkStart)
            differentAccessibility(session)
            if (ready) session.finishCheck(checkEnd)
            session.observe(stylus("9900", "22"), if (ready) checkEnd + 100 else checkStart + 5200)
            failed(session, TeachingFailure.COLLISION)
        }
    }

    @Test
    fun requiredAccessMustBePresentAtStartAndLossCannotHealWithoutRetry() {
        val startCases = listOf(
            direct(access = fullAccess.copy(accessibility = false)),
            direct(withInput = true, access = fullAccess.copy(inputLive = false)),
            logged(access = fullAccess.copy(fullLogs = false)),
            logged(withInput = true, access = fullAccess.copy(inputLive = false)),
        )
        startCases.forEach {
            it.beginCheck(checkStart)
            failed(it, TeachingFailure.ACCESS_LOST)
            it.accessChanged(fullAccess)
            it.beginCheck(checkStart + 1)
            failed(it, TeachingFailure.ACCESS_LOST)
        }
        for (ready in listOf(false, true)) {
            val sessions = listOf(
                direct() to fullAccess.copy(accessibility = false),
                direct(withInput = true) to fullAccess.copy(inputLive = false),
                logged() to fullAccess.copy(fullLogs = false),
                logged(withInput = true) to fullAccess.copy(inputLive = false),
            )
            sessions.forEach { (session, lost) ->
                session.beginCheck(checkStart)
                differentAccessibility(session)
                if (ready) session.finishCheck(checkEnd)
                session.accessChanged(lost)
                session.accessChanged(fullAccess)
                session.finishCheck(checkEnd)
                failed(session, TeachingFailure.ACCESS_LOST)
            }
        }
    }

    @Test
    fun offLimitedAndWaitingLogsAllInvalidateEvenWhenPreciseInputStaysLive() {
        for (logs in listOf(LogAccess.OFF, LogAccess.LIMITED, LogAccess.WAITING)) {
            val session = logged(withInput = true)
            session.beginCheck(checkStart)
            differentAccessibility(session)
            session.finishCheck(checkEnd)
            assertTrue(session.state is TeachingState.Ready)
            session.accessChanged(TeachingAccess(true, logs, true))
            session.accessChanged(TeachingAccess(true, LogAccess.FULL, true))
            failed(session, TeachingFailure.ACCESS_LOST)
        }
    }

    @Test
    fun onlyRequiredSourcesAreMandatoryAndSourceReplacementInvalidatesUntilRetry() {
        val direct = direct(access = TeachingAccess(true, false, false))
        direct.beginCheck(checkStart)
        differentAccessibility(direct)
        direct.finishCheck(checkEnd)
        assertTrue(direct.state is TeachingState.Ready)

        val logged = logged(access = TeachingAccess(false, true, false))
        logged.beginCheck(checkStart)
        logged.observe(KeySample.Log("1000", "KEYLOG_PhoneWindowManagerExtImpl", rawKey("25", true), "25"), 24000)
        logged.observe(KeySample.Log("1000", "KEYLOG_PhoneWindowManagerExtImpl", rawKey("25", false), "25"), 24100)
        logged.finishCheck(checkEnd)
        assertTrue(logged.state is TeachingState.Ready)

        for (ready in listOf(false, true)) {
            val session = logged(withInput = true)
            session.beginCheck(checkStart)
            differentAccessibility(session)
            if (ready) session.finishCheck(checkEnd)
            session.sourceRestarted(logs = true)
            session.accessChanged(fullAccess)
            failed(session, TeachingFailure.ACCESS_LOST)
            val inputRestart = logged(withInput = true)
            inputRestart.beginCheck(checkStart)
            inputRestart.sourceRestarted(logs = false, input = true)
            failed(inputRestart, TeachingFailure.ACCESS_LOST)
        }
    }

    @Test
    fun captureCandidateCheckingFailureAndCancelNeverWriteAndClosedCannotRevive() {
        val saved = SavedConfig()
        val old = saved.value
        val capturing = TeachingSession(fullAccess)
        assertFalse(saved.save(capturing))
        capturing.cancel()
        capturing.capture(baseline, taps, hold)
        capturing.beginCheck(checkStart)
        assertEquals(TeachingState.Closed, capturing.state)
        assertFalse(saved.save(capturing))
        val candidate = direct()
        assertFalse(saved.save(candidate))
        candidate.beginCheck(checkStart)
        assertFalse(saved.save(candidate))
        candidate.cancel()
        differentAccessibility(candidate)
        candidate.finishCheck(checkEnd)
        assertFalse(saved.save(candidate))
        val failed = direct()
        failed.beginCheck(checkStart)
        failed.finishCheck(checkEnd)
        assertFalse(saved.save(failed))
        assertEquals(old, saved.value)
        assertEquals(0, saved.writes)
    }

    @Test
    fun staleDeliveryCannotSupplyReleaseForRetryOrWriteAfterCancel() {
        val gate = TeachingDelivery()
        val saved = SavedConfig()
        gate.advance(TeachingHandle(1))
        val first = direct()
        first.beginCheck(checkStart)
        val oldStamp = gate.stamp
        first.cancel()
        gate.advance(TeachingHandle(2))
        val retry = direct()
        retry.beginCheck(checkStart)
        retry.observe(KeySample.Accessibility(25, 114, true), 24000)
        gate.deliver(oldStamp) { retry.observe(KeySample.Accessibility(25, 114, false), 24100) }
        retry.finishCheck(checkEnd)
        failed(retry, TeachingFailure.NO_DIFFERENT_KEY)
        assertFalse(saved.save(first))
        assertFalse(saved.save(retry))
        assertEquals(0, saved.writes)
    }

    @Test
    fun explicitSaveWritesOnceAndAppliesOnlyLearnedFieldsToCurrentConfig() {
        val saved = SavedConfig()
        val session = direct()
        session.beginCheck(checkStart)
        differentAccessibility(session)
        session.finishCheck(checkEnd)
        assertEquals(0, saved.writes)
        val current = saved.value.copy(enabled = false, theme = ThemeMode.DARK, pausedApps = setOf("current.app"),
            holdMs = 900, actions = mapOf(Gesture.SINGLE to SnapAction.NoAction))
        saved.value = current
        assertTrue(saved.save(session))
        assertEquals(current.copy(keyEvent = LearnedKey(142, 88), logSignature = null, logKeyCode = null, inputKey = null), saved.value)
        assertFalse(saved.save(session))
        session.cancel()
        assertFalse(saved.save(session))
        assertEquals(1, saved.writes)
    }

    @Test
    fun loggedSaveRetainsCalibrationAndClearsOldDirectBinding() {
        val session = logged(oplus = true, withInput = true)
        val learned = (session.state as TeachingState.Candidate).result as LearnResult.LogLineFound
        session.beginCheck(checkStart)
        differentAccessibility(session)
        session.finishCheck(checkEnd)
        val patch = session.takeSave()!!
        val current = Config(keyEvent = LearnedKey(141, 87), theme = ThemeMode.DARK, releaseGapMs = 400)
        val changed = patch.applyTo(current)
        assertNull(changed.keyEvent)
        assertEquals(learned.signature, changed.logSignature)
        assertEquals(learned.logKeyCode, changed.logKeyCode)
        assertEquals(learned.inputKey, changed.inputKey)
        assertEquals(learned.holdRepeats, changed.holdRepeats)
        assertEquals(learned.releaseGapMs, changed.releaseGapMs)
        assertEquals(ThemeMode.DARK, changed.theme)
        assertTrue(runCatching { patch.applyTo(current) }.isFailure)
        assertNull(session.takeSave())
    }

    private fun direct(
        code: Int = 142, scan: Int = 88, withInput: Boolean = false, access: TeachingAccess = fullAccess,
    ): TeachingSession =
        TeachingSession(access).apply {
            positiveTimes.forEach {
                observe(KeySample.Accessibility(code, scan, true), it)
                if (withInput) observe(input(inputCode, true), it)
            }
            capture(baseline, taps, hold)
            assertEquals(LearnResult.KeyEventFound(LearnedKey(code, scan), if (withInput) InputKey(inputCode) else null),
                (state as TeachingState.Candidate).result)
        }

    private fun logged(oplus: Boolean = false, withInput: Boolean = false, access: TeachingAccess = fullAccess): TeachingSession =
        TeachingSession(access).apply {
            positiveTimes.forEachIndexed { index, time ->
                if (oplus) {
                    observe(KeySample.Log("1000", "KEYLOG_PhoneWindowManagerExtImpl", rawKey(snapCode, true), snapCode), time)
                    observe(KeySample.Log("1000", oplusTag, oplusMessage, snapCode), time + 1)
                } else observe(stylus("${900 + index}", "${10 + index}"), time)
                if (withInput) observe(input(inputCode, true), time)
            }
            capture(baseline, taps, hold)
            assertTrue(state is TeachingState.Candidate)
            assertTrue((state as TeachingState.Candidate).result is LearnResult.LogLineFound)
        }

    private fun differentAccessibility(session: TeachingSession, code: Int = 25, scan: Int = 114) {
        session.observe(KeySample.Accessibility(code, scan, true), 24000)
        session.observe(KeySample.Accessibility(code, scan, false), 24100)
    }

    private fun stylus(counter: String, type: String) = KeySample.Log("424242", stylusTag,
        "StylusModule.onUpdateToolType():$counter Update tool type = $type", null)

    private fun rawKey(code: String, down: Boolean) =
        "overrideInterceptKeyBeforeQueueing:KeyEvent { action=${if (down) "ACTION_DOWN" else "ACTION_UP"}, keyCode=$code, scanCode=0 }"

    private fun input(code: String, down: Boolean) = KeySample.Input(InputKeyEvent(InputKey(code), down))

    private fun failed(session: TeachingSession, reason: TeachingFailure) {
        assertEquals(TeachingState.Failed(reason), session.state)
        assertNull(session.takeSave())
    }

    private class SavedConfig {
        var value = Config(keyEvent = LearnedKey(141, 87), theme = ThemeMode.LIGHT)
        var writes = 0
        fun save(session: TeachingSession): Boolean {
            val patch = session.takeSave() ?: return false
            value = patch.applyTo(value)
            writes++
            return true
        }
    }
}
