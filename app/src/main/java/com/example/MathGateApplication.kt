package com.example

import android.app.Application
import android.util.Log

class MathGateApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("MathGateApplication", "Application Initialized.")
    }
}
