package com.iitg.autologin

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        LogStore.init(this)
        LogStore.log("App", "process started")
    }
}
