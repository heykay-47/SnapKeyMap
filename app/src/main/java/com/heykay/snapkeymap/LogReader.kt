package com.heykay.snapkeymap

import android.content.Context
import android.util.Log
import java.util.concurrent.Executors
import kotlin.concurrent.thread

enum class LogAccess {
    /** Not reading the log. */
    OFF,
    /** Started; Android is showing (or about to show) its "Allow access to all device logs?" pop-up. */
    WAITING,
    /** Android only lets us see our own log lines: the pop-up was declined. */
    LIMITED,
    /** We can see the whole system log, so Snap Key presses can be detected. */
    FULL,
}

enum class LogSource { LOCAL, SHIZUKU }

/**
 * Streams `logcat` in a background thread.
 *
 * Local readers need foreground consent; Shizuku readers run as shell without that prompt.
 */
class LogReader(
    private val context: Context,
    private val onLine: (timestamp: String, uid: String, tid: String, tag: String, message: String) -> Unit,
    private val onAccess: (LogAccess) -> Unit,
    private val onSource: (LogSource) -> Unit,
    private val dispatchAccess: (() -> Unit) -> Unit = { it() },
    private val shouldParse: () -> Boolean = { true },
    private val onStart: () -> Unit = {},
) {
    @Volatile private var session: Session? = null
    @Volatile private var tryLocal = false
    // Finish tearing down the old process before starting its replacement.
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "snapkey-logcat").apply { isDaemon = true }
    }

    fun needsForegroundConsent(): Boolean = tryLocal || !canUseShizuku()

    @Synchronized
    fun start(preferShizuku: Boolean = false) {
        if (session?.active == true) return
        if (preferShizuku) tryLocal = false
        val next = Session()
        session = next
        onStart()
        worker.execute { next.run() }
    }

    @Synchronized
    fun stop() {
        session?.close()
        session = null
    }

    private inner class Session {
        @Volatile var active = true
            private set
        @Volatile private var process: Process? = null
        @Volatile private var shell: ShizukuLogStream? = null
        @Volatile private var full = false

        fun close() {
            active = false
            process?.destroy()
            shell?.close()
        }

        private fun report(access: LogAccess) {
            dispatchAccess {
                if (session === this) onAccess(access)
            }
        }

        fun run() {
            if (!active) return
            report(LogAccess.WAITING)
            val source = if (!tryLocal && canUseShizuku()) LogSource.SHIZUKU else LogSource.LOCAL
            tryLocal = false
            dispatchAccess { if (session === this) onSource(source) }
            val myPid = android.os.Process.myPid()
            var ownProbes = 0
            var result = LogAccess.OFF
            try {
                val input = if (source == LogSource.SHIZUKU) {
                    val stream = ShizukuLogStream(context)
                    shell = stream
                    if (!active) return
                    stream.open()
                } else {
                    val proc = ProcessBuilder("logcat", "-v", "threadtime,uid", "-T", "1")
                        .redirectErrorStream(true).start()
                    process = proc
                    if (!active) return
                    proc.inputStream
                }
                startProbe()
                input.bufferedReader().use { reader ->
                    while (active) {
                        val line = reader.readLine() ?: break
                        if (full && !shouldParse()) continue
                        val match = LINE.matchEntire(line) ?: continue
                        val timestamp = match.groupValues[1]
                        val uid = match.groupValues[2]
                        val pid = match.groupValues[3].toIntOrNull() ?: continue
                        val tid = match.groupValues[4]
                        val tag = match.groupValues[5]
                        val message = match.groupValues[6]
                        if (pid != myPid) {
                            if (!full) {
                                full = true
                                report(LogAccess.FULL)
                            }
                            synchronized(this@LogReader) {
                                if (active) onLine(timestamp, uid, tid, tag, message)
                            }
                        } else if (source == LogSource.LOCAL && !full && tag == TAG && message == PROBE && ++ownProbes >= 2) {
                            // Our own probes arrive but nothing from other apps: access was declined.
                            result = LogAccess.LIMITED
                            break
                        }
                    }
                }
            } catch (_: Exception) {
                // Failed binds and broken streams end the session; explicit stops suppress the report.
            } finally {
                shell?.close()
                process?.destroy()
                process?.waitFor()
                if (active && source == LogSource.SHIZUKU) tryLocal = true
                val endedOnItsOwn = active
                active = false
                if (endedOnItsOwn) report(result)
            }
        }

        /** Writes marker lines so we can tell "declined" (we see only these) from "still asking" (we see nothing). */
        private fun startProbe() = thread(isDaemon = true, name = "snapkey-logcat-probe") {
            repeat(40) {
                if (!active || full) return@thread
                Log.i(TAG, PROBE)
                Thread.sleep(1500)
            }
        }
    }

    private companion object {
        const val TAG = "SnapKeyMap"
        const val PROBE = "log access probe"

        // threadtime,uid format: "10-18 04:27:44.123  1000  1234  5678 D Tag     : message"
        val LINE = Regex("""^(\d\d-\d\d\s+[\d:.]+)\s+(\S+)\s+(\d+)\s+(\d+)\s+[VDIWEFA]\s+(.*?)\s*: (.*)$""")
    }
}
