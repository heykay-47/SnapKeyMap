package com.heykay.snapkeymap

import android.util.Log
import java.io.IOException
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
    private val onLine: (timestamp: String, tag: String, message: String) -> Unit,
    private val onAccess: (LogAccess) -> Unit,
) {
    @Volatile private var session: Session? = null

    fun start() {
        stop()
        val next = Session()
        session = next
        thread(isDaemon = true, name = "snapkey-logcat") { next.run() }
    }

    fun stop() {
        session?.close()
        session = null
    }

    private inner class Session {
        @Volatile private var active = true
        @Volatile private var process: Process? = null
        @Volatile private var full = false

        fun close() {
            active = false
            process?.destroy()
        }

        private fun report(access: LogAccess) {
            if (active) onAccess(access)
        }

        fun run() {
            report(LogAccess.WAITING)
            val proc = try {
                ProcessBuilder("logcat", "-v", "threadtime", "-T", "1").redirectErrorStream(true).start()
            } catch (e: IOException) {
                report(LogAccess.OFF)
                return
            }
            process = proc
            if (!active) {
                proc.destroy()
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
                    val pid = match.groupValues[2].toIntOrNull() ?: continue
                    val tag = match.groupValues[3]
                    val message = match.groupValues[4]
                    if (pid != myPid) {
                        if (!full) {
                            full = true
                            report(LogAccess.FULL)
                        }
                        onLine(timestamp, tag, message)
                    } else if (!full && tag == TAG && message == PROBE && ++ownProbes >= 2) {
                        // Our own probes arrive but nothing from other apps: access was declined.
                        result = LogAccess.LIMITED
                        break
                    }
                }
            } catch (_: IOException) {
                // The process was destroyed.
            }
            proc.destroy()
            report(result)
            active = false
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

        // threadtime format: "10-18 04:27:44.123  1234  5678 D Tag     : message"
        val LINE = Regex("""^(\d\d-\d\d\s+[\d:.]+)\s+(\d+)\s+\d+\s+[VDIWEFA]\s+(.*?)\s*: (.*)$""")
    }
}
