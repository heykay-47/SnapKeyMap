package com.heykay.snapkeymap

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

class ShizukuLogService : Binder() {
    private val processes = java.util.concurrent.ConcurrentHashMap<Int, Process>()

    init {
        Runtime.getRuntime().addShutdownHook(Thread { processes.values.forEach { it.destroy() } })
    }

    @Synchronized
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == 16777115) {
            processes.values.forEach { it.destroy() }
            exitProcess(0)
        }
        if (code == GRANT_DND_TRANSACTION) {
            data.enforceInterface(DESCRIPTOR)
            val result = runCatching {
                val proc = ProcessBuilder("cmd", "notification", "allow_dnd", "com.heykay.snapkeymap")
                    .redirectErrorStream(true).start()
                try {
                    if (!proc.waitFor(10, TimeUnit.SECONDS)) throw IOException("Do Not Disturb grant timed out")
                    val output = proc.inputStream.bufferedReader().use { it.readText().take(16_384) }
                    ShizukuGrantResult(proc.exitValue(), output)
                } finally {
                    proc.destroy()
                }
            }.getOrElse { ShizukuGrantResult(-1, it.message ?: "Couldn't allow Do Not Disturb access") }
            reply?.writeNoException()
            reply?.writeInt(result.exitCode)
            reply?.writeString(result.output)
            return true
        }
        if (code != LOGCAT && code != INPUT) return super.onTransact(code, data, reply, flags)
        data.enforceInterface(DESCRIPTOR)
        processes.remove(code)?.destroy()
        val pipe = ParcelFileDescriptor.createPipe()
        try {
            val command = if (code == INPUT) listOf("getevent", "-lt")
                else listOf("logcat", "-v", "threadtime,uid", "-T", "1")
            val proc = ProcessBuilder(command)
                .redirectErrorStream(true).start()
            processes[code] = proc
            thread(isDaemon = true, name = "snapkey-shell-$code") {
                try {
                    ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                        proc.inputStream.use { it.copyTo(output) }
                    }
                } catch (_: IOException) {
                    // Closing the app's read end breaks the pipe and stops its shell reader too.
                } finally {
                    proc.destroy()
                    proc.waitFor()
                    processes.remove(code, proc)
                }
            }
            reply?.writeNoException()
            pipe[0].writeToParcel(reply ?: error("Missing reply"), 0)
        } catch (e: Exception) {
            processes.remove(code)?.destroy()
            pipe[1].close()
            throw e
        } finally {
            pipe[0].close()
        }
        return true
    }

    companion object {
        const val DESCRIPTOR = "com.heykay.snapkeymap.ShizukuLogService"
        const val LOGCAT = IBinder.FIRST_CALL_TRANSACTION
        const val INPUT = IBinder.FIRST_CALL_TRANSACTION + 1
        const val GRANT_DND_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION + 2
    }
}

internal fun canUseShizuku(): Boolean = runCatching {
    Shizuku.pingBinder() && !Shizuku.isPreV11() &&
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
}.getOrDefault(false)

internal data class ShizukuGrantResult(val exitCode: Int, val output: String)

internal suspend fun grantDndAccessWithShizuku(context: Context): ShizukuGrantResult = withContext(Dispatchers.Main) {
    check(canUseShizuku()) { "Shizuku isn't running or SnapKey Map isn't allowed in it" }
    // A distinct tag, not just a process name, keeps unbinding from stopping the log reader.
    val args = Shizuku.UserServiceArgs(ComponentName(context, ShizukuLogService::class.java))
        .tag("dnd-grant")
        .processNameSuffix("dnd-grant")
        .daemon(false)
    val connected = CompletableDeferred<IBinder>()
    val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            connected.complete(service)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            connected.completeExceptionally(IOException("Shizuku disconnected"))
        }
    }
    try {
        Shizuku.bindUserService(args, connection)
        val service = withTimeoutOrNull(15_000) { connected.await() }
            ?: throw IOException("Shizuku connection timed out")
        withContext(Dispatchers.IO) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(ShizukuLogService.DESCRIPTOR)
                if (!service.transact(ShizukuLogService.GRANT_DND_TRANSACTION, data, reply, 0)) {
                    throw IOException("Shizuku couldn't allow Do Not Disturb access")
                }
                reply.readException()
                ShizukuGrantResult(reply.readInt(), reply.readString().orEmpty())
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    } finally {
        withContext(NonCancellable + Dispatchers.Main) {
            runCatching { Shizuku.unbindUserService(args, connection, true) }
            runCatching { Shizuku.unbindUserService(args, connection, false) }
        }
    }
}

internal class ShizukuLogStream(
    context: Context,
    private val transaction: Int = ShizukuLogService.LOGCAT,
) : Closeable {
    private val main = Handler(Looper.getMainLooper())
    private val args = Shizuku.UserServiceArgs(ComponentName(context, ShizukuLogService::class.java))
        // Removing one user service must not kill the other reader.
        .tag(if (transaction == ShizukuLogService.INPUT) "input-reader" else "log-reader")
        .processNameSuffix(if (transaction == ShizukuLogService.INPUT) "input-reader" else "log-reader")
        .version(2)
        .daemon(false)
    private val connected = CountDownLatch(1)
    @Volatile private var closed = false
    private var binder: IBinder? = null
    private var input: InputStream? = null
    private val death = IBinder.DeathRecipient { close() }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            synchronized(this@ShizukuLogStream) {
                if (!closed) binder = service
                connected.countDown()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) = close()
    }

    fun open(): InputStream {
        main.post {
            synchronized(this) {
                if (!closed) {
                    try {
                        Shizuku.bindUserService(args, connection)
                    } catch (_: Exception) {
                        connected.countDown()
                    }
                }
            }
        }
        if (!connected.await(15, TimeUnit.SECONDS)) throw IOException("Shizuku log reader timed out")
        val service = synchronized(this) {
            if (closed) throw IOException("Shizuku disconnected")
            (binder ?: throw IOException("Shizuku log reader unavailable")).also { it.linkToDeath(death, 0) }
        }
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(ShizukuLogService.DESCRIPTOR)
            if (!service.transact(transaction, data, reply, 0)) {
                throw IOException("Shizuku couldn't start reader")
            }
            reply.readException()
            val stream = ParcelFileDescriptor.AutoCloseInputStream(
                ParcelFileDescriptor.CREATOR.createFromParcel(reply),
            )
            synchronized(this) {
                if (closed) {
                    stream.close()
                    throw IOException("Shizuku disconnected")
                }
                input = stream
            }
            return stream
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        connected.countDown()
        runCatching { binder?.unlinkToDeath(death, 0) }
        // Shizuku's connection collection is main-thread-owned, including failed/pending binds.
        main.post {
            runCatching { Shizuku.unbindUserService(args, connection, true) }
            runCatching { Shizuku.unbindUserService(args, connection, false) }
        }
        runCatching { input?.close() }
        input = null
        binder = null
    }
}
