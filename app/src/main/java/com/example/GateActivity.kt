package com.example

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme

class GateActivity : ComponentActivity() {

    private lateinit var prefs: AppPreferences
    private val mathEngine = MathEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPreferences(this)
        if (prefs.isSleepMode) {
            finish()
            return
        }
        enableEdgeToEdge()

        // Disable standard Back button functionality during Lock Screen unless they have quota left or Sleep Mode is active
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (prefs.remainingMinutes > 0 || prefs.isSleepMode) {
                    finish()
                } else {
                    Toast.makeText(this@GateActivity, "Selesaikan soal matematika untuk membuka HP!", Toast.LENGTH_SHORT).show()
                }
            }
        })

        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A) // Rich slate-900 background
                ) {
                    GateUnlockFlow(
                        prefs = prefs,
                        mathEngine = mathEngine,
                        onUnlocked = { totalMinutesReward ->
                            // Update daily quota setting and force-refresh the service monitor
                            prefs.remainingMinutes = prefs.remainingMinutes + totalMinutesReward
                            ScreenTimeService.start(this)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GateUnlockFlow(
    prefs: AppPreferences,
    mathEngine: MathEngine,
    onUnlocked: (Int) -> Unit
) {
    val context = LocalContext.current
    val difficulty = Difficulty.fromString(prefs.difficulty)
    val rewardPerQuestion = prefs.rewardPerQuestion

    // Core state of active quiz
    var currentQuestion by remember { mutableStateOf<Question?>(null) }
    var currentAnswerInput by remember { mutableStateOf("") }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isFeedbackError by remember { mutableStateOf(false) }
    var isAnswerSuccessScreen by remember { mutableStateOf(false) }
    var latestRewardEarned by remember { mutableStateOf(0) }

    // Initialize or generate the first question
    if (currentQuestion == null) {
        currentQuestion = mathEngine.generateQuestion(difficulty)
    }

    if (isAnswerSuccessScreen) {
        // Step 2: Success Reward Screen
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header Success
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Success",
                tint = Color(0xFF10B981),
                modifier = Modifier
                    .height(72.dp)
                    .width(72.dp)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Text(
                text = "Jawaban Benar! 🎉",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Kerja bagus! Kamu berhasil menyelesaikan tantangan matematika.",
                fontSize = 15.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SISA WAKTU HP SAAT INI",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        letterSpacing = 1.5.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "${prefs.remainingMinutes} Menit",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF10B981)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "(Berhasil mendapat +$latestRewardEarned Menit)",
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Keep playing button
            Button(
                onClick = {
                    currentQuestion = mathEngine.generateQuestion(difficulty)
                    currentAnswerInput = ""
                    feedbackMessage = null
                    isAnswerSuccessScreen = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("continue_solving_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF38BDF8),
                    contentColor = Color(0xFF0F172A)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = "Lanjutkan Soal"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Lanjutkan Soal",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Done and use phone button
            Button(
                onClick = {
                    onUnlocked(0) // Safe exit with no additional earned minutes (already updated)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("exit_gate_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Text(
                    text = "Balik ke HP",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    } else {
        // Step 1: Math Quiz Screen Directly
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Status Indicators
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Tantangan Matematika",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                        if (prefs.isSleepMode) {
                            Text(
                                text = "💤 Mode Tidur Aktif",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF8B5CF6)
                            )
                        } else if (prefs.remainingMinutes > 0) {
                            Text(
                                text = "Sisa waktu HP: ${prefs.remainingMinutes}m",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                    
                    // Difficulty indicator pill & Voluntary exit button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    when (difficulty) {
                                        Difficulty.VERY_EASY -> Color(0xFF1D4ED8)
                                        Difficulty.EASY -> Color(0xFF047857)
                                        Difficulty.MEDIUM -> Color(0xFFB45309)
                                        Difficulty.HARD -> Color(0xFFB91C1C)
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = when(difficulty) {
                                    Difficulty.VERY_EASY -> "Sgt Mudah"
                                    Difficulty.EASY -> "Mudah"
                                    Difficulty.MEDIUM -> "Sedang"
                                    Difficulty.HARD -> "Sulit"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        if (prefs.remainingMinutes > 0 || prefs.isSleepMode) {
                            IconButton(
                                onClick = { (context as? Activity)?.finish() },
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.15f), CircleShape)
                                    .testTag("exit_gate_voluntarily")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Keluar",
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Progress meter single beautiful track indicating locked status
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (prefs.remainingMinutes > 0 || prefs.isSleepMode) Color(0xFF10B981) else Color(0xFFEF4444))
                )
            }

            // Challenge container
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = "Challenge",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.height(36.dp).width(36.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Berapakah hasil dari:",
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // The Question Formula display
                    currentQuestion?.let { q ->
                        Text(
                            text = q.expression,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Answer state display
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .background(Color(0xFF0F172A), RoundedCornerShape(16.dp))
                            .border(width = 1.dp, color = Color(0xFF334155), shape = RoundedCornerShape(16.dp))
                    ) {
                        Text(
                            text = currentAnswerInput.ifEmpty { "?" },
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentAnswerInput.isEmpty()) Color(0xFF475569) else Color(0xFF38BDF8)
                        )
                    }

                    // Feedbacks
                    AnimatedVisibility(
                        visible = feedbackMessage != null,
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut()
                    ) {
                        feedbackMessage?.let { msg ->
                            Text(
                                text = msg,
                                color = if (isFeedbackError) Color(0xFFEF4444) else Color(0xFF10B981),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .testTag("feedback_text")
                            )
                        }
                    }
                }
            }

            // Interactive custom keypads
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val padRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "DEL")
                )

                padRows.forEach { rowKeys ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowKeys.forEach { key ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(2.2f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when (key) {
                                            "C" -> Color(0xFFEF4444).copy(alpha = 0.15f)
                                            "DEL" -> Color(0xFF64748B).copy(alpha = 0.2f)
                                            else -> Color(0xFF1E293B)
                                        }
                                    )
                                    .verticalScrollDisabledClickable {
                                        when (key) {
                                            "C" -> {
                                                currentAnswerInput = ""
                                                feedbackMessage = null
                                            }
                                            "DEL" -> {
                                                if (currentAnswerInput.isNotEmpty()) {
                                                    currentAnswerInput = currentAnswerInput.dropLast(1)
                                                }
                                            }
                                            else -> {
                                                if (currentAnswerInput.length < 6) {
                                                    currentAnswerInput += key
                                                }
                                            }
                                        }
                                    }
                            ) {
                                if (key == "DEL") {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Default.Backspace,
                                        contentDescription = "Hapus",
                                        tint = Color.White
                                    )
                                } else {
                                    Text(
                                        text = key,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (key == "C") Color(0xFFF87171) else Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Submit Action Button
                Button(
                    onClick = {
                        val activeQ = currentQuestion
                        if (activeQ != null) {
                            val isCorrect = mathEngine.validateAnswer(activeQ, currentAnswerInput)
                            if (isCorrect) {
                                isFeedbackError = false
                                feedbackMessage = "Jawaban Benar! 🎉"
                                
                                // Process direct reward immediately
                                prefs.remainingMinutes = prefs.remainingMinutes + rewardPerQuestion
                                latestRewardEarned = rewardPerQuestion
                                ScreenTimeService.start(context)
                                
                                // Trigger Small Success Screen display
                                isAnswerSuccessScreen = true
                            } else {
                                isFeedbackError = true
                                val motivationalPhrases = listOf(
                                    "Coba lagi, kamu pasti bisa! 💪",
                                    "Belum tepat, hitung pelan-pelan ya! ✨",
                                    "Jangan menyerah, coba sekali lagi! 🧠",
                                    "Ulangi hitungannya, kamu hampir bisa! 🚀"
                                )
                                feedbackMessage = motivationalPhrases.random()
                                currentAnswerInput = ""
                            }
                        }
                    },
                    enabled = currentAnswerInput.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("submit_answer_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981),
                        contentColor = Color(0xFF0F172A),
                        disabledContainerColor = Color(0xFF334155),
                        disabledContentColor = Color(0xFF64748B)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "KIRIM JAWABAN",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// Custom handler for small success delay and transition
private fun handlerOnSuccessDelay(
    currentAnswerInput: String,
    currentQuestionIndex: Int,
    selectedQuestionCount: Int,
    onNext: () -> Unit,
    onCompleted: () -> Unit
) {
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        if (currentQuestionIndex + 1 >= selectedQuestionCount) {
            onCompleted()
        } else {
            onNext()
        }
    }, 1000)
}

// Extension to avoid triggering click and scroll concurrently
@Composable
fun Modifier.verticalScrollDisabledClickable(onClick: () -> Unit): Modifier {
    return this.clip(RoundedCornerShape(12.dp))
        .background(Color.White.copy(alpha = 0.05f))
        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
        .padding(4.dp)
        .background(Color.Transparent)
        .then(
            Modifier.border(0.dp, Color.Transparent)
        )
        .clip(RoundedCornerShape(12.dp))
        .background(Color.Transparent)
        .testTag("pad_click")
        .then(
            Modifier.background(Color.Transparent)
        )
        .clip(RoundedCornerShape(12.dp))
        .background(Color.Transparent)
        .testTag("pad_btn")
        .then(
            Modifier.background(Color.Transparent)
        )
        .then(
            Modifier.background(Color.Transparent)
        )
        .then(
            // Actual clickable
            Modifier.border(0.dp, Color.Transparent)
        )
        .clickable { onClick() }
}
