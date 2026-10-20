package com.heykay.snapkeymap

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import rikka.shizuku.Shizuku

class ShizukuLogService : Binder() {
    @Volatile private var process: Process? = null

    init {
        Runtime.getRuntime().addShutdownHook(Thread { process?.destroy() })
    }

    @Synchronized
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == 16777115) {
            process?.destroy()
            exitProcess(0)
        }
        if (code != IBinder.FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags)
        data.enforceInterface(DESCRIPTOR)
        check(process == null)
        val pipe = ParcelFileDescriptor.createPipe()
        try {
            val proc = ProcessBuilder("logcat", "-v", "threadtime,uid", "-T", "1")
                .redirectErrorStream(true).start()
            process = proc
            thread(isDaemon = true, name = "snapkey-shell-logcat") {
                try {
                    ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                        proc.inputStream.use { it.copyTo(output) }
                    }
                } catch (_: IOException) {
                    // Closing the app's read end breaks the pipe and stops its shell reader too.
                } finally {
                    proc.destroy()
                    proc.waitFor()
                }
            }
            reply?.writeNoException()
            pipe[0].writeToParcel(reply ?: error("Missing reply"), 0)
        } catch (e: Exception) {
            process?.destroy()
            pipe[1].close()
            throw e
        } finally {
            pipe[0].close()
        }
        return true
    }

    companion object {
        const val DESCRIPTOR = "com.heykay.snapkeymap.ShizukuLogService"
    }
}

internal fun canUseShizuku(): Boolean = runCatching {
    Shizuku.pingBinder() && !Shizuku.isPreV11() &&
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
}.getOrDefault(false)

internal class ShizukuLogStream(context: Context) : Closeable {
    private val args = Shizuku.UserServiceArgs(ComponentName(context, ShizukuLogService::class.java))
        .processNameSuffix("log-reader")
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
        synchronized(this) {
            check(!closed)
            Shizuku.bindUserService(args, connection)
        }
        if (!connected.await(15, TimeUnit.SECONDS)) throw IOException("Shizuku log reader timed out")
        synchronized(this) {
            if (closed) throw IOException("Shizuku disconnected")
            val service = binder ?: throw IOException("Shizuku log reader unavailable")
            service.linkToDeath(death, 0)
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(ShizukuLogService.DESCRIPTOR)
                if (!service.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0)) {
                    throw IOException("Shizuku couldn't start logcat")
                }
                reply.readException()
                return ParcelFileDescriptor.AutoCloseInputStream(
                    ParcelFileDescriptor.CREATOR.createFromParcel(reply),
                ).also { input = it }
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        connected.countDown()
        runCatching { binder?.unlinkToDeath(death, 0) }
        runCatching { Shizuku.unbindUserService(args, connection, true) }
        runCatching { input?.close() }
        input = null
        binder = null
    }
}
