package com.heykay.snapkeymap

import android.util.Log
import java.io.IOException
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

/**
 * Streams `logcat` in a background thread.
 *
 * Android only offers full log access when the app is on screen, and then asks the user. So
 * [start] must be called while our activity is visible. Once allowed, the stream keeps working
 * in the background for as long as the process lives.
 */
class LogReader(
    private val onLine: (timestamp: String, uid: String, tag: String, message: String) -> Unit,
    private val onAccess: (LogAccess) -> Unit,
    private val dispatchAccess: (() -> Unit) -> Unit = { it() },
) {
    @Volatile private var session: Session? = null
    // Finish tearing down the old process before starting its replacement.
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "snapkey-logcat").apply { isDaemon = true }
    }

    @Synchronized
    fun start() {
        if (session?.active == true) return
        val next = Session()
        session = next
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
        @Volatile private var full = false

        fun close() {
            active = false
            process?.destroy()
        }

        private fun report(access: LogAccess) {
            dispatchAccess {
                if (session === this) onAccess(access)
            }
        }

        fun run() {
            if (!active) return
            report(LogAccess.WAITING)
            val proc = try {
                ProcessBuilder("logcat", "-v", "threadtime,uid", "-T", "1").redirectErrorStream(true).start()
            } catch (e: IOException) {
                active = false
                report(LogAccess.OFF)
                return
            }
            process = proc
            if (!active) {
                proc.destroy()
                proc.waitFor()
                return
            }
            startProbe()

            val myPid = android.os.Process.myPid()
            var ownProbes = 0
            var result = LogAccess.OFF
            try {
                val reader = proc.inputStream.bufferedReader()
                while (active) {
                    val line = reader.readLine() ?: break
                    val match = LINE.matchEntire(line) ?: continue
                    val timestamp = match.groupValues[1]
                    val uid = match.groupValues[2]
                    val pid = match.groupValues[3].toIntOrNull() ?: continue
                    val tag = match.groupValues[4]
                    val message = match.groupValues[5]
                    if (pid != myPid) {
                        if (!full) {
                            full = true
                            report(LogAccess.FULL)
                        }
                        synchronized(this@LogReader) {
                            if (active) onLine(timestamp, uid, tag, message)
                        }
                    } else if (!full && tag == TAG && message == PROBE && ++ownProbes >= 2) {
                        // Our own probes arrive but nothing from other apps: access was declined.
                        result = LogAccess.LIMITED
                        break
                    }
                }
            } catch (_: IOException) {
                // EOF and I/O failures both end this session; explicit stops suppress the report.
            }
            proc.destroy()
            proc.waitFor()
            val endedOnItsOwn = active
            active = false
            if (endedOnItsOwn) report(result)
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
        val LINE = Regex("""^(\d\d-\d\d\s+[\d:.]+)\s+(\S+)\s+(\d+)\s+\d+\s+[VDIWEFA]\s+(.*?)\s*: (.*)$""")
    }
}
