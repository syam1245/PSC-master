package com.example.pscmaster.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pscmaster.data.entity.Question
import com.example.pscmaster.ui.theme.SuccessGreen
import com.example.pscmaster.ui.theme.ErrorRed
import com.example.pscmaster.ui.viewmodel.QuizViewModel
import com.example.pscmaster.ui.viewmodel.PracticeConfig
import com.example.pscmaster.ui.viewmodel.QuizUiState
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    viewModel: QuizViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val configState by viewModel.configState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (uiState.isConfiguring) "PRACTICE SETUP" else "QUIZ MODE", 
                        style = MaterialTheme.typography.labelLarge,
                        letterSpacing = 1.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isConfiguring) {
                PracticeConfigurator(
                    configState = configState,
                    onToggleShuffle = viewModel::onToggleShuffle,
                    onToggleSubject = viewModel::onToggleSubject,
                    onToggleRevision = viewModel::onToggleRevision,
                    onToggleAiVariation = viewModel::onToggleAiVariation,
                    onStart = viewModel::startPractice
                )
            } else if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.isQuizFinished) {
                QuizResultView(
                    score = uiState.score,
                    total = uiState.questions.size,
                    answeredCount = uiState.answeredIndices.size,
                    onBack = onNavigateBack
                )
            } else if (uiState.questions.isEmpty()) {
                EmptyQuizState(onNavigateBack)
            } else {
                QuizContent(uiState, viewModel)
            }
        }
    }
}

@Composable
fun QuizContent(uiState: QuizUiState, viewModel: QuizViewModel) {
    val pagerState = rememberPagerState(pageCount = { uiState.questions.size })
    val scope = rememberCoroutineScope()
    
    Column(modifier = Modifier.fillMaxSize()) {
        val progress by animateFloatAsState(
            targetValue = if (uiState.questions.isNotEmpty()) (pagerState.currentPage + 1).toFloat() / uiState.questions.size else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "progress"
        )
        
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "QUESTION ${pagerState.currentPage + 1} OF ${uiState.questions.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "${uiState.answeredIndices.size} answered · ${uiState.score} correct",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pagerState.currentPage > 0) {
                        IconButton(
                            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (pagerState.currentPage < uiState.questions.size - 1) {
                        IconButton(
                            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = true
        ) { pageIndex ->
            QuestionPage(
                question = uiState.questions[pageIndex],
                selectedOption = uiState.answeredIndices[pageIndex],
                isSkipped = uiState.skippedIndices.contains(pageIndex),
                isLastPage = pageIndex == uiState.questions.size - 1,
                onAnswerSelected = { optionIndex ->
                    viewModel.onAnswerSelected(pageIndex, optionIndex)
                },
                onSkip = {
                    viewModel.onSkipQuestion(pageIndex)
                    if (pageIndex < uiState.questions.size - 1) {
                        scope.launch { pagerState.animateScrollToPage(pageIndex + 1) }
                    }
                },
                isAiVariationEnabled = viewModel.configState.value.isAiVariationEnabled,
                aiVariation = uiState.aiVariations[pageIndex],
                isLoadingVariation = uiState.loadingVariations.contains(pageIndex),
                onGenerateVariation = { viewModel.generateAiVariation(pageIndex) },
                onFinish = { viewModel.onFinishQuiz() }
            )
        }
    }
}

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

@Composable
fun QuestionPage(
    question: Question,
    selectedOption: Int?,
    isSkipped: Boolean,
    isLastPage: Boolean,
    onAnswerSelected: (Int) -> Unit,
    onSkip: () -> Unit,
    isAiVariationEnabled: Boolean,
    aiVariation: String?,
    isLoadingVariation: Boolean,
    onGenerateVariation: () -> Unit,
    onFinish: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isAnswered = selectedOption != null

    // Calculate dynamic scaling to help fit content
    val questionLength = question.questionText.length + (aiVariation?.length ?: 0)
    val baseFontSize = when {
        questionLength > 300 -> 14.sp
        questionLength > 150 -> 16.sp
        else -> 18.sp
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // 1. Header Row (Fixed)
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val subjectColor = getSubjectColor(question.subject)
            Surface(
                color = subjectColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, subjectColor.copy(alpha = 0.3f))
            ) {
                Text(
                    text = question.subject.uppercase(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = subjectColor,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!isAnswered) {
                TextButton(onClick = onSkip, contentPadding = PaddingValues(0.dp)) {
                    Icon(if (isSkipped) Icons.Default.Refresh else Icons.Default.SkipNext, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isSkipped) "SKIPPED" else "SKIP", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // 2. Question Area (Flexible Weight)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Text(
                    text = question.questionText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = baseFontSize,
                        lineHeight = (baseFontSize.value * 1.4).sp
                    ),
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (isAiVariationEnabled) {
                if (aiVariation != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text("AI REPHRASED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = aiVariation,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = (baseFontSize.value - 2).sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }
                } else if (!isAnswered) {
                    OutlinedButton(
                        onClick = onGenerateVariation,
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isLoadingVariation
                    ) {
                        if (isLoadingVariation) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("SHOW AI VARIATION", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 3. Options Area (Fixed)
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = if (isLastPage && isAnswered) 8.dp else 0.dp)
        ) {
            question.options.forEachIndexed { index, option ->
                val isCorrect = index == question.correctIndex
                val isUserSelected = selectedOption == index
                
                QuizOptionItem(
                    text = option,
                    index = index,
                    isAnswered = isAnswered,
                    isCorrect = isCorrect,
                    isUserSelected = isUserSelected,
                    fontSize = (baseFontSize.value - 1).sp,
                    onClick = {
                        if (!isAnswered) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAnswerSelected(index)
                        }
                    }
                )
            }
        }

        // 4. Finish Session Button
        if (isLastPage && (isAnswered || isSkipped)) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("FINISH SESSION", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun QuizOptionItem(
    text: String,
    index: Int,
    isAnswered: Boolean,
    isCorrect: Boolean,
    isUserSelected: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onClick: () -> Unit
) {
    val containerColor = when {
        isAnswered && isCorrect -> SuccessGreen.copy(alpha = 0.12f)
        isAnswered && isUserSelected && !isCorrect -> ErrorRed.copy(alpha = 0.12f)
        isUserSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.surface
    }
    
    val borderColor = when {
        isAnswered && isCorrect -> SuccessGreen
        isAnswered && isUserSelected && !isCorrect -> ErrorRed
        isUserSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    }

    // High-contrast and bright colors for the letter indicators
    val letterBgColor = when {
        isAnswered && isCorrect -> SuccessGreen
        isAnswered && isUserSelected && !isCorrect -> ErrorRed
        isUserSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) // Brighter idle state
    }

    val letterTextColor = when {
        isAnswered && isCorrect || isAnswered && isUserSelected && !isCorrect || isUserSelected -> Color.White
        else -> Color.White // Keeping it white on a bright background for pop
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(letterBgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ('A' + index).toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = letterTextColor,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSize),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isUserSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun EmptyQuizState(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("UP TO DATE!", style = MaterialTheme.typography.headlineMedium)
        Text(
            "No questions found or scheduled for now. Add some questions to begin!",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedButton(onClick = onBack) {
            Text("GO BACK")
        }
    }
}

@Composable
fun QuizResultView(score: Int, total: Int, answeredCount: Int, onBack: () -> Unit) {
    val percentage = if (answeredCount > 0) (score.toFloat() / answeredCount * 100).toInt() else 0
    
    val (emoji, message) = when {
        percentage >= 90 -> "🏆" to "Outstanding! You're well prepared!"
        percentage >= 70 -> "🎯" to "Great job! Keep up the momentum!"
        percentage >= 50 -> "📚" to "Good effort! Review weak areas."
        percentage >= 30 -> "💪" to "Keep practicing, you'll improve!"
        else -> "🔄" to "Time to revisit the basics."
    }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(180.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 12.dp,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            CircularProgressIndicator(
                progress = { if (answeredCount > 0) score.toFloat() / answeredCount else 0f },
                modifier = Modifier.fillMaxSize(),
                color = if (percentage >= 50) SuccessGreen else ErrorRed,
                strokeWidth = 12.dp,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$percentage%", style = MaterialTheme.typography.displayLarge)
                Text("SCORE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Text(emoji, fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("SESSION COMPLETE", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "$score correct out of $answeredCount answered ($total total)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("BACK TO DASHBOARD")
        }
    }
}

@Composable
fun PracticeConfigurator(
    configState: PracticeConfig,
    onToggleShuffle: (Boolean) -> Unit,
    onToggleSubject: (String) -> Unit,
    onToggleRevision: (Boolean) -> Unit,
    onToggleAiVariation: (Boolean) -> Unit,
    onStart: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Smart Revision Card
            ConfigCard(
                title = "SMART REVISION",
                description = "Spaced Repetition (3d, 1w, 2w, 1m) based on your weak subjects.",
                icon = Icons.Default.AutoAwesome,
                checked = configState.isRevisionMode,
                onCheckedChange = onToggleRevision,
                color = MaterialTheme.colorScheme.primary
            )

            if (!configState.isRevisionMode) {
                Text(
                    "MANUAL SETUP", 
                    style = MaterialTheme.typography.labelLarge, 
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                // Shuffle Card - Minimal
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text("Shuffle Questions", style = MaterialTheme.typography.bodyLarge)
                        }
                        Switch(
                            checked = configState.isShuffleEnabled, 
                            onCheckedChange = { 
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleShuffle(it) 
                            }
                        )
                    }
                }

                Text(
                    "SELECT SUBJECTS", 
                    style = MaterialTheme.typography.labelLarge, 
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                if (configState.availableSubjects.isEmpty()) {
                    Text(
                        "No subjects available. Add questions first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // Subjects Grid using FlowRow
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        configState.availableSubjects.forEach { subject ->
                            val isSelected = configState.selectedSubjects.contains(subject)
                            FilterChip(
                                selected = isSelected,
                                onClick = { 
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleSubject(subject) 
                                },
                                label = { 
                                    Text(
                                        subject, 
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    ) 
                                },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) }
                                } else null,
                                shape = RoundedCornerShape(12.dp),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    selectedBorderWidth = 2.dp
                                ),
                                modifier = Modifier.heightIn(min = 44.dp)
                            )
                        }
                    }
                }

                if (configState.selectedSubjects.isEmpty() && configState.availableSubjects.isNotEmpty()) {
                    Text(
                        "Includes all subjects by default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // AI Variations Card
            ConfigCard(
                title = "QUESTION VARIATIONS",
                description = "AI rephrases questions for better understanding. (English & Malayalam)",
                icon = Icons.Default.AutoAwesome,
                checked = configState.isAiVariationEnabled,
                onCheckedChange = onToggleAiVariation,
                color = MaterialTheme.colorScheme.tertiary,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
            )
        }

        // Bottom Start Button
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStart()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (configState.isRevisionMode) "INITIALIZE AI REVISION" else "START PRACTICE SESSION",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ConfigCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    color: Color,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
) {
    val haptic = LocalHapticFeedback.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) color.copy(alpha = 0.1f) else containerColor
        ),
        border = if (checked) androidx.compose.foundation.BorderStroke(2.dp, color) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    description, 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
            Switch(
                checked = checked, 
                onCheckedChange = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCheckedChange(it) 
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = color,
                    checkedTrackColor = color.copy(alpha = 0.3f)
                )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = { content() }
    )
}
