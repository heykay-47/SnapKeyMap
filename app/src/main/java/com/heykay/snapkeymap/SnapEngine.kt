package com.heykay.snapkeymap

import android.Manifest
import android.app.KeyguardManager
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

enum class DetectionSource { KEY_EVENT, PRECISE_INPUT, SYSTEM_LOG }

internal const val DETECTION_HISTORY_SIZE = 20

internal fun appendDetection(history: List<SnapEngine.Detected>, d: SnapEngine.Detected): List<SnapEngine.Detected> =
    (history + d).takeLast(DETECTION_HISTORY_SIZE)

internal class LogRetryBackoff {
    private var healthySinceMs: Long? = null
    private var delayMs = 1000L

    fun onAccess(access: LogAccess, nowMs: Long) {
        if (access == LogAccess.FULL) {
            if (healthySinceMs == null) healthySinceMs = nowMs
        } else {
            healthySinceMs?.let { if (nowMs - it >= 30_000L) delayMs = 1000L }
            healthySinceMs = null
        }
    }

    fun nextDelayMs(): Long = delayMs.also {
        delayMs = (delayMs * 2).coerceAtMost(60_000L)
    }
}

/** Connects the two ways of noticing the Snap Key (key events and the system log) to the actions. */
object SnapEngine {
    data class Detected(val gesture: Gesture, val atMs: Long, val source: DetectionSource)

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
    private val teachingLogEdges = TeachingLogEdges()

    private val accessState = MutableStateFlow(LogAccess.OFF)
    val logAccess: StateFlow<LogAccess> = accessState.asStateFlow()
    private val sourceState = MutableStateFlow(LogSource.LOCAL)
    val logSource: StateFlow<LogSource> = sourceState.asStateFlow()

    private val lastDetected = MutableStateFlow<Detected?>(null)
    val lastGesture: StateFlow<Detected?> = lastDetected.asStateFlow()
    private val historyState = MutableStateFlow<List<Detected>>(emptyList())
    val detections: StateFlow<List<Detected>> = historyState.asStateFlow()

    private val lastKeySeenState = MutableStateFlow<Long?>(null)
    val lastKeySeenAt: StateFlow<Long?> = lastKeySeenState.asStateFlow()

    private val listeningSinceState = MutableStateFlow<Long?>(null)
    val listeningSince: StateFlow<Long?> = listeningSinceState.asStateFlow()

    /** Bumped whenever our screen comes back, so the UI re-checks permissions granted elsewhere. */
    private val resumeCount = MutableStateFlow(0)
    val uiResumes: StateFlow<Int> = resumeCount.asStateFlow()

    private var teachingSession: TeachingSession? = null
    private val teachingState = MutableStateFlow<TeachingState?>(null)
    internal val teaching: StateFlow<TeachingState?> = teachingState.asStateFlow()
    private val delivery = TeachingDelivery()
    private var nextTeachingId = 0L
    private var teachingLogDemand = false
    private var inputLearningBoundary = Long.MIN_VALUE
    private var inputPressed = false
    private var ignoreInputUntilUp = false
    private val heldInputKeys = mutableSetOf<InputKey>()
    private var logResumeAfterMs = Long.MIN_VALUE
    private var mainUiVisible = false
    private var foregroundApp: String? = null
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
    private val loggedKeys = LoggedKeyContext()
    private val logRetry = LogRetryBackoff()
    private var inputRetryDelayMs = 1000L
    private val reconnectInput = Runnable {
        if (wantsLog && readersDemanded() && canUseShizuku()) inputReader.start()
    }
    private val reconnect = Runnable {
        if (wantsLog && readersDemanded() && hasLogPermission()) {
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
        ActionRunner.init(app)
        reader = LogReader(
            context = app,
            onLine = ::onLogLine,
            onAccess = ::onAccessChanged,
            onSource = {
                if (sourceState.value != it) invalidateTeachingSource(logs = true)
                sourceState.value = it
            },
            dispatchAccess = { task -> main.post { task() }; Unit },
            shouldParse = { !preciseState.value || delivery.stamp.handle != null },
            onStart = {
                logGeneration++
                teachingLogEdges.reset()
                invalidateTeachingSource(logs = true)
            },
        )
        keyGestures = gestureDetector(DetectionSource.KEY_EVENT)
        logGestures = gestureDetector(DetectionSource.SYSTEM_LOG)
        inputGestures = gestureDetector(DetectionSource.PRECISE_INPUT)
        inputReader = InputReader(app, ::onInputEvent, ::onInputLiveChanged, { delivery.stamp }) { task ->
            main.post { task() }; Unit
        }
        Notifications.createChannel(app)
        scope.launch {
            store.config.collect { updateInputSource() }
        }
        scope.launch {
            store.config.map { Triple(it.enabled, it.needsLogAccess, it.isTaught) }
                .distinctUntilChanged().collect { (enabled, needsLog, _) ->
                    if ((!enabled || !needsLog) && !teachingLogDemand) {
                        consentAttempted = false
                        stop()
                    } else if (uiVisible && accessState.value == LogAccess.OFF) connect()
                    else if (!uiVisible) startBackgroundLogs()
                    updateListeningSince()
                }
        }
    }

    fun now(): Long = SystemClock.elapsedRealtime()

    private fun gestureDetector(source: DetectionSource) = GestureDetector(
        schedule = { task, delay -> main.postDelayed(task, delay); Unit },
        cancel = { task -> main.removeCallbacks(task) },
        holdMs = { store.config.value.holdMs },
        doublePressWindowMs = { store.config.value.doublePressWindowMs },
        onGesture = { onGesture(it, source) },
    )

    fun hasLogPermission(): Boolean =
        app.checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED

    /** Local readers need our screen on top; shell readers do not need foreground consent. */
    fun connect(preferShizuku: Boolean = false) {
        if (!readersDemanded() || !hasLogPermission()) return
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

    internal fun prepareTeachingLogs() {
        teachingLogDemand = true
        connect()
    }

    internal fun releaseTeachingLogs() {
        if (teachingSession != null) return
        teachingLogDemand = false
        if (!readersDemanded()) stop()
    }

    fun stop() {
        invalidateTeachingSource(logs = true, input = true)
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
            invalidateTeachingSource(logs = true)
            logGeneration++
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
        invalidateTeachingSource(logs = false, accessibility = true)
        refreshTeachingAccess()
        updateListeningSince()
        startBackgroundLogs()
        if (!uiVisible) notifyIfPaused()
    }

    fun onServiceDisconnected() {
        refreshTeachingAccess()
        foregroundApp = null
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
        refreshTeachingAccess()
        updateInputSource()
        main.removeCallbacks(reconnectInput)
        if (live) inputRetryDelayMs = 1000L
        else if (wantsLog && readersDemanded() && canUseShizuku()) {
            main.postDelayed(reconnectInput, inputRetryDelayMs)
            inputRetryDelayMs = (inputRetryDelayMs * 2).coerceAtMost(60_000L)
        }
    }

    private fun updateInputSource() {
        val config = store.config.value
        val precise = config.enabled && config.needsLogAccess && config.inputKey != null && inputLiveState.value
        if (precise != preciseState.value) {
            invalidateTeachingSource(logs = true)
            teachingLogEdges.reset()
            val alreadyHandled = precise && logGestures.hasHandledPress
            val releasedAfterSwitch = !precise && inputPressed
            logGeneration++
            resetGestures()
            oplusEdges.reset(pressed = releasedAfterSwitch)
            inputPressed = releasedAfterSwitch
            ignoreInputUntilUp = alreadyHandled
            preciseState.value = precise
        }
        updateListeningSince()
    }

    private fun onInputEvent(event: InputKeyEvent, time: Long, stamp: TeachingDelivery.Stamp) {
        delivery.deliver(stamp) { onCurrentInputEvent(event, time) }
    }

    private fun onCurrentInputEvent(event: InputKeyEvent, time: Long) {
        if (time <= inputLearningBoundary) return
        if (event.down) heldInputKeys += event.key else heldInputKeys -= event.key
        teachingSession?.let { session ->
            session.observe(KeySample.Input(event), time)
            publishTeaching()
            return
        }
        val config = store.config.value
        if (!preciseState.value || !KeyMatching.matches(config.keyBinding(), KeySample.Input(event))) return
        inputPressed = event.down
        if (ignoreInputUntilUp) {
            if (!event.down) ignoreInputUntilUp = false
            return
        }
        inputGestures.wantsDouble = config.action(Gesture.DOUBLE) != SnapAction.NoAction
        inputGestures.wantsLong = config.action(Gesture.LONG) != SnapAction.NoAction
        inputGestures.wantsTapHold = config.action(Gesture.TAP_HOLD) != SnapAction.NoAction
        markKeySeen()
        if (event.down) inputGestures.down() else inputGestures.up()
    }

    private fun onAccessChanged(access: LogAccess) {
        if (accessState.value == LogAccess.FULL && access != LogAccess.FULL) {
            logGeneration++
            invalidateTeachingSource(logs = true)
        }
        if (access == LogAccess.WAITING) invalidateTeachingSource(logs = true)
        logRetry.onAccess(access, now())
        accessState.value = access
        refreshTeachingAccess()
        updateListeningSince()
        if (access == LogAccess.FULL) {
            consentAttempted = false
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
        if (access == LogAccess.OFF && wantsLog && readersDemanded() && hasLogPermission()) {
            main.postDelayed(reconnect, logRetry.nextDelayMs())
        }
    }

    private fun notifyIfPaused() {
        val config = store.config.value
        val paused = config.enabled && config.isTaught && config.needsLogAccess &&
            hasLogPermission() && !preciseState.value && accessState.value != LogAccess.FULL
        if (paused) Notifications.showPaused(app)
    }

    // Called on the logcat thread for every line from another process.
    private fun onLogLine(timestamp: String, uid: String, tid: String, tag: String, message: String) {
        val stamp = delivery.stamp
        val time = now()
        if (preciseState.value && stamp.handle == null) return
        val generation = logGeneration
        val sample = loggedKeys.sample(generation, stamp.epoch, uid, tid, tag, message)
        val explicitEdges = isKnownOplusKeyLine(uid, tag, message)
        if (stamp.handle == null && !KeyMatching.matches(store.config.value.keyBinding(), sample)) return
        main.post {
            if (generation != logGeneration) return@post
            delivery.deliver(stamp) {
                val session = teachingSession
                if (stamp.handle != null) {
                    if (session == null) return@deliver
                    session.observe(sample, time)
                    if (explicitEdges) teachingLogEdges.observe(timestamp, sample)
                    publishTeaching()
                    return@deliver
                }
                val config = store.config.value
                if (!config.enabled || preciseState.value || !config.needsLogAccess) return@deliver
                if (KeyMatching.matches(config.keyBinding(), sample)) {
                    onLogKeyLine(timestamp, explicitEdges)
                }
            }
        }
    }

    /** Known Oppo logs report edges; other learned logs retain the silence-based fallback. */
    private fun onLogKeyLine(timestamp: String, explicitEdges: Boolean) {
        val config = store.config.value
        logGestures.wantsDouble = config.action(Gesture.DOUBLE) != SnapAction.NoAction
        logGestures.wantsLong = (explicitEdges || config.holdRepeats) && config.action(Gesture.LONG) != SnapAction.NoAction
        logGestures.wantsTapHold = (explicitEdges || config.holdRepeats) && config.action(Gesture.TAP_HOLD) != SnapAction.NoAction
        if (explicitEdges) {
            inputPressed = false
            oplusEdges.accept(timestamp)
            return
        }
        if (now() < logResumeAfterMs) {
            logResumeAfterMs = now() + config.releaseGapMs
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
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return false
        val sample = KeySample.Accessibility(event.keyCode, event.scanCode, event.action == KeyEvent.ACTION_DOWN, event.repeatCount)
        teachingSession?.let { session ->
            session.observe(sample, now())
            publishTeaching()
            return false
        }
        val config = store.config.value
        if (!config.enabled || !KeyMatching.matches(config.keyBinding(), sample)) return false
        keyGestures.wantsDouble = config.action(Gesture.DOUBLE) != SnapAction.NoAction
        keyGestures.wantsLong = config.action(Gesture.LONG) != SnapAction.NoAction
        keyGestures.wantsTapHold = config.action(Gesture.TAP_HOLD) != SnapAction.NoAction
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
        return foregroundApp !in config.pausedApps
    }

    fun onForegroundApp(packageName: String) {
        foregroundApp = packageName
    }

    private fun onGesture(gesture: Gesture, source: DetectionSource) {
        if (teachingSession != null) return
        markKeySeen()
        val detected = Detected(gesture, now(), source)
        historyState.value = appendDetection(historyState.value, detected)
        lastDetected.value = detected
        val config = store.config.value
        if (foregroundApp in config.pausedApps) return
        if (gesture in config.unlockedOnly && app.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        val action = config.action(gesture)
        if (action == SnapAction.NoAction) return
        if (action !is SnapAction.Ringer) buzz()
        ActionRunner.run(SnapAccessibilityService.instance ?: app, action)
    }

    private fun buzz() {
        val vibrator = app.getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }

    internal fun beginTeaching(): TeachingHandle {
        if (teachingSession != null) {
            teachingLogEdges.restore(store.config.value.logKeyCode, oplusEdges)
            teachingSession?.cancel()
        }
        val handle = TeachingHandle(++nextTeachingId)
        inputLearningBoundary = now()
        resetGestures()
        teachingLogEdges.begin(store.config.value.logKeyCode, oplusEdges)
        oplusEdges.reset()
        teachingSession = TeachingSession(teachingAccess())
        delivery.advance(handle)
        teachingLogDemand = true
        publishTeaching()
        connect()
        return handle
    }

    /** Called after the hands-off step: the key is released and lines from earlier presses have been handled. */
    internal fun markKeyReleased(handle: TeachingHandle) {
        if (delivery.stamp.handle != handle || teachingSession?.state != TeachingState.Capturing) return
        oplusEdges.reset()
        teachingLogEdges.reset()
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

    internal fun cancelTeaching(handle: TeachingHandle) {
        if (delivery.stamp.handle != handle) return
        teachingSession?.cancel()
        endTeaching()
    }

    internal fun captureTeaching(handle: TeachingHandle, baseline: LongRange, taps: List<LongRange>, hold: LongRange) {
        if (delivery.stamp.handle != handle || teachingSession?.state != TeachingState.Capturing) return
        teachingSession?.capture(baseline, taps, hold)
        delivery.advance(handle)
        publishTeaching()
    }

    internal fun checkDifferentKey(handle: TeachingHandle) {
        if (delivery.stamp.handle != handle || teachingSession?.state !is TeachingState.Candidate) return
        refreshTeachingAccess()
        delivery.advance(handle)
        teachingSession?.beginCheck(now())
        publishTeaching()
    }

    internal fun finishDifferentKeyCheck(handle: TeachingHandle) {
        if (delivery.stamp.handle != handle) return
        refreshTeachingAccess()
        teachingSession?.finishCheck(now())
        publishTeaching()
    }

    internal fun saveTeaching(handle: TeachingHandle): Boolean {
        if (delivery.stamp.handle != handle) return false
        refreshTeachingAccess()
        val patch = teachingSession?.takeSave() ?: return false
        store.update(patch::applyTo)
        endTeaching()
        return true
    }

    private fun endTeaching() {
        inputLearningBoundary = now()
        resetGestures()
        teachingSession = null
        delivery.advance(null)
        teachingState.value = null
        teachingLogDemand = false
        val config = store.config.value
        if (!config.enabled || !config.needsLogAccess) stop()
        else if (hasLogPermission() && accessState.value == LogAccess.OFF) connect()
        updateInputSource()
        teachingLogEdges.restore(store.config.value.logKeyCode, oplusEdges)
        teachingLogEdges.reset()
        ignoreInputUntilUp = store.config.value.inputKey?.let { it in heldInputKeys } == true
        inputPressed = ignoreInputUntilUp
        logResumeAfterMs = now() + store.config.value.releaseGapMs
    }

    private fun readersDemanded(): Boolean = teachingLogDemand ||
        (store.config.value.enabled && store.config.value.needsLogAccess)

    private fun teachingAccess() = TeachingAccess(
        SnapAccessibilityService.running.value, accessState.value, inputLiveState.value,
    )

    private fun refreshTeachingAccess() {
        teachingSession?.accessChanged(teachingAccess())
        publishTeaching()
    }

    private fun invalidateTeachingSource(logs: Boolean, input: Boolean = false, accessibility: Boolean = false) {
        teachingSession?.sourceRestarted(logs, input, accessibility)
        publishTeaching()
    }

    private fun publishTeaching() {
        teachingState.value = teachingSession?.state
    }
}
