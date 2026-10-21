package com.heykay.snapkeymap

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import com.heykay.snapkeymap.ui.ConsentActivity
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
import rikka.shizuku.Shizuku

/** Connects the two ways of noticing the Snap Key (key events and the system log) to the actions. */
object SnapEngine {
    data class Detected(val gesture: Gesture, val atMs: Long)

    lateinit var store: ConfigStore
        private set

    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var reader: LogReader
    private lateinit var inputReader: InputReader
    private lateinit var keyGestures: GestureDetector
    private lateinit var logGestures: GestureDetector
    private lateinit var inputGestures: GestureDetector
    private val inputLiveState = MutableStateFlow(false)
    val inputLive: StateFlow<Boolean> = inputLiveState.asStateFlow()
    private val preciseState = MutableStateFlow(false)
    val preciseDetection: StateFlow<Boolean> = preciseState.asStateFlow()
    private val oplusEdges = OplusKeyEdges(
        onDown = { markKeySeen(); logGestures.down() },
        onUp = { markKeySeen(); logGestures.up() },
    )

    private val accessState = MutableStateFlow(LogAccess.OFF)
    val logAccess: StateFlow<LogAccess> = accessState.asStateFlow()
    private val sourceState = MutableStateFlow(LogSource.LOCAL)
    val logSource: StateFlow<LogSource> = sourceState.asStateFlow()

    private val lastDetected = MutableStateFlow<Detected?>(null)
    val lastGesture: StateFlow<Detected?> = lastDetected.asStateFlow()

    private val lastKeySeenState = MutableStateFlow<Long?>(null)
    val lastKeySeenAt: StateFlow<Long?> = lastKeySeenState.asStateFlow()

    private val listeningSinceState = MutableStateFlow<Long?>(null)
    val listeningSince: StateFlow<Long?> = listeningSinceState.asStateFlow()

    /** Bumped whenever our screen comes back, so the UI re-checks permissions granted elsewhere. */
    private val resumeCount = MutableStateFlow(0)
    val uiResumes: StateFlow<Int> = resumeCount.asStateFlow()

    @Volatile private var learning: MutableList<Observation>? = null
    private var inputLearningBoundary = Long.MIN_VALUE
    private var inputPressed = false
    private var ignoreInputUntilUp = false
    private var mainUiVisible = false
    private var consentVisible = false
    private val uiVisible get() = mainUiVisible || consentVisible
    private var consentAttempted = false
    private var wantsLog = false
    private var watchingShizuku = false
    private val binderReceived = Shizuku.OnBinderReceivedListener { main.post { connectShizukuIfNeeded() } }
    private val binderDead = Shizuku.OnBinderDeadListener {
        main.post {
            inputReader.stop()
            if (wantsLog && sourceState.value == LogSource.SHIZUKU) {
                reader.stop()
                onAccessChanged(LogAccess.OFF)
            }
        }
    }
    private val shizukuPermission = Shizuku.OnRequestPermissionResultListener { _, _ ->
        main.post { connectShizukuIfNeeded() }
    }
    @Volatile private var logGeneration = 0
    private var retryDelayMs = 1000L
    private var inputRetryDelayMs = 1000L
    private val reconnectInput = Runnable {
        if (wantsLog && store.config.value.enabled && canUseShizuku()) inputReader.start()
    }
    private val reconnect = Runnable {
        if (wantsLog && store.config.value.enabled && hasLogPermission()) {
            if (uiVisible || !reader.needsForegroundConsent()) reader.start()
            else startBackgroundLogs()
        }
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
            context = app,
            onLine = ::onLogLine,
            onAccess = ::onAccessChanged,
            onSource = { sourceState.value = it },
            dispatchAccess = { task -> main.post { task() }; Unit },
        )
        keyGestures = gestureDetector()
        logGestures = gestureDetector()
        inputGestures = gestureDetector()
        inputReader = InputReader(app, ::onInputEvent, ::onInputLiveChanged) { task ->
            main.post { task() }; Unit
        }
        Notifications.createChannel(app)
        scope.launch {
            store.config.collect { updateInputSource() }
        }
        scope.launch {
            store.config.map { Triple(it.enabled, it.needsLogAccess, it.isTaught) }
                .distinctUntilChanged().collect { (enabled, needsLog, _) ->
                    if (!enabled || !needsLog) {
                        consentAttempted = false
                        stop()
                    } else if (uiVisible && accessState.value == LogAccess.OFF) connect()
                    else if (!uiVisible) startBackgroundLogs()
                    updateListeningSince()
                }
        }
    }

    fun now(): Long = SystemClock.elapsedRealtime()

    private fun gestureDetector() = GestureDetector(
        schedule = { task, delay -> main.postDelayed(task, delay); Unit },
        cancel = { task -> main.removeCallbacks(task) },
        holdMs = { store.config.value.holdMs },
        doublePressWindowMs = { store.config.value.doublePressWindowMs },
        onGesture = ::onGesture,
    )

    fun hasLogPermission(): Boolean =
        app.checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED

    /** Local readers need our screen on top; shell readers do not need foreground consent. */
    fun connect(preferShizuku: Boolean = false) {
        if (!store.config.value.enabled || !hasLogPermission()) return
        wantLogs()
        main.removeCallbacks(reconnect)
        reader.start(preferShizuku)
    }

    private fun wantLogs() {
        wantsLog = true
        inputReader.start()
        if (!watchingShizuku) {
            watchingShizuku = true
            Shizuku.addBinderReceivedListener(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(shizukuPermission)
        }
    }

    fun stop() {
        wantsLog = false
        if (watchingShizuku) {
            watchingShizuku = false
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
            Shizuku.removeRequestPermissionResultListener(shizukuPermission)
        }
        logGeneration++
        main.removeCallbacks(reconnect)
        main.removeCallbacks(reconnectInput)
        inputReader.stop()
        reader.stop()
        onAccessChanged(LogAccess.OFF)
        resetGestures()
    }

    private fun connectShizukuIfNeeded() {
        if (wantsLog && canUseShizuku()) inputReader.start()
        if (wantsLog && (accessState.value != LogAccess.FULL || sourceState.value == LogSource.LOCAL) && canUseShizuku()) {
            reader.stop()
            connect(preferShizuku = true)
        }
    }

    fun onUiResumed() {
        mainUiVisible = true
        resumeCount.value++
        Notifications.cancelPaused(app)
        val config = store.config.value
        if (config.needsLogAccess && hasLogPermission() && accessState.value == LogAccess.OFF) connect()
        else connectShizukuIfNeeded()
    }

    fun onUiStopped() {
        mainUiVisible = false
    }

    fun onConsentResumed(connectReader: Boolean) {
        consentVisible = true
        if (!connectReader || accessState.value == LogAccess.FULL) return
        // A reader denied while we were backgrounded cannot prompt; replace it now we're on top.
        reader.stop()
        onAccessChanged(LogAccess.WAITING)
        connect()
    }

    fun onConsentPaused() {
        consentVisible = false
        if (!uiVisible) notifyIfPaused()
    }

    private fun startBackgroundLogs() {
        val config = store.config.value
        if (!config.enabled || !config.needsLogAccess || !config.isTaught || !hasLogPermission() ||
            accessState.value == LogAccess.FULL || uiVisible) return
        wantLogs()
        if (!reader.needsForegroundConsent()) {
            connect()
            return
        }
        val service = SnapAccessibilityService.instance ?: return
        if (consentAttempted) return
        consentAttempted = true
        try {
            service.startActivity(Intent(service, ConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            notifyIfPaused()
        }
    }

    fun onServiceConnected() {
        updateListeningSince()
        startBackgroundLogs()
        if (!uiVisible) notifyIfPaused()
    }

    fun onServiceDisconnected() {
        keyGestures.reset()
        updateListeningSince()
    }

    private fun updateListeningSince() {
        val config = store.config.value
        val listening = config.enabled && if (config.needsLogAccess) {
            preciseState.value || accessState.value == LogAccess.FULL
        } else {
            SnapAccessibilityService.running.value
        }
        if (!listening) listeningSinceState.value = null
        else if (listeningSinceState.value == null) listeningSinceState.value = System.currentTimeMillis()
    }

    private fun markKeySeen() {
        lastKeySeenState.value = System.currentTimeMillis()
    }

    private fun onInputLiveChanged(live: Boolean) {
        inputLiveState.value = live
        updateInputSource()
        main.removeCallbacks(reconnectInput)
        if (live) inputRetryDelayMs = 1000L
        else if (wantsLog && store.config.value.enabled && canUseShizuku()) {
            main.postDelayed(reconnectInput, inputRetryDelayMs)
            inputRetryDelayMs = (inputRetryDelayMs * 2).coerceAtMost(60_000L)
        }
    }

    private fun updateInputSource() {
        val config = store.config.value
        val precise = config.enabled && config.needsLogAccess && config.inputKey != null && inputLiveState.value
        if (precise != preciseState.value) {
            val alreadyHandled = precise && logGestures.hasHandledPress
            val releasedAfterSwitch = !precise && inputPressed
            logGeneration++
            resetGestures()
            // Raw input tells us whether the next Oppo log edge will be a release.
            oplusEdges.reset(pressed = releasedAfterSwitch)
            inputPressed = releasedAfterSwitch
            ignoreInputUntilUp = alreadyHandled
            preciseState.value = precise
        }
        updateListeningSince()
    }

    // InputReader dispatches events on main and rejects callbacks from old sessions.
    private fun onInputEvent(event: InputKeyEvent, time: Long) {
        // A learning edge queued before Finish/Cancel must not become a mapped action.
        if (time <= inputLearningBoundary) return
        learning?.let { list ->
            if (event.down) synchronized(list) { list += Observation(time, KeyLearning.inputKey(event.key.code)) }
            return
        }
        val config = store.config.value
        if (!preciseState.value || event.key != config.inputKey) return
        inputPressed = event.down
        if (ignoreInputUntilUp) {
            if (!event.down) ignoreInputUntilUp = false
            return
        }
        inputGestures.wantsDouble = config.action(Gesture.DOUBLE) != SnapAction.NoAction
        inputGestures.wantsLong = config.action(Gesture.LONG) != SnapAction.NoAction
        markKeySeen()
        if (event.down) inputGestures.down() else inputGestures.up()
    }

    private fun onAccessChanged(access: LogAccess) {
        accessState.value = access
        updateListeningSince()
        if (access == LogAccess.FULL) {
            consentAttempted = false
            retryDelayMs = 1000L
            Notifications.cancelPaused(app)
        }
        if (access == LogAccess.OFF || access == LogAccess.LIMITED) {
            main.removeCallbacks(logKeyUp)
            logKeyDown = false
            oplusEdges.reset(pressed = inputPressed)
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
            hasLogPermission() && !preciseState.value && accessState.value != LogAccess.FULL
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
        if (preciseState.value) return
        val isSnapKey = config.enabled && config.needsLogAccess && acceptsKeyLog(config.logSignature, uid, tag, message)
        if (explicitEdges || isSnapKey) main.post {
            if (generation != logGeneration || !store.config.value.enabled || preciseState.value) return@post
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
            inputPressed = false
            oplusEdges.accept(timestamp)
            return
        }
        if (!logKeyDown) {
            logKeyDown = true
            markKeySeen()
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
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                markKeySeen()
                keyGestures.down()
            }
            KeyEvent.ACTION_UP -> {
                markKeySeen()
                keyGestures.up()
            }
        }
        return true
    }

    private fun onGesture(gesture: Gesture) {
        markKeySeen()
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
        inputLearningBoundary = now()
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
        inputPressed = false
        ignoreInputUntilUp = false
        keyGestures.reset()
        logGestures.reset()
        inputGestures.reset()
    }

    fun cancelLearning() {
        if (learning == null) return
        inputLearningBoundary = now()
        resetGestures()
        learning = null
    }

    fun finishLearning(baseline: LongRange, taps: List<LongRange>, hold: LongRange): LearnResult {
        val list = learning ?: return LearnResult.NothingFound
        inputLearningBoundary = now()
        resetGestures()
        val observations = synchronized(list) {
            learning = null
            list.toList()
        }
        val result = KeyLearning.analyze(observations, baseline, taps, hold)
        when (result) {
            is LearnResult.KeyEventFound -> store.update {
                it.copy(keyEvent = result.key, logSignature = null, inputKey = result.inputKey)
            }
            is LearnResult.LogLineFound -> store.update {
                it.copy(
                    keyEvent = null,
                    logSignature = result.signature,
                    inputKey = result.inputKey,
                    holdRepeats = result.holdRepeats,
                    releaseGapMs = result.releaseGapMs,
                )
            }
            LearnResult.NothingFound -> Unit
        }
        updateInputSource()
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
