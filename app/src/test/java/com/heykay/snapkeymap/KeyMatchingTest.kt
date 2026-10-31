package com.heykay.snapkeymap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyMatchingTest {
    private val tag = "KEYLOG_OplusKeyEventUtil"
    private val message = "should not notify undefined keys in restrict listen mode"
    private val snap = "KEYCODE_ACTION_BUTTON_CLICK"
    private val oplus = KeyBinding.Logged(KeySignature(tag, message, "1000"), snap, InputKey("BTN_TRIGGER_HAPPY32"))

    @Test
    fun knownAndroidKeyUsesCodeAndUnknownUsesCodeAndScan() {
        assertTrue(KeyMatching.matches(KeyBinding.Direct(LearnedKey(142, 88)), key(142, 99)))
        assertFalse(KeyMatching.matches(KeyBinding.Direct(LearnedKey(142, 88)), key(141, 88)))
        assertTrue(KeyMatching.matches(KeyBinding.Direct(LearnedKey(0, 88)), key(0, 88)))
        assertFalse(KeyMatching.matches(KeyBinding.Direct(LearnedKey(0, 88)), key(0, 99)))
        assertFalse(KeyMatching.matches(KeyBinding.Direct(LearnedKey(0, 88)), key(142, 88)))
    }

    @Test
    fun inputIdentityIsExactAndIsNotAnOperationalRouteForDirectKeys() {
        assertTrue(KeyMatching.matches(oplus, input("BTN_TRIGGER_HAPPY32")))
        assertFalse(KeyMatching.matches(oplus, input("BTN_TRIGGER_HAPPY3")))
        assertFalse(KeyMatching.matches(oplus.copy(inputKey = null), input("BTN_TRIGGER_HAPPY32")))
        assertFalse(KeyMatching.matches(KeyBinding.Direct(LearnedKey(142, 88)), input("BTN_TRIGGER_HAPPY32")))
        assertFalse(KeyMatching.matches(oplus, key(142, 88)))
    }

    @Test
    fun logIdentityPreservesUidTagNormalizationAndUsableSignatureRules() {
        val signature = KeySignature("StylusModule", "StylusModule.onUpdateToolType():# Update tool type = #", "system")
        val binding = KeyBinding.Logged(signature, null, null)
        val sample = KeySample.Log("1000", "StylusModule", "  StylusModule.onUpdateToolType():9876 Update tool type = 54  ", null)
        assertTrue(KeyMatching.matches(binding, sample))
        assertFalse(KeyMatching.matches(binding, sample.copy(uid = "2000")))
        assertFalse(KeyMatching.matches(binding, sample.copy(tag = "OtherStylusModule")))
        assertFalse(KeyMatching.matches(binding, sample.copy(message = "other 9876")))
        assertFalse(KeyMatching.matches(binding.copy(signature = signature.copy(uid = null)), sample))
        val longMessage = "x".repeat(170)
        assertTrue(KeyMatching.matches(binding.copy(signature = KeySignature("Long", normalizeLogMessage(longMessage), "1000")),
            sample.copy(tag = "Long", message = longMessage + "suffix")))
    }

    @Test
    fun oplusFilteringUsesSameThreadContextAndAcceptsMissingContextLikeRuntime() {
        val context = LoggedKeyContext()
        fun raw(thread: String, code: String) = context.sample(1, 1, "system", thread, "KEYLOG_PhoneWindowManagerExtImpl",
            "overrideInterceptKeyBeforeQueueing:KeyEvent { action=ACTION_DOWN, keyCode=$code, scanCode=0 }")
        fun edge(thread: String) = context.sample(1, 1, "1000", thread, tag, message)
        raw("snap", snap)
        raw("volume", "KEYCODE_VOLUME_DOWN")
        assertTrue(KeyMatching.matches(oplus, edge("snap")))
        assertFalse(KeyMatching.matches(oplus, edge("volume")))
        assertTrue(KeyMatching.matches(oplus, edge("missing")))
        raw("snap", "KEYCODE_VOLUME_DOWN")
        assertFalse(KeyMatching.matches(oplus, edge("snap")))
        assertEquals(null, context.sample(2, 1, "1000", "volume", tag, message).threadKey)
        raw("volume", "KEYCODE_VOLUME_DOWN")
        assertEquals(null, context.sample(1, 2, "1000", "volume", tag, message).threadKey)
    }

    @Test
    fun legacyNullSignatureStillAcceptsOnlyKnownSystemOplusLines() {
        val legacy = KeyBinding.Logged(null, null, null)
        assertTrue(KeyMatching.matches(legacy, KeySample.Log("system", tag, message, null)))
        assertFalse(KeyMatching.matches(legacy, KeySample.Log("2000", tag, message, null)))
        assertFalse(KeyMatching.matches(legacy, KeySample.Log("1000", "Other", "pressed", null)))
        assertFalse(KeyMatching.matches(oplus, KeySample.Log("1000", tag, message, "KEYCODE_VOLUME_DOWN")))
        assertTrue(KeyMatching.matches(oplus.copy(logKeyCode = null), KeySample.Log("1000", tag, message, "KEYCODE_VOLUME_DOWN")))
    }

    private fun key(code: Int, scan: Int) = KeySample.Accessibility(code, scan, true)
    private fun input(code: String) = KeySample.Input(InputKeyEvent(InputKey(code), true))
}
