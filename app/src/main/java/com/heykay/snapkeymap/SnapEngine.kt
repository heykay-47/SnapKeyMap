package com.heykay.snapkeymap

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Connects the two ways of noticing the Snap Key (key events and the system log) to the actions. */
object SnapEngine {
    data class Detected(val gesture: Gesture, val atMs: Long)

    lateinit var store: ConfigStore
        private set

    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var reader: LogReader
    private lateinit var keyGestures: GestureDetector
    private lateinit var logGestures: GestureDetector
    private val oplusEdges = OplusKeyEdges(
        onDown = { logGestures.down() },
        onUp = { logGestures.up() },
    )

    private val accessState = MutableStateFlow(LogAccess.OFF)
    val logAccess: StateFlow<LogAccess> = accessState.asStateFlow()

    private val lastDetected = MutableStateFlow<Detected?>(null)
    val lastGesture: StateFlow<Detected?> = lastDetected.asStateFlow()

    /** Bumped whenever our screen comes back, so the UI re-checks permissions granted elsewhere. */
    private val resumeCount = MutableStateFlow(0)
    val uiResumes: StateFlow<Int> = resumeCount.asStateFlow()

    @Volatile private var learning: MutableList<Observation>? = null
    private var uiVisible = false
    private var wantsLog = false
    @Volatile private var logGeneration = 0
    private var retryDelayMs = 1000L
    private val reconnect = Runnable {
        if (wantsLog && store.config.value.enabled && hasLogPermission()) reader.start()
    }
    private var logKeyDown = false
    private val logKeyUp = Runnable {
        logKeyDown = false
        logGestures.up()
    }

    fun init(context: Context) {
        app = context.applicationContext
        store = ConfigStore(app)
        reader = LogReader(
            onLine = ::onLogLine,
            onAccess = ::onAccessChanged,
            dispatchAccess = { task -> main.post { task() }; Unit },
        )
        keyGestures = gestureDetector(longPressMs = 500)
        logGestures = gestureDetector(longPressMs = 650)
        Notifications.createChannel(app)
        scope.launch {
            store.config.map { it.enabled to it.needsLogAccess }.distinctUntilChanged().collect { (enabled, needsLog) ->
                if (!enabled || !needsLog) stop()
                else if (uiVisible && accessState.value == LogAccess.OFF) connect()
            }
        }
    }

    fun now(): Long = SystemClock.elapsedRealtime()

    private fun gestureDetector(longPressMs: Long) = GestureDetector(
        schedule = { task, delay -> main.postDelayed(task, delay); Unit },
        cancel = { task -> main.removeCallbacks(task) },
        longPressMs = longPressMs,
        onGesture = ::onGesture,
    )

    fun hasLogPermission(): Boolean =
        app.checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED

    /** Starts reading the system log. Only works while our screen is showing; Android asks the user. */
    fun connect() {
        if (!store.config.value.enabled || !hasLogPermission()) return
        wantsLog = true
        main.removeCallbacks(reconnect)
        reader.start()
    }

    fun stop() {
        wantsLog = false
        logGeneration++
        main.removeCallbacks(reconnect)
        reader.stop()
        onAccessChanged(LogAccess.OFF)
        resetGestures()
    }

    fun onUiResumed() {
        uiVisible = true
        resumeCount.value++
        Notifications.cancelPaused(app)
        val config = store.config.value
        if (config.needsLogAccess && hasLogPermission() && accessState.value == LogAccess.OFF) connect()
    }

    fun onUiStopped() {
        uiVisible = false
    }

    fun onServiceConnected() {
        if (!uiVisible) notifyIfPaused()
    }

    private fun onAccessChanged(access: LogAccess) {
        accessState.value = access
        if (access == LogAccess.FULL) {
            retryDelayMs = 1000L
            Notifications.cancelPaused(app)
        }
        if (access == LogAccess.OFF || access == LogAccess.LIMITED) {
            main.removeCallbacks(logKeyUp)
            logKeyDown = false
            oplusEdges.reset()
            logGestures.reset()
            if (!uiVisible) notifyIfPaused()
        }
        main.removeCallbacks(reconnect)
        if (access == LogAccess.OFF && wantsLog && store.config.value.enabled && hasLogPermission()) {
            main.postDelayed(reconnect, retryDelayMs)
            retryDelayMs = (retryDelayMs * 2).coerceAtMost(60_000L)
        }
    }

    private fun notifyIfPaused() {
        val config = store.config.value
        val paused = config.enabled && config.isTaught && config.needsLogAccess &&
            hasLogPermission() && accessState.value != LogAccess.FULL
        if (paused) Notifications.showPaused(app)
    }

    // Called on the logcat thread for every line from another process.
    private fun onLogLine(timestamp: String, uid: String, tag: String, message: String) {
        val generation = logGeneration
        val explicitEdges = isKnownOplusKeyLine(uid, tag, message)
        learning?.let { list ->
            synchronized(list) {
                if (learning === list) list += Observation(now(), KeyLearning.logKey(uid, tag, message))
            }
            if (explicitEdges) main.post {
                if (generation == logGeneration) oplusEdges.observe(timestamp)
            }
            return
        }
        val config = store.config.value
        val isSnapKey = config.enabled && config.needsLogAccess && acceptsKeyLog(config.logSignature, uid, tag, message)
        if (explicitEdges || isSnapKey) main.post {
            if (generation != logGeneration || !store.config.value.enabled) return@post
            if (learning != null) {
                if (explicitEdges) oplusEdges.observe(timestamp)
            } else if (isSnapKey) {
                onLogKeyLine(timestamp, explicitEdges)
            }
        }
    }

    /** Known Oppo logs report edges; other learned logs retain the silence-based fallback. */
    private fun onLogKeyLine(timestamp: String, explicitEdges: Boolean) {
        val config = store.config.value
        logGestures.wantsDouble = config.action(Gesture.DOUBLE) != SnapAction.NoAction
        logGestures.wantsLong = (explicitEdges || config.holdRepeats) && config.action(Gesture.LONG) != SnapAction.NoAction
        if (explicitEdges) {
            oplusEdges.accept(timestamp)
            return
        }
        if (!logKeyDown) {
            logKeyDown = true
            logGestures.down()
        }
        main.removeCallbacks(logKeyUp)
        main.postDelayed(logKeyUp, config.releaseGapMs)
    }

    /** Called by the accessibility service. Returns true to stop the key from reaching other apps. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        learning?.let { list ->
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && event.keyCode !in IGNORED_KEYS) {
                synchronized(list) { list += Observation(now(), KeyLearning.keyEventKey(event.keyCode, event.scanCode)) }
            }
            return false
        }
        val config = store.config.value
        val key = config.keyEvent ?: return false
        if (!config.enabled || event.keyCode != key.keyCode) return false
        if (key.keyCode == KeyEvent.KEYCODE_UNKNOWN && event.scanCode != key.scanCode) return false
        keyGestures.wantsDouble = config.action(Gesture.DOUBLE) != SnapAction.NoAction
        keyGestures.wantsLong = config.action(Gesture.LONG) != SnapAction.NoAction
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) keyGestures.down()
            KeyEvent.ACTION_UP -> keyGestures.up()
        }
        return true
    }

    private fun onGesture(gesture: Gesture) {
        lastDetected.value = Detected(gesture, now())
        val action = store.config.value.action(gesture)
        if (action == SnapAction.NoAction) return
        buzz()
        ActionRunner.run(SnapAccessibilityService.instance ?: app, action)
    }

    private fun buzz() {
        val vibrator = app.getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }

    fun startLearning() {
        resetGestures()
        learning = mutableListOf()
    }

    /** Called after the hands-off step: the key is released and lines from earlier presses have been handled. */
    fun markKeyReleased() {
        oplusEdges.reset()
    }

    private fun resetGestures() {
        main.removeCallbacks(logKeyUp)
        logKeyDown = false
        keyGestures.reset()
        logGestures.reset()
    }

    fun cancelLearning() {
        if (learning == null) return
        resetGestures()
        learning = null
    }

    fun finishLearning(baseline: LongRange, taps: List<LongRange>, hold: LongRange): LearnResult {
        val list = learning ?: return LearnResult.NothingFound
        resetGestures()
        val observations = synchronized(list) {
            learning = null
            list.toList()
        }
        val result = KeyLearning.analyze(observations, baseline, taps, hold)
        when (result) {
            is LearnResult.KeyEventFound -> store.update { it.copy(keyEvent = result.key, logSignature = null) }
            is LearnResult.LogLineFound -> store.update {
                it.copy(
                    keyEvent = null,
                    logSignature = result.signature,
                    holdRepeats = result.holdRepeats,
                    releaseGapMs = result.releaseGapMs,
                )
            }
            LearnResult.NothingFound -> Unit
        }
        return result
    }

    private val IGNORED_KEYS = setOf(
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_APP_SWITCH,
    )
}
