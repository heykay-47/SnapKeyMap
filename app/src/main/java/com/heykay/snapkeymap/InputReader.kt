package com.heykay.snapkeymap

import android.content.Context
import android.os.SystemClock
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors

/** Keep touchscreen/sync traffic in the shell process, without pinning a device during learning. */
internal fun copyInputKeyEvents(input: InputStream, output: OutputStream) {
    val writer = output.bufferedWriter()
    input.bufferedReader().use { reader ->
        while (true) {
            val line = reader.readLine() ?: break
            if (!line.contains(" EV_KEY ")) continue
            writer.write(line)
            writer.newLine()
            // A buffered key edge must not wait for another press to reach the app.
            writer.flush()
        }
    }
}

/** Shell-only input stream. A failed reader leaves log detection running. */
internal class InputReader(
    private val context: Context,
    private val onEvent: (InputKeyEvent, Long, TeachingDelivery.Stamp) -> Unit,
    private val onLive: (Boolean) -> Unit,
    private val receipt: () -> TeachingDelivery.Stamp,
    private val dispatch: (() -> Unit) -> Unit,
) {
    @Volatile private var session: Session? = null
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "snapkey-input").apply { isDaemon = true }
    }

    @Synchronized
    fun start() {
        if (session?.active == true || !canUseShizuku()) return
        if (session != null) onLive(false)
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
                        val stamp = receipt()
                        dispatch {
                            if (session === this && active) {
                                // Opening a pipe alone does not prove getevent can read devices.
                                if (!live) {
                                    live = true
                                    onLive(true)
                                }
                                onEvent(event, time, stamp)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                stream.close()
                val endedOnItsOwn = active
                active = false
                if (endedOnItsOwn) dispatch { if (session === this) onLive(false) }
            }
        }
    }
}
