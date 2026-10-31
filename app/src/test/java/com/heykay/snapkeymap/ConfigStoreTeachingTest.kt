package com.heykay.snapkeymap

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigStoreTeachingTest {
    @Test
    fun falseAndThrowingCommitRestorePreferenceMemoryAndKeepCandidateForExplicitRetry() {
        for (failure in listOf(Outcome.FALSE, Outcome.THROW)) {
            val prefs = FakePreferences()
            val store = ConfigStore(prefs) { false }
            val session = readySession()
            val old = store.config.value
            val snapshot = prefs.getAll()
            val publishedDuringCommit = mutableListOf<Config>()
            val stateDuringCommit = mutableListOf<TeachingState>()
            prefs.outcomes.addAll(listOf(failure, Outcome.FALSE))
            prefs.duringCommit = {
                publishedDuringCommit += store.config.value
                stateDuringCommit += session.state
            }
            assertFalse(session.save { store.updateConfirmed(it::applyTo) })
            assertTrue(session.state is TeachingState.SaveFailed)
            assertEquals(old, store.config.value)
            assertEquals(snapshot, prefs.getAll())
            assertEquals(old, ConfigStore(prefs) { false }.config.value)
            assertEquals(listOf(old, old), publishedDuringCommit)
            assertTrue(stateDuringCommit.all { it is TeachingState.Ready })
            assertEquals(0, prefs.applies)
            assertEquals(2, prefs.commits)

            prefs.duringCommit = null
            store.update { it.copy(theme = ThemeMode.DARK, pausedApps = setOf("current.app"), holdMs = 900) }
            val current = store.config.value
            prefs.duringCommit = {
                publishedDuringCommit += store.config.value
                stateDuringCommit += session.state
            }
            assertTrue(session.save { store.updateConfirmed(it::applyTo) })
            assertEquals(TeachingState.Closed, session.state)
            assertEquals(current.copy(keyEvent = LearnedKey(142, 88)), store.config.value)
            assertEquals(store.config.value, ConfigStore(prefs) { false }.config.value)
            assertEquals(prefs.getAll(), prefs.disk)
            assertEquals(listOf(old, old, current), publishedDuringCommit)
            assertTrue(stateDuringCommit.last() is TeachingState.SaveFailed)
            assertFalse(session.save { store.updateConfirmed(it::applyTo) })
            assertEquals(3, prefs.commits)
            assertEquals(1, prefs.applies)
        }
    }

    @Test
    fun cancelAfterFailedProductionSaveKeepsOldConfigAndCannotRetry() {
        val prefs = FakePreferences()
        val store = ConfigStore(prefs) { false }
        val old = store.config.value
        val snapshot = prefs.getAll()
        val session = readySession()
        prefs.outcomes += Outcome.FALSE
        assertFalse(session.save { store.updateConfirmed(it::applyTo) })
        session.cancel()
        assertFalse(session.save { store.updateConfirmed(it::applyTo) })
        assertEquals(TeachingState.Closed, session.state)
        assertEquals(old, store.config.value)
        assertEquals(snapshot, prefs.getAll())
        assertEquals(2, prefs.commits)
    }

    @Test
    fun failedProductionSaveKeepsMatchingAndRequiredAccessChecksActive() {
        for (collision in listOf(false, true)) {
            val prefs = FakePreferences()
            val store = ConfigStore(prefs) { false }
            val old = store.config.value
            val session = readySession()
            prefs.outcomes += Outcome.THROW
            assertFalse(session.save { store.updateConfirmed(it::applyTo) })
            if (collision) session.observe(KeySample.Accessibility(142, 88, false), 30000)
            else session.accessChanged(TeachingAccess(false, true, true))
            assertEquals(TeachingState.Failed(if (collision) TeachingFailure.COLLISION else TeachingFailure.ACCESS_LOST), session.state)
            assertFalse(session.save { store.updateConfirmed(it::applyTo) })
            assertEquals(old, store.config.value)
            assertEquals(2, prefs.commits)
        }
    }

    @Test
    fun ordinaryUpdateStillPublishesAndUsesApplyWithoutAConfirmedCommit() {
        val prefs = FakePreferences()
        val store = ConfigStore(prefs) { false }
        store.update { it.copy(theme = ThemeMode.DARK, holdMs = 5000) }
        assertEquals(ThemeMode.DARK, store.config.value.theme)
        assertEquals(1200L, store.config.value.holdMs)
        assertEquals("DARK", prefs.getString("theme", null))
        assertEquals(1, prefs.applies)
        assertEquals(0, prefs.commits)
    }

    private fun readySession() = TeachingSession(TeachingAccess(true, false, false)).apply {
        listOf(7000L, 11000L, 15000L, 18200L).forEach {
            observe(KeySample.Accessibility(142, 88, true), it)
        }
        capture(0L..5000L, listOf(6000L..9000L, 10000L..13000L, 14000L..17000L), 18000L..21500L)
        beginCheck(23000)
        observe(KeySample.Accessibility(25, 114, true), 24000)
        observe(KeySample.Accessibility(25, 114, false), 24100)
        finishCheck(23000 + TeachingSession.CHECK_MS + TeachingSession.SETTLE_MS)
        assertTrue(state is TeachingState.Ready)
    }

    private enum class Outcome { TRUE, FALSE, THROW }

    private class FakePreferences : SharedPreferences {
        private val memory = mutableMapOf<String, Any>(
            "setup_complete" to true, "key_code" to 141, "scan_code" to 87, "theme" to "LIGHT",
            "other_boolean" to false, "other_int" to 12, "other_long" to 13L,
            "other_float" to 1.5f, "other_string" to "retained", "other_set" to setOf("retained"),
        )
        var disk: Map<String, Any> = memory.toMap()
            private set
        val outcomes = ArrayDeque<Outcome>()
        var duringCommit: (() -> Unit)? = null
        var commits = 0
            private set
        var applies = 0
            private set

        override fun getAll(): MutableMap<String, *> = memory.toMutableMap()
        override fun contains(key: String): Boolean = key in memory
        override fun getString(key: String, defValue: String?): String? = memory[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            (memory[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues
        override fun getInt(key: String, defValue: Int): Int = memory[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = memory[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = memory[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = memory[key] as? Boolean ?: defValue
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val changes = mutableMapOf<String, Any?>()
            private var clear = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { changes[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor =
                apply { changes[key] = values?.toSet() }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { changes[key] = value }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { changes[key] = value }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { changes[key] = value }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { changes[key] = value }
            override fun remove(key: String): SharedPreferences.Editor = apply { changes[key] = null }
            override fun clear(): SharedPreferences.Editor = apply { clear = true }

            private fun publishMemory() {
                if (clear) memory.clear()
                changes.forEach { (key, value) -> if (value == null) memory.remove(key) else memory[key] = value }
            }

            override fun commit(): Boolean {
                commits++
                publishMemory()
                duringCommit?.invoke()
                return when (outcomes.removeFirstOrNull() ?: Outcome.TRUE) {
                    Outcome.TRUE -> { disk = memory.toMap(); true }
                    Outcome.FALSE -> false
                    Outcome.THROW -> throw IllegalStateException("Injected write failure after memory changed")
                }
            }

            override fun apply() {
                applies++
                publishMemory()
            }
        }
    }
}
