package com.example

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class MathGateAccessibilityService : AccessibilityService() {

    private lateinit var prefs: AppPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (prefs.isSetupCompleted && !prefs.isSleepMode && prefs.remainingMinutes <= 0) {
            val isStateChanged = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            if (isStateChanged) {
                val packageName = event.packageName?.toString() ?: ""
                if (packageName.isNotEmpty() && packageName != this.packageName) {
                    Log.d("MathGateAccessibility", "Force returning to GateActivity. Attempted package: $packageName")
                    val intent = Intent(this, GateActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(intent)
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d("MathGateAccessibility", "Accessibility Service Interrupted")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (prefs.isSetupCompleted && !prefs.isSleepMode && prefs.remainingMinutes <= 0) {
            val keyCode = event.keyCode
            val action = event.action
            
            Log.d("MathGateAccessibility", "Lock Active. Key Event Intercepted: $keyCode, Action: $action")
            
            if (keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_HOME ||
                keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
                
                if (action == KeyEvent.ACTION_DOWN) {
                    // Force the Gate Screen back on top if child attempts to escape
                    val intent = Intent(this, GateActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(intent)
                }
                return true // Consume the key event: blocks system navigation
            }
        }
        return super.onKeyEvent(event)
    }
}
