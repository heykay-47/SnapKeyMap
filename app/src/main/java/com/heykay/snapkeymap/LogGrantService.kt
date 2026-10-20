package com.heykay.snapkeymap

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/** Runs only the one-time grant in Shizuku's shell/root process. */
class LogGrantService : Binder() {
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == 16777115) exitProcess(0) // Shizuku's UserService destroy transaction.
        if (code != IBinder.FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags)
        data.enforceInterface(DESCRIPTOR)
        val error = try {
            val process = ProcessBuilder(
                "pm", "grant", "com.heykay.snapkeymap", "android.permission.READ_LOGS",
            ).redirectErrorStream(true).start()
            try {
                when {
                    !process.waitFor(10, TimeUnit.SECONDS) -> "The grant command timed out."
                    process.exitValue() != 0 -> "pm grant failed (exit ${process.exitValue()})."
                    else -> null
                }
            } finally {
                process.destroy()
            }
        } catch (_: Exception) {
            "Shizuku couldn't run the grant command."
        }
        reply?.writeNoException()
        reply?.writeString(error)
        return true
    }

    companion object {
        const val DESCRIPTOR = "com.heykay.snapkeymap.LogGrantService"
    }
}

internal suspend fun grantLogAccessWithShizuku(context: Context) {
    val args = Shizuku.UserServiceArgs(ComponentName(context, LogGrantService::class.java))
        .processNameSuffix("log-grant")
        .daemon(false)
    var connection: ServiceConnection? = null
    try {
        val binder = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<IBinder> { continuation ->
                val conn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, service: IBinder) {
                        if (continuation.isActive) continuation.resume(service)
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(IllegalStateException("Shizuku disconnected."))
                        }
                    }
                }
                connection = conn
                try {
                    Shizuku.bindUserService(args, conn)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        } ?: error("Shizuku's grant service didn't start.")
        withContext(Dispatchers.IO) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(LogGrantService.DESCRIPTOR)
                check(binder.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0)) {
                    "Shizuku couldn't run the grant command."
                }
                reply.readException()
                reply.readString()?.let { error(it) }
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    } finally {
        runCatching { Shizuku.unbindUserService(args, connection, true) }
    }
}
