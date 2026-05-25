package com.example

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("MathGatePrefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SETUP_COMPLETED = "is_setup_completed"
        private const val KEY_PIN_HASH = "parent_pin_hash"
        private const val KEY_DAILY_QUOTA = "daily_quota_minutes"
        private const val KEY_DIFFICULTY = "difficulty_level"
        private const val KEY_REWARD_PER_QUESTION = "reward_per_question"
        private const val KEY_MIN_QUESTIONS = "min_questions"
        private const val KEY_REMAINING_MINUTES = "remaining_today_minutes"
        private const val KEY_LAST_RESET_DATE = "last_reset_date"
        private const val KEY_APP_DISPLAY_NAME = "app_display_name"
        private const val KEY_APP_ICON_INDEX = "app_icon_index"
        
        // Custom keys for developer-friendly simulation mode
        private const val KEY_SIMULATION_MODE = "simulation_mode"
        private const val KEY_LAST_TICK_TIME = "last_tick_time"
    }

    var isSetupCompleted: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETED, value).apply()

    var pinHash: String?
        get() = prefs.getString(KEY_PIN_HASH, null)
        set(value) = prefs.edit().putString(KEY_PIN_HASH, value).apply()

    var dailyQuota: Int
        get() = prefs.getInt(KEY_DAILY_QUOTA, 120)
        set(value) = prefs.edit().putInt(KEY_DAILY_QUOTA, value).apply()

    var difficulty: String
        get() = prefs.getString(KEY_DIFFICULTY, "easy") ?: "easy"
        set(value) = prefs.edit().putString(KEY_DIFFICULTY, value).apply()

    var rewardPerQuestion: Int
        get() = prefs.getInt(KEY_REWARD_PER_QUESTION, 10)
        set(value) = prefs.edit().putInt(KEY_REWARD_PER_QUESTION, value).apply()

    var minQuestions: Int
        get() = prefs.getInt(KEY_MIN_QUESTIONS, 1)
        set(value) = prefs.edit().putInt(KEY_MIN_QUESTIONS, value).apply()

    var remainingMinutes: Int
        get() = prefs.getInt(KEY_REMAINING_MINUTES, 120)
        set(value) = prefs.edit().putInt(KEY_REMAINING_MINUTES, value).apply()

    var lastResetDate: String?
        get() = prefs.getString(KEY_LAST_RESET_DATE, null)
        set(value) = prefs.edit().putString(KEY_LAST_RESET_DATE, value).apply()

    var appDisplayName: String
        get() = prefs.getString(KEY_APP_DISPLAY_NAME, "Math Gate") ?: "Math Gate"
        set(value) = prefs.edit().putString(KEY_APP_DISPLAY_NAME, value).apply()

    var appIconIndex: Int
        get() = prefs.getInt(KEY_APP_ICON_INDEX, 0)
        set(value) = prefs.edit().putInt(KEY_APP_ICON_INDEX, value).apply()

    var isSimulationMode: Boolean
        get() = prefs.getBoolean(KEY_SIMULATION_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_SIMULATION_MODE, value).apply()

    var pinLength: Int
        get() = prefs.getInt("parent_pin_length", 4)
        set(value) = prefs.edit().putInt("parent_pin_length", value).apply()

    fun savePin(pin: String) {
        val hashed = hashString(pin)
        pinHash = hashed
        pinLength = pin.length
    }

    fun verifyPin(pin: String): Boolean {
        val hashed = hashString(pin)
        return hashed == pinHash
    }

    fun hashString(input: String): String {
        val bytes = input.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Calendar.getInstance().time)
    }

    fun checkAndResetDailyQuota(): Boolean {
        val today = getTodayDateString()
        if (lastResetDate != today) {
            remainingMinutes = dailyQuota
            lastResetDate = today
            return true
        }
        return false
    }
}
