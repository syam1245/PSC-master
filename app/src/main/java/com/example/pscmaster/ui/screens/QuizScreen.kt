@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.example.pscmaster.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.example.pscmaster.ui.theme.getSubjectColor
import com.example.pscmaster.ui.viewmodel.QuizViewModel
import com.example.pscmaster.ui.viewmodel.PracticeConfig
import com.example.pscmaster.ui.viewmodel.QuizUiState
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

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
                    onToggleAdaptiveMode = viewModel::onToggleAdaptiveMode,
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
    
    // Sync UI state back to ViewModel when page changes
    LaunchedEffect(pagerState.currentPage) {
        viewModel.updateCurrentPage(pagerState.currentPage)
    }

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
            userScrollEnabled = true,
            beyondViewportPageCount = 1
        ) { pageIndex ->
            QuestionPage(
                question = uiState.questions[pageIndex],
                selectedOption = uiState.answeredIndices[pageIndex],
                isSkipped = uiState.skippedIndices.contains(pageIndex),
                isLastPage = pageIndex == uiState.questions.size - 1,
                viewModel = viewModel,
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

@Composable
fun QuestionPage(
    question: Question,
    selectedOption: Int?,
    isSkipped: Boolean,
    isLastPage: Boolean,
    viewModel: QuizViewModel,
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

            Spacer(Modifier.width(8.dp))
            
            // New Badge State (Fetched reactively)
            val badgeState by viewModel.observeBadgeState(question.id).collectAsState(initial = null)
            if ((badgeState ?: 0) < 3) {
                Surface(
                    color = MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "NEW",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(Modifier.weight(1f))
                TextButton(onClick = onSkip, contentPadding = PaddingValues(0.dp)) {
                    Icon(if (isSkipped) Icons.Default.Refresh else Icons.Default.SkipNext, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isSkipped) "SKIPPED" else "SKIP", style = MaterialTheme.typography.labelLarge)
                }
        }

        // 2. Scrollable Content Area (Question + Options)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Question + AI Variation
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AutoAwesome, 
                                        contentDescription = null, 
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "AI REPHRASED VERSION", 
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                
                                if (isLoadingVariation) {
                                    Box(modifier = Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                } else if (aiVariation != null) {
                                    Text(
                                        text = aiVariation,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            lineHeight = 20.sp
                                        ),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                } else {
                                    // Manual Generate Button
                                    TextButton(
                                        onClick = onGenerateVariation,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.tertiary
                                        )
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("REPHRASE WITH AI", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Options
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                question.options.forEachIndexed { index, option ->
                    val isCorrect = index == question.correctIndex
                    val isSelected = index == selectedOption
                    
                    val backgroundColor = when {
                        isAnswered && isCorrect -> SuccessGreen.copy(alpha = 0.15f)
                        isSelected && !isCorrect -> ErrorRed.copy(alpha = 0.15f)
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surface
                    }
                    
                    val borderColor = when {
                        isAnswered && isCorrect -> SuccessGreen
                        isSelected && !isCorrect -> ErrorRed
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    }

                    Surface(
                        onClick = { if (!isAnswered) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAnswerSelected(index)
                        } },
                        enabled = !isAnswered,
                        shape = RoundedCornerShape(12.dp),
                        color = backgroundColor,
                        border = androidx.compose.foundation.BorderStroke(if (isSelected || (isAnswered && isCorrect)) 2.dp else 1.dp, borderColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected || (isAnswered && isCorrect)) borderColor else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isAnswered && isCorrect) {
                                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp), tint = Color.White)
                                    } else if (isSelected && !isCorrect) {
                                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = Color.White)
                                    } else {
                                        Text(
                                            text = ('A' + index).toString(),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected || (isAnswered && isCorrect)) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Explanation Section
            if (isAnswered && question.explanation.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .animateContentSize()
                ) {
                    Text(
                        "EXPLANATION",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = question.explanation,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        }

        // 3. Bottom Action (Fixed)
        if (isLastPage && isAnswered) {
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("VIEW RESULTS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun EmptyQuizState(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.QuestionMark, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(24.dp))
        Text("No questions found for the selected subjects.", textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) {
            Text("GO BACK")
        }
    }
}

@Composable
fun QuizResultView(score: Int, total: Int, answeredCount: Int, onBack: () -> Unit) {
    val percentage = if (total > 0) (score * 100) / total else 0
    val (message, emoji) = when {
        percentage >= 90 -> "Excellent!" to "🏆"
        percentage >= 75 -> "Great job!" to "🌟"
        percentage >= 50 -> "Good effort" to "👍"
        else -> "Keep practicing" to "📚"
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
            CircularProgressIndicator(
                progress = { percentage / 100f },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 12.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
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
    onToggleAdaptiveMode: (Boolean) -> Unit,
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

            // Adaptive Engine Card
            ConfigCard(
                title = "ADAPTIVE ENGINE",
                description = "Focuses on your weak areas and ensures you see new questions within 3 sessions.",
                icon = Icons.Default.Psychology,
                checked = configState.isAdaptiveMode,
                onCheckedChange = onToggleAdaptiveMode,
                color = MaterialTheme.colorScheme.secondary,
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f)
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
