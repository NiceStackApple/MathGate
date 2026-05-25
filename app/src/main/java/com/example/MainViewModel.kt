package com.example

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class AppScreen {
    object Splash : AppScreen()
    object SetupWizard : AppScreen()
    object EnterPin : AppScreen()
    object SettingsDashboard : AppScreen()
}

class MainViewModel(context: Context) : ViewModel() {
    private val prefs = AppPreferences(context)

    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.Splash)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _remainingTimeMinutes = MutableStateFlow(prefs.remainingMinutes)
    val remainingTimeMinutes: StateFlow<Int> = _remainingTimeMinutes.asStateFlow()

    private val _isSimulationMode = MutableStateFlow(prefs.isSimulationMode)
    val isSimulationMode: StateFlow<Boolean> = _isSimulationMode.asStateFlow()

    init {
        checkInitialScreenState()
    }

    fun checkInitialScreenState() {
        if (!prefs.isSetupCompleted) {
            _currentScreen.value = AppScreen.SetupWizard
        } else {
            _currentScreen.value = AppScreen.EnterPin
        }
        refreshTimeState()
    }

    fun refreshTimeState() {
        _remainingTimeMinutes.value = prefs.remainingMinutes
        _isSimulationMode.value = prefs.isSimulationMode
    }

    // PIN Hashing and setup actions
    fun completeWizardSetup(
        pin: String,
        quotaMinutes: Int,
        diff: String,
        rewMinutes: Int,
        minQ: Int,
        appName: String
    ): Boolean {
        if (pin.length < 4 || pin.length > 6 || pin.toIntOrNull() == null) {
            return false
        }
        prefs.savePin(pin)
        prefs.dailyQuota = quotaMinutes
        prefs.remainingMinutes = quotaMinutes
        prefs.difficulty = diff
        prefs.rewardPerQuestion = rewMinutes
        prefs.minQuestions = minQ
        prefs.appDisplayName = appName
        prefs.isSetupCompleted = true
        
        _remainingTimeMinutes.value = quotaMinutes
        _currentScreen.value = AppScreen.SettingsDashboard
        return true
    }

    fun unlockDashboardWithPin(pin: String): Boolean {
        if (prefs.verifyPin(pin)) {
            _currentScreen.value = AppScreen.SettingsDashboard
            return true
        }
        return false
    }

    fun updateSettings(
        quotaMinutes: Int,
        diff: String,
        rewMinutes: Int,
        minQ: Int,
        appName: String,
        iconIndex: Int
    ) {
        prefs.dailyQuota = quotaMinutes
        prefs.difficulty = diff
        prefs.rewardPerQuestion = rewMinutes
        prefs.minQuestions = minQ
        prefs.appDisplayName = appName
        prefs.appIconIndex = iconIndex
    }

    fun unlockDashboard() {
        _currentScreen.value = AppScreen.SettingsDashboard
    }

    fun adjustRemainingMinutes(delta: Int) {
        val nextVal = (prefs.remainingMinutes + delta).coerceAtLeast(0)
        prefs.remainingMinutes = nextVal
        _remainingTimeMinutes.value = nextVal
    }

    fun toggleSimulationMode() {
        val next = !prefs.isSimulationMode
        prefs.isSimulationMode = next
        _isSimulationMode.value = next
    }

    fun logoutToPinScreen() {
        _currentScreen.value = AppScreen.EnterPin
    }

    fun resetApp(context: Context) {
        // Full hard reset
        prefs.isSetupCompleted = false
        prefs.pinHash = null
        prefs.dailyQuota = 120
        prefs.remainingMinutes = 120
        prefs.difficulty = "easy"
        prefs.rewardPerQuestion = 10
        prefs.minQuestions = 1
        prefs.appDisplayName = "Math Gate"
        prefs.isSimulationMode = false
        
        ScreenTimeService.stop(context)
        _currentScreen.value = AppScreen.SetupWizard
    }
}
