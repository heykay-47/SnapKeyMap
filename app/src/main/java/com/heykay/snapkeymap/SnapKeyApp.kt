package com.heykay.snapkeymap

import android.app.Application

class SnapKeyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SnapEngine.init(this)
    }
}
