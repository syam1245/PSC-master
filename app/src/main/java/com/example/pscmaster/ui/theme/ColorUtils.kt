package com.example.pscmaster.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

fun getSubjectColor(subject: String): Color {
    val hash = subject.hashCode().absoluteValue
    val colors = listOf(
        Color(0xFFE57373), // Red
        Color(0xFF81C784), // Green
        Color(0xFF64B5F6), // Blue
        Color(0xFFFFD54F), // Amber
        Color(0xFFBA68C8), // Purple
        Color(0xFF4DB6AC), // Teal
        Color(0xFFFF8A65), // Deep Orange
        Color(0xFFAED581), // Light Green
        Color(0xFF9575CD), // Deep Purple
        Color(0xFF7986CB)  // Indigo
    )
    return colors[hash % colors.size]
}
