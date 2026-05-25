package com.example

import kotlin.random.Random

enum class Difficulty {
    VERY_EASY, EASY, MEDIUM, HARD;

    companion object {
        fun fromString(value: String): Difficulty {
            return when (value.lowercase().replace(" ", "_")) {
                "very_easy", "veryeasy", "sangat_mudah" -> VERY_EASY
                "medium" -> MEDIUM
                "hard" -> HARD
                else -> EASY
            }
        }
    }
}

data class Question(
    val expression: String,
    val correctAnswer: Int
)

class MathEngine {
    private val random = Random(System.currentTimeMillis())

    fun generateQuestion(difficulty: Difficulty): Question {
        return when (difficulty) {
            Difficulty.VERY_EASY -> generateVeryEasyQuestion()
            Difficulty.EASY -> generateEasyQuestion()
            Difficulty.MEDIUM -> generateMediumQuestion()
            Difficulty.HARD -> generateHardQuestion()
        }
    }

    private fun generateVeryEasyQuestion(): Question {
        // Simple 1-digit addition & subtraction for kids
        val isAddition = random.nextBoolean()
        if (isAddition) {
            val a = random.nextInt(1, 10)
            val b = random.nextInt(1, 10)
            return Question("$a + $b", a + b)
        } else {
            val a = random.nextInt(2, 10)
            val b = random.nextInt(1, a) // Ensures positive answer
            return Question("$a − $b", a - b)
        }
    }

    private fun generateEasyQuestion(): Question {
        // Addition & subtraction 1-2 digits
        val isAddition = random.nextBoolean()
        if (isAddition) {
            val a = random.nextInt(5, 50)
            val b = random.nextInt(1, 40)
            return Question("$a + $b", a + b)
        } else {
            val a = random.nextInt(10, 80)
            val b = random.nextInt(1, a - 1) // Ensures positive answer
            return Question("$a − $b", a - b)
        }
    }

    private fun generateMediumQuestion(): Question {
        // Multiplication & division 2 digit
        val isMultiplication = random.nextBoolean()
        if (isMultiplication) {
            val a = random.nextInt(11, 25) // 2-digit
            val b = random.nextInt(3, 9)    // 1-digit/simple multiplier
            return Question("$a × $b", a * b)
        } else {
            // Division guaranteed to be an integer
            val answer = random.nextInt(2, 15)
            val divisor = random.nextInt(3, 12)
            val dividend = answer * divisor
            return Question("$dividend ÷ $divisor", answer)
        }
    }

    private fun generateHardQuestion(): Question {
        // Mixed operations & large numbers, (A + B) * C - D
        val a = random.nextInt(10, 30)
        val b = random.nextInt(5, 15)
        val c = random.nextInt(3, 6)
        val d = random.nextInt(5, 25)
        
        val result = (a + b) * c - d
        val expression = "($a + $b) × $c − $d"
        
        if (result > 0) {
            return Question(expression, result)
        } else {
            // Fallback to simpler hard mixed without brackets that is positive
            val x = random.nextInt(15, 40)
            val y = random.nextInt(5, 15)
            val z = random.nextInt(10, 50)
            return Question("$x × $y − $z", x * y - z)
        }
    }

    fun validateAnswer(question: Question, input: String): Boolean {
        val parsedInput = input.trim().toIntOrNull() ?: return false
        return parsedInput == question.correctAnswer
    }
}
