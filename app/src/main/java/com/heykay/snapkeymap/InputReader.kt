package com.heykay.snapkeymap

import android.content.Context
import android.os.SystemClock
import java.util.concurrent.Executors

/** Shell-only input stream. A failed reader leaves log detection running. */
class InputReader(
    private val context: Context,
    private val onEvent: (InputKeyEvent, Long) -> Unit,
    private val onLive: (Boolean) -> Unit,
    private val dispatch: (() -> Unit) -> Unit,
) {
    @Volatile private var session: Session? = null
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "snapkey-input").apply { isDaemon = true }
    }

    @Synchronized
    fun start() {
        if (session?.active == true || !canUseShizuku()) return
        val next = Session()
        session = next
        worker.execute { next.run() }
    }

    @Synchronized
    fun stop() {
        session?.close()
        session = null
        onLive(false)
    }

    private inner class Session {
        @Volatile var active = true
            private set
        private val stream = ShizukuLogStream(context, ShizukuLogService.INPUT)

        fun close() {
            active = false
            stream.close()
        }

        fun run() {
            var live = false
            try {
                if (!active) return
                stream.open().bufferedReader().use { reader ->
                    while (active) {
                        val line = reader.readLine() ?: break
                        val event = parseInputKeyEvent(line) ?: continue
                        val time = SystemClock.elapsedRealtime()
                        dispatch {
                            if (session === this && active) {
                                // Opening a pipe alone does not prove getevent can read devices.
                                if (!live) {
                                    live = true
                                    onLive(true)
                                }
                                onEvent(event, time)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Missing device access and dead binders both fall back to logs.
            } finally {
                stream.close()
                val endedOnItsOwn = active
                active = false
                if (endedOnItsOwn) dispatch { if (session === this) onLive(false) }
            }
        }
    }
}
