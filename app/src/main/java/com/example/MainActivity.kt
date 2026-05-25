package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var prefs: AppPreferences
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = AppPreferences(this)
        viewModel = MainViewModel(this)

        // Automatically request notification permissions on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // Automatically start service when app opens if setup is already complete
        if (prefs.isSetupCompleted) {
            ScreenTimeService.start(this)
        }

        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A) // Rich deep slate-900 canvas
                ) {
                    val currentScreen by viewModel.currentScreen.collectAsState()
                    val remainingTimeMinutes by viewModel.remainingTimeMinutes.collectAsState()
                    val isSimulationMode by viewModel.isSimulationMode.collectAsState()

                    // Periodically refresh states
                    LaunchedEffect(Unit) {
                        viewModel.refreshTimeState()
                    }

                    when (currentScreen) {
                        AppScreen.Splash -> SplashScreen(
                            onGetStarted = { viewModel.checkInitialScreenState() }
                        )
                        AppScreen.SetupWizard -> SetupWizardScreen(
                            onComplete = { pin, quota, diff, reward, minQ, appName ->
                                val success = viewModel.completeWizardSetup(pin, quota, diff, reward, minQ, appName)
                                if (success) {
                                    ScreenTimeService.start(this@MainActivity)
                                    Toast.makeText(this@MainActivity, "Konfigurasi Math Gate Berhasil!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(this@MainActivity, "Data PIN/Setelan tidak valid!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        AppScreen.EnterPin -> EnterPinScreen(
                            prefs = prefs,
                            onValidated = {
                                viewModel.refreshTimeState()
                                viewModel.unlockDashboard()
                            }
                        )
                        AppScreen.SettingsDashboard -> SettingsDashboardScreen(
                            prefs = prefs,
                            remainingTimeMinutes = remainingTimeMinutes,
                            isSimulationMode = isSimulationMode,
                            onTimeAdjusted = { delta ->
                                viewModel.adjustRemainingMinutes(delta)
                            },
                            onToggleSimulation = {
                                viewModel.toggleSimulationMode()
                            },
                            onSaveSettings = { quota, diff, reward, minQ, appName, selectedIconIdx ->
                                val oldIconIndex = prefs.appIconIndex
                                val iconChanged = oldIconIndex != selectedIconIdx
                                viewModel.updateSettings(quota, diff, reward, minQ, appName, selectedIconIdx)
                                if (iconChanged) {
                                    changeAppDisguise(this@MainActivity, selectedIconIdx)
                                    Toast.makeText(this@MainActivity, "Pengaturan disamarkan! Aplikasi akan ditutup untuk menerapkan nama dan ikon baru...", Toast.LENGTH_LONG).show()
                                    window.decorView.postDelayed({
                                        finishAffinity()
                                    }, 1200)
                                } else {
                                    Toast.makeText(this@MainActivity, "Pengaturan berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onLockScreen = {
                                viewModel.logoutToPinScreen()
                            },
                            onResetApp = {
                                viewModel.resetApp(this@MainActivity)
                                Toast.makeText(this@MainActivity, "Aplikasi berhasil di-reset!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.refreshTimeState()
        }
    }
}

// Function to dynamically update Launcher Icon and Name using Activity Aliases
fun changeAppDisguise(context: Context, chosenIndex: Int) {
    val pm = context.packageManager
    val packageName = context.packageName
    
    val prefixes = listOf(packageName, "com.example")
    val suffixList = listOf(
        "MainActivityDefault",
        "MainActivityCalculator",
        "MainActivityWeather",
        "MainActivityNotes"
    )

    try {
        var lastEnabledComponent: ComponentName? = null
        for (prefix in prefixes) {
            for (i in suffixList.indices) {
                val compNameStr = "$prefix.${suffixList[i]}"
                val componentName = ComponentName(packageName, compNameStr)
                val state = if (i == chosenIndex) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                
                try {
                    val currentState = pm.getComponentEnabledSetting(componentName)
                    if (currentState != state) {
                        pm.setComponentEnabledSetting(
                            componentName,
                            state,
                            PackageManager.DONT_KILL_APP
                        )
                    }
                } catch (e: Exception) {
                    // Ignore component-not-found for non-matching namespace
                }
            }
        }
        
        val targetName = when(chosenIndex) {
            1 -> "Kalkulator"
            2 -> "Cuaca"
            3 -> "Notes"
            else -> "Math Gate"
        }
        Log.d("MainActivity", "App disguise updated to: $targetName")
    } catch (e: Exception) {
        Log.e("MainActivity", "Failed to change app disguise: ${e.message}")
    }
}

// Custom check functions for system permissions
fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    }
    return mode == android.app.AppOpsManager.MODE_ALLOWED
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedComponentName = ComponentName(context, MathGateAccessibilityService::class.java)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServices)
    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        val enabledService = ComponentName.unflattenFromString(componentNameString)
        if (enabledService != null && enabledService == expectedComponentName) {
            return true
        }
    }
    return false
}

@Composable
fun SplashScreen(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing brand logo container
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Color(0xFF38BDF8).copy(alpha = 0.15f), CircleShape)
                .border(2.dp, Color(0xFF38BDF8), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Math Gate",
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "MATH GATE",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Kunci Layar HP Cerdas Dengan Matematika Harian",
            fontSize = 15.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("get_started_button"),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Mulai Konfigurasi",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SetupWizardScreen(
    onComplete: (pin: String, quota: Int, diff: String, reward: Int, minQ: Int, appName: String) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var step by remember { mutableStateOf(1) }

    // Step 1: PIN setup state
    var pinValue by remember { mutableStateOf("") }
    var pinConfirmValue by remember { mutableStateOf("") }

    // Step 2: Settings rule configuration states
    var dailyQuota by remember { mutableStateOf(120) }
    var difficulty by remember { mutableStateOf("easy") }
    var rewardPerQuestion by remember { mutableStateOf(10) }
    var minQuestions by remember { mutableStateOf(1) }
    var appNameSelection by remember { mutableStateOf("Math Gate") }

    // Step 3: Local checking for permissions
    var usageStatsGranted by remember { mutableStateOf(false) }
    var accessibilityActive by remember { mutableStateOf(false) }

    LaunchedEffect(step) {
        usageStatsGranted = hasUsageStatsPermission(context)
        accessibilityActive = isAccessibilityServiceEnabled(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Step header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "SETUP WIZARD",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF38BDF8),
                letterSpacing = 2.sp
            )
            Text(
                text = "Langkah $step dari 3",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Progress dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3).forEach { s ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (s == step) Color(0xFF38BDF8) else Color(0xFF334155))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Main content depending on active step
        Box(modifier = Modifier.weight(1f, fill = false)) {
            when (step) {
                1 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Buat PIN Akses Ortu",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "PIN digunakan oleh orang tua untuk membuka setelan dan menambah kuota. Anak tidak boleh tahu PIN ini.",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = pinValue,
                        onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) pinValue = it },
                        label = { Text("Masukkan PIN Ortu (4-6 angka)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("setup_pin_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF475569)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = pinConfirmValue,
                        onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) pinConfirmValue = it },
                        label = { Text("Konfirmasi Kembali PIN Ortu") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("setup_pin_confirm_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF475569)
                        )
                    )
                }
                2 -> Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "Konfigurasi Aturan Sesi",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Quota
                    Text("Kuota Screen Time Harian (Menit): $dailyQuota", fontWeight = FontWeight.SemiBold, color = Color.White)
                    Slider(
                        value = dailyQuota.toFloat(),
                        onValueChange = { dailyQuota = it.toInt() },
                        valueRange = 10f..480f,
                        steps = 46,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF38BDF8), activeTrackColor = Color(0xFF38BDF8))
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Difficulty selector
                    Text("Tingkat Kesulitan Soal:", fontWeight = FontWeight.SemiBold, color = Color.White)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("very_easy" to "Sgt Mudah", "easy" to "Mudah", "medium" to "Sedang", "hard" to "Sulit").forEach { (id, label) ->
                            val selected = difficulty == id
                            Button(
                                onClick = { difficulty = id },
                                modifier = Modifier.weight(1f),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) Color(0xFF38BDF8) else Color(0xFF1E293B),
                                    contentColor = if (selected) Color(0xFF0F172A) else Color.White
                                )
                            ) {
                                Text(label, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }

                    DifficultyPreview(difficulty, modifier = Modifier.padding(vertical = 4.dp))

                    Spacer(modifier = Modifier.height(14.dp))

                    // Reward per answer
                    Text("Bonus Menit per Soal Benar: $rewardPerQuestion Menit", fontWeight = FontWeight.SemiBold, color = Color.White)
                    Slider(
                        value = rewardPerQuestion.toFloat(),
                        onValueChange = { rewardPerQuestion = it.toInt() },
                        valueRange = 1f..60f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF10B981), activeTrackColor = Color(0xFF10B981))
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Minimum questions
                    Text("Jumlah Soal Wajib Minim: $minQuestions", fontWeight = FontWeight.SemiBold, color = Color.White)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 2, 3, 5).forEach { num ->
                            val selected = minQuestions == num
                            Button(
                                onClick = { minQuestions = num },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) Color(0xFF38BDF8) else Color(0xFF1E293B),
                                    contentColor = if (selected) Color(0xFF0F172A) else Color.White
                                )
                            ) {
                                Text(num.toString())
                            }
                        }
                    }
                }
                3 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Izin Android yang Diperlukan",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Untuk memblokir HP saat kuota harian habis, aplikasi memerlukan beberapa izin sistem.",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Usage Stats card
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (usageStatsGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = "Usage Status",
                                tint = if (usageStatsGranted) Color(0xFF10B981) else Color(0xFFF59E0B)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Akses Data Penggunaan", fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Wajib untuk menghitung screen time aktif HP anak.", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            }
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // Some older/newer devices require normal actions
                                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                            ) {
                                Text("Aktifkan", fontSize = 12.sp)
                            }
                        }
                    }

                    // Accessibility Service card
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (accessibilityActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = "Accessibility Service",
                                tint = if (accessibilityActive) Color(0xFF10B981) else Color(0xFFF59E0B)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Accessibility Service", fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Wajib untuk memblokir tombol navigasi saat terkunci.", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            }
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                            ) {
                                Text("Aktifkan", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "💡 Catatan Pengujian: Di emulator, apabila Anda tidak sempat mengaktifkan izin di atas, Anda tetap dapat menguji fungsionalitas Math Gate dengan mengetuk tombol demonstrasi atau mengaktifkan Simulasi Cepat di layar dashboard nanti!",
                        fontSize = 12.sp,
                        color = Color(0xFF38BDF8),
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Navigation actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (step > 1) {
                OutlinedButton(
                    onClick = { step-- },
                    modifier = Modifier.height(50.dp).width(120.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Kembali")
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Button(
                onClick = {
                    if (step == 1) {
                        if (pinValue.length < 4 || pinValue.length > 6) {
                            Toast.makeText(context, "PIN harus bertipe angka 4-6 digit!", Toast.LENGTH_SHORT).show()
                        } else if (pinValue != pinConfirmValue) {
                            Toast.makeText(context, "PIN Konfirmasi tidak cocok!", Toast.LENGTH_SHORT).show()
                        } else {
                            step++
                        }
                    } else if (step == 2) {
                        step++
                    } else if (step == 3) {
                        onComplete(pinValue, dailyQuota, difficulty, rewardPerQuestion, minQuestions, appNameSelection)
                    }
                },
                modifier = Modifier.height(50.dp).width(150.dp).testTag("setup_wizard_next_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color(0xFF0F172A))
            ) {
                Text(if (step == 3) "Selesai & Aktifkan" else "Lanjutkan")
            }
        }
    }
}

@Composable
fun EnterPinScreen(
    prefs: AppPreferences,
    onValidated: () -> Unit
) {
    var accumulatedPin by remember { mutableStateOf("") }
    var attemptMessage by remember { mutableStateOf<String?>(null) }
    var wrongPinCount by remember { mutableStateOf(0) }
    var cooldownTimeRemaining by remember { mutableStateOf(0L) }

    // Check lockout cooldown state
    val context = LocalContext.current
    val lockoutTimestamp = remember { mutableStateOf(context.getSharedPreferences("LockoutPrefs", Context.MODE_PRIVATE).getLong("lockout_time", 0L)) }

    fun refreshCooldown() {
        val diff = System.currentTimeMillis() - lockoutTimestamp.value
        val waitPeriod = 5 * 60 * 1000 // 5 minutes
        if (diff < waitPeriod) {
            cooldownTimeRemaining = (waitPeriod - diff) / 1000
        } else {
            cooldownTimeRemaining = 0L
        }
    }

    LaunchedEffect(Unit) {
        refreshCooldown()
        if (cooldownTimeRemaining > 0) {
            // Periodic update
            while (cooldownTimeRemaining > 0) {
                kotlinx.coroutines.delay(1000)
                refreshCooldown()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Lock screen title
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Settings Locked",
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "MASUK SETELAN ORANG TUA",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Pintu setelan dilindungi oleh PIN akses Orang Tua",
                fontSize = 13.sp,
                color = Color(0xFF94A3B8)
            )
        }

        // Passcode indicators (dots)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (cooldownTimeRemaining > 0) {
                Text(
                    text = "Salah PIN 5x! Terkunci. Tunggu ${cooldownTimeRemaining}s lagi.",
                    color = Color(0xFFEF4444),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    val activeLength = accumulatedPin.length
                    for (i in 0 until prefs.pinLength) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i < activeLength) Color(0xFF38BDF8) else Color(0xFF334155)
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        )
                    }
                }

                attemptMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = Color(0xFFEF4444),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("pin_attempt_error")
                    )
                }
            }
        }

        // Touch grid keyboard
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("C", "0", "OK")
            )

            keys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { digit ->
                        val isEnabled = cooldownTimeRemaining <= 0L
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.8f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (digit == "OK") Color(0xFF10B981).copy(alpha = if (isEnabled) 1f else 0.4f)
                                    else Color(0xFF1E293B).copy(alpha = if (isEnabled) 1f else 0.4f)
                                )
                                .clickable(enabled = isEnabled) {
                                    if (digit == "C") {
                                        accumulatedPin = ""
                                        attemptMessage = null
                                    } else if (digit == "OK") {
                                        val isValid = prefs.verifyPin(accumulatedPin)
                                        if (isValid) {
                                            wrongPinCount = 0
                                            onValidated()
                                        } else {
                                            wrongPinCount++
                                            accumulatedPin = ""
                                            if (wrongPinCount >= 5) {
                                                // Register lockout
                                                val now = System.currentTimeMillis()
                                                context.getSharedPreferences("LockoutPrefs", Context.MODE_PRIVATE)
                                                    .edit().putLong("lockout_time", now).apply()
                                                lockoutTimestamp.value = now
                                                refreshCooldown()
                                            } else {
                                                attemptMessage = "PIN Salah! Percobaan Tersisa: ${5 - wrongPinCount}"
                                            }
                                        }
                                    } else {
                                        if (accumulatedPin.length < 6) {
                                            val nextPin = accumulatedPin + digit
                                            accumulatedPin = nextPin
                                            if (prefs.verifyPin(nextPin)) {
                                                wrongPinCount = 0
                                                onValidated()
                                            }
                                        }
                                    }
                                }
                        ) {
                            Text(
                                text = digit,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (digit == "OK") Color(0xFF0F172A) else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDashboardScreen(
    prefs: AppPreferences,
    remainingTimeMinutes: Int,
    isSimulationMode: Boolean,
    onTimeAdjusted: (Int) -> Unit,
    onToggleSimulation: () -> Unit,
    onSaveSettings: (quota: Int, diff: String, reward: Int, minQ: Int, appName: String, selectedIconIdx: Int) -> Unit,
    onLockScreen: () -> Unit,
    onResetApp: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Form inputs initialized from current variables
    var dailyQuotaInput by remember { mutableStateOf(prefs.dailyQuota) }
    var difficultyInput by remember { mutableStateOf(prefs.difficulty) }
    var rewardPerQuestionInput by remember { mutableStateOf(prefs.rewardPerQuestion) }
    var minQuestionsInput by remember { mutableStateOf(prefs.minQuestions) }
    var appNameInput by remember { mutableStateOf(prefs.appDisplayName) }
    var appIconIdxInput by remember { mutableStateOf(prefs.appIconIndex) }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App title profile header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DASHBOARD ORANG TUA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8),
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Math Gate Status",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            IconButton(
                onClick = onLockScreen,
                modifier = Modifier.background(Color(0xFF1E293B), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "Logout",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Info System Notice for Accessibility / IO Cache issues after install/update
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFD97706).copy(alpha = 0.15f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "System Notice",
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(20.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Tips Sinkronisasi Layanan",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF59E0B)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Jika Anda baru meng-update / meng-install ulang aplikasi dan muncul kendala sistem (I/O cache) atau layanan tidak merespon, harap Matikan lalu Aktifkan kembali izin 'Math Gate' di Layanan Aksesibilitas HP Anda untuk membersihkan cache berkas APK lama.",
                        fontSize = 11.sp,
                        color = Color(0xFFE2E8F0),
                        lineHeight = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Timer gauge Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SISA KUOTA SCREEN TIME ANAK HARI INI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "$remainingTimeMinutes Menit",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    color = if (remainingTimeMinutes > 0) Color(0xFF10B981) else Color(0xFFEF4444)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Time adjustments row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onTimeAdjusted(-10) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.2f), contentColor = Color(0xFFF87171))
                    ) {
                        Text("-10 Mnt")
                    }
                    Button(
                        onClick = { onTimeAdjusted(10) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981).copy(alpha = 0.2f), contentColor = Color(0xFF34D399))
                    ) {
                        Text("+10 Mnt")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Developer speed simulation helper
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text("Simulasi Mode Cepat", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        Text("1 Menit berkurang setiap 2 detik", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    }
                    Switch(
                        checked = isSimulationMode,
                        onCheckedChange = { onToggleSimulation() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF38BDF8),
                            checkedTrackColor = Color(0xFF0284C7)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Trigger manual Lock Screen block (demo mode)
        Button(
            onClick = {
                // Instantly force-set remaining minutes to 0 for demo locking
                onTimeAdjusted(-remainingTimeMinutes)
                // Trigger Lock activity
                val intent = Intent(context, GateActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth().testTag("demo_lock_button"),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706), contentColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Calculate, contentDescription = "Lock Screen Test")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Uji Layar Kunci (Buka Gate)", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Editable settings sheet
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "KONFIGURASI ATURAN DETIL",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Input Daily Limit
                Text("Kuota Harian Default (Menit): $dailyQuotaInput", fontWeight = FontWeight.SemiBold, color = Color.White)
                Slider(
                    value = dailyQuotaInput.toFloat(),
                    onValueChange = { dailyQuotaInput = it.toInt() },
                    valueRange = 10f..480f,
                    steps = 46,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF38BDF8), activeTrackColor = Color(0xFF38BDF8))
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Input Difficulty Level
                Text("Kesulitan Soal:", fontWeight = FontWeight.SemiBold, color = Color.White)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("very_easy" to "Sgt Mudah", "easy" to "Mudah", "medium" to "Sedang", "hard" to "Sulit").forEach { (id, label) ->
                        val selected = difficultyInput == id
                        Button(
                            onClick = { difficultyInput = id },
                            modifier = Modifier.weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) Color(0xFF38BDF8) else Color(0xFF0F172A),
                                contentColor = if (selected) Color(0xFF0F172A) else Color.White
                            )
                        ) {
                            Text(label, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }

                DifficultyPreview(difficultyInput, modifier = Modifier.padding(vertical = 4.dp))

                Spacer(modifier = Modifier.height(14.dp))

                // Input Reward per correct equation
                Text("Bonus Waktu per Soal Benar: $rewardPerQuestionInput Menit", fontWeight = FontWeight.SemiBold, color = Color.White)
                Slider(
                    value = rewardPerQuestionInput.toFloat(),
                    onValueChange = { rewardPerQuestionInput = it.toInt() },
                    valueRange = 1f..60f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF10B981), activeTrackColor = Color(0xFF10B981))
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Input mandatory questions
                Text("Jumlah Soal Wajib Minim: $minQuestionsInput", fontWeight = FontWeight.SemiBold, color = Color.White)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 2, 3, 5).forEach { quantity ->
                        val selected = minQuestionsInput == quantity
                        Button(
                            onClick = { minQuestionsInput = quantity },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) Color(0xFF38BDF8) else Color(0xFF0F172A),
                                contentColor = if (selected) Color(0xFF0F172A) else Color.White
                            )
                        ) {
                            Text(quantity.toString())
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // APP DISGUISE PANEL
                Text(
                    text = "PENYAMARAN APLIKASI (DISGUISE)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Select disguise type
                val disguiseNames = listOf("Math Gate", "Kalkulator", "Cuaca", "Notes")
                val disguiseIcons = listOf(
                    Icons.Default.Lock,
                    Icons.Default.Calculate,
                    Icons.Default.Cloud,
                    Icons.Default.Notes
                )

                disguiseNames.forEachIndexed { idx, name ->
                    val selected = appIconIdxInput == idx
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(
                                if (selected) Color(0xFF38BDF8).copy(alpha = 0.12f) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                if (selected) Color(0xFF38BDF8) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                appIconIdxInput = idx
                                appNameInput = name
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = disguiseIcons[idx],
                            contentDescription = name,
                            tint = if (selected) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = name,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else Color(0xFF94A3B8)
                            )
                            Text(
                                text = if (idx == 0) "Tampilan standar aplikasi" else "Disamarkan sebagai $name di laci aplikasi",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Save All configurations Button
                Button(
                    onClick = {
                        onSaveSettings(
                            dailyQuotaInput,
                            difficultyInput,
                            rewardPerQuestionInput,
                            minQuestionsInput,
                            appNameInput,
                            appIconIdxInput
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("save_settings_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("SIMPAN SETELAN ORANG TUA", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Factory reset button
        TextButton(
            onClick = onResetApp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Reset Master", tint = Color(0xFFEF4444))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reset Pengaturan & Set Ulang PIN (Master)", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun DifficultyPreview(difficultyId: String, modifier: Modifier = Modifier) {
    val (desc, example) = when (difficultyId) {
        "very_easy" -> Pair(
            "Operasi dasar penjumlahan & pengurangan sederhana 1 digit (angka 1-9). Sangat cocok untuk pemula atau anak-anak.",
            "Contoh: 5 + 3 = 8 atau 9 − 4 = 5"
        )
        "medium" -> Pair(
            "Operasi perkalian menengah (2-digit dikali 1-digit) serta operasi pembagian genap bulat tanpa sisa maupun desimal.",
            "Contoh: 14 × 6 = 84 atau 96 ÷ 8 = 12"
        )
        "hard" -> Pair(
            "Operasi campuran bertingkat menggunakan tanda kurung (prioritas), perkalian, pembagian, serta penjumlahan/pengurangan angka besar.",
            "Contoh: (18 + 7) × 4 − 12 = 88 atau 24 × 8 − 35 = 157"
        )
        else -> Pair( // "easy"
            "Operasi dasar penjumlahan & pengurangan angka 1 sampai 2 digit (angka 1-80) untuk latihan kecepatan berhitung.",
            "Contoh: 23 + 15 = 38 atau 64 − 27 = 37"
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF38BDF8).copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Info Kesulitan",
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Preview Soal: " + when (difficultyId) {
                    "very_easy" -> "Sangat Mudah"
                    "medium" -> "Sedang"
                    "hard" -> "Sulit"
                    else -> "Mudah"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF38BDF8)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = desc,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.8f),
            lineHeight = 15.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF10B981).copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = example,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF10B981),
                lineHeight = 15.sp
            )
        }
    }
}
