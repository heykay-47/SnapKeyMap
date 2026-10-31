package com.heykay.snapkeymap

internal data class TeachingHandle(val id: Long)

internal class TeachingDelivery {
    data class Stamp(val epoch: Long, val handle: TeachingHandle?)

    @Volatile var stamp = Stamp(0, null)
        private set
    private var observeFromEpoch = 0L

    fun advance(handle: TeachingHandle?) {
        stamp = Stamp(stamp.epoch + 1, handle)
    }

    fun discardPending() {
        advance(stamp.handle)
        observeFromEpoch = stamp.epoch
    }

    fun deliver(receipt: Stamp, onStale: () -> Unit = {}, action: () -> Unit) {
        if (receipt == stamp) action()
        else if (receipt.epoch >= observeFromEpoch) onStale()
    }
}
