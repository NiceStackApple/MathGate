package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicBoolean

class ScreenTimeService : Service() {

    private lateinit var prefs: AppPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var isScreenOn = true
    private val isServiceRunning = AtomicBoolean(false)

    companion object {
        private const val TAG = "ScreenTimeService"
        private const val CHANNEL_ID = "MathGateChannel"
        private const val NOTIFICATION_ID = 8472
        
        const val ACTION_STATE_CHANGED = "com.example.MATH_GATE_STATE_CHANGED"
        const val EXTRA_REMAINING_MINUTES = "extra_remaining_minutes"

        fun start(context: Context) {
            val intent = Intent(context, ScreenTimeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenTimeService::class.java)
            context.stopService(intent)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    Log.d(TAG, "Screen is ON. Monitoring active.")
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    Log.d(TAG, "Screen is OFF. Monitoring paused.")
                }
            }
        }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (isScreenOn) {
                decrementQuota()
            }
            // If in simulation mode, tick much faster (e.g. every 2 seconds instead of 60 seconds)
            val delay = if (prefs.isSimulationMode) 2000L else 60000L
            handler.postDelayed(this, delay)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        createNotificationChannel()
        
        // Register for Screen On/Off broadcasts
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isServiceRunning.getAndSet(true)) {
            Log.d(TAG, "Starting ScreenTimeService")
            startForeground(NOTIFICATION_ID, buildNotification())
            
            // Perform daily reset checks on service start
            prefs.checkAndResetDailyQuota()
            
            // Start the time-tracking loop
            handler.post(tickRunnable)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning.set(false)
        handler.removeCallbacks(tickRunnable)
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister screen status receiver: ${e.message}")
        }
        Log.d(TAG, "ScreenTimeService stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun decrementQuota() {
        // Daily reset check
        val didReset = prefs.checkAndResetDailyQuota()
        if (didReset) {
            notifyStateChanged()
            updateNotification()
            return
        }

        val currentRemaining = prefs.remainingMinutes
        if (currentRemaining > 0) {
            val nextRemaining = currentRemaining - 1
            prefs.remainingMinutes = nextRemaining
            Log.d(TAG, "Remaining screen time ticked: $nextRemaining minutes left")
            
            // Fire alerts at 30 minutes, 10 minutes
            if (nextRemaining == 30 || nextRemaining == 10) {
                showSimpleAlertNotification("Math Gate", "Sisa waktu HP kamu tinggal $nextRemaining menit!")
            }

            // Trigger Lock screen overlay when screen time reaches 0
            if (nextRemaining <= 0) {
                triggerGateLock()
            }

            notifyStateChanged()
            updateNotification()
        } else {
            // Already expired, ensure Gate Screen is triggered
            triggerGateLock()
        }
    }

    private fun triggerGateLock() {
        Log.w(TAG, "Quota EXPIRED! Launching Gate Screen...")
        val intent = Intent(this, GateActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun notifyStateChanged() {
        val intent = Intent(ACTION_STATE_CHANGED).apply {
            putExtra(EXTRA_REMAINING_MINUTES, prefs.remainingMinutes)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Layanan Sistem"
            val descriptionText = "Menampilkan status dan sisa waktu screen time harian"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val remaining = prefs.remainingMinutes
        val text = "⏱ Sisa waktu: $remaining menit"
        
        val intent = Intent(this, GateActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val smallIconResId = android.R.drawable.ic_dialog_info

        val displayTitle = "Sistem Android"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(displayTitle)
            .setContentText(text)
            .setSmallIcon(smallIconResId)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun showSimpleAlertNotification(title: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alertChannelId = "MathGateAlertChannel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertChannel = NotificationChannel(
                alertChannelId,
                "Notifikasi Sistem",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(alertChannel)
        }

        val intent = Intent(this, GateActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 
            1, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val smallIconResId = android.R.drawable.ic_dialog_info

        val displayTitle = "Sistem Android"

        val notification = NotificationCompat.Builder(this, alertChannelId)
            .setContentTitle(displayTitle)
            .setContentText(message)
            .setSmallIcon(smallIconResId)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }
}
