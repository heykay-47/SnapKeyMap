package com.heykay.snapkeymap

internal data class TeachingHandle(val id: Long)

internal class TeachingDelivery {
    data class Stamp(val epoch: Long, val handle: TeachingHandle?)

    @Volatile var stamp = Stamp(0, null)
        private set

    fun advance(handle: TeachingHandle?) {
        stamp = Stamp(stamp.epoch + 1, handle)
    }

    fun deliver(receipt: Stamp, action: () -> Unit) {
        if (receipt == stamp) action()
    }
}
